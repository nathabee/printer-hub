package printerhub.monitoring;

import printerhub.PrinterSnapshot;
import printerhub.PrinterState;
import printerhub.runtime.PrinterRuntimeNode;
import printerhub.runtime.PrinterRuntimeStateCache;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PrinterMonitoringTask implements Runnable {

    private static final String DEFAULT_STATUS_COMMAND = "M105";
    private static final Pattern HOTEND_PATTERN = Pattern.compile("T:([0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern BED_PATTERN = Pattern.compile("B:([0-9]+(?:\\.[0-9]+)?)");

    private final PrinterRuntimeNode node;
    private final PrinterRuntimeStateCache stateCache;
    private final Clock clock;
    private final String statusCommand;

    public PrinterMonitoringTask(PrinterRuntimeNode node, PrinterRuntimeStateCache stateCache) {
        this(node, stateCache, Clock.systemUTC(), DEFAULT_STATUS_COMMAND);
    }

    public PrinterMonitoringTask(
            PrinterRuntimeNode node,
            PrinterRuntimeStateCache stateCache,
            Clock clock,
            String statusCommand
    ) {
        if (node == null) {
            throw new IllegalArgumentException("node must not be null");
        }
        if (stateCache == null) {
            throw new IllegalArgumentException("stateCache must not be null");
        }
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }
        if (statusCommand == null || statusCommand.isBlank()) {
            throw new IllegalArgumentException("statusCommand must not be blank");
        }

        this.node = node;
        this.stateCache = stateCache;
        this.clock = clock;
        this.statusCommand = statusCommand;
    }

    @Override
    public void run() {
        PrinterSnapshot previousSnapshot = stateCache.currentOrDisconnected(node.id());

        if (!node.enabled()) {
            stateCache.update(
                    node.id(),
                    PrinterSnapshot.error(
                            PrinterState.DISCONNECTED,
                            previousSnapshot.hotendTemperature(),
                            previousSnapshot.bedTemperature(),
                            previousSnapshot.lastResponse(),
                            "Printer node is disabled.",
                            Instant.now(clock)
                    )
            );
            return;
        }

        try {
            stateCache.update(
                    node.id(),
                    PrinterSnapshot.connecting(
                            previousSnapshot.hotendTemperature(),
                            previousSnapshot.bedTemperature(),
                            previousSnapshot.lastResponse(),
                            Instant.now(clock)
                    )
            );

            node.printerPort().connect();

            String response = node.printerPort().sendCommand(statusCommand);

            if (response == null || response.isBlank()) {
                stateCache.update(
                        node.id(),
                        PrinterSnapshot.error(
                                PrinterState.ERROR,
                                previousSnapshot.hotendTemperature(),
                                previousSnapshot.bedTemperature(),
                                response,
                                "No response for command " + statusCommand,
                                Instant.now(clock)
                        )
                );
                return;
            }

            Double hotendTemperature = extractTemperature(HOTEND_PATTERN, response);
            Double bedTemperature = extractTemperature(BED_PATTERN, response);

            PrinterState state = resolveState(response, hotendTemperature);

            stateCache.update(
                    node.id(),
                    PrinterSnapshot.fromResponse(
                            state,
                            hotendTemperature != null ? hotendTemperature : previousSnapshot.hotendTemperature(),
                            bedTemperature != null ? bedTemperature : previousSnapshot.bedTemperature(),
                            response,
                            Instant.now(clock)
                    )
            );
        } catch (Exception exception) {
            stateCache.update(
                    node.id(),
                    PrinterSnapshot.error(
                            PrinterState.ERROR,
                            previousSnapshot.hotendTemperature(),
                            previousSnapshot.bedTemperature(),
                            previousSnapshot.lastResponse(),
                            exception.getMessage(),
                            Instant.now(clock)
                    )
            );
        }
    }

    private PrinterState resolveState(String response, Double hotendTemperature) {
        String normalized = response.toLowerCase(Locale.ROOT);

        if (normalized.contains("error")
                || normalized.contains("kill")
                || normalized.contains("halted")) {
            return PrinterState.ERROR;
        }

        if (normalized.contains("busy")
                || normalized.contains("printing")) {
            return PrinterState.PRINTING;
        }

        if (hotendTemperature != null && hotendTemperature > 45.0) {
            return PrinterState.HEATING;
        }

        if (normalized.contains("ok")
                || normalized.contains("t:")) {
            return PrinterState.IDLE;
        }

        return PrinterState.UNKNOWN;
    }

    private Double extractTemperature(Pattern pattern, String response) {
        Matcher matcher = pattern.matcher(response);

        if (!matcher.find()) {
            return null;
        }

        try {
            return Double.parseDouble(matcher.group(1));
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}