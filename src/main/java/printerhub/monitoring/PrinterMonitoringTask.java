package printerhub.monitoring;

import printerhub.OperationMessages;
import printerhub.PrinterSnapshot;
import printerhub.PrinterState;
import printerhub.config.PrinterProtocolDefaults;
import printerhub.persistence.PrinterEventStore;
import printerhub.persistence.PrinterSnapshotStore;
import printerhub.runtime.PrinterRuntimeNode;
import printerhub.runtime.PrinterRuntimeStateCache;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PrinterMonitoringTask implements Runnable {

    private static final Pattern HOTEND_PATTERN = Pattern.compile("T:([0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern BED_PATTERN = Pattern.compile("B:([0-9]+(?:\\.[0-9]+)?)");

    private final PrinterRuntimeNode node;
    private final PrinterRuntimeStateCache stateCache;
    private final PrinterSnapshotStore snapshotStore;
    private final PrinterEventStore eventStore;
    private final Clock clock;
    private final String statusCommand;
    private final BooleanSupplier shutdownSignal;
    private final MonitoringEventPolicy eventPolicy;

    public PrinterMonitoringTask(PrinterRuntimeNode node, PrinterRuntimeStateCache stateCache) {
        this(
                node,
                stateCache,
                new PrinterSnapshotStore(),
                new PrinterEventStore(),
                Clock.systemUTC(),
                PrinterProtocolDefaults.DEFAULT_STATUS_COMMAND,
                () -> false,
                new MonitoringEventPolicy(Clock.systemUTC(), java.time.Duration.ofSeconds(60))
        );
    }

    public PrinterMonitoringTask(
            PrinterRuntimeNode node,
            PrinterRuntimeStateCache stateCache,
            PrinterSnapshotStore snapshotStore,
            PrinterEventStore eventStore,
            Clock clock,
            String statusCommand,
            BooleanSupplier shutdownSignal,
            MonitoringEventPolicy eventPolicy
    ) {
        if (node == null) {
            throw new IllegalArgumentException(OperationMessages.NODE_MUST_NOT_BE_NULL);
        }
        if (stateCache == null) {
            throw new IllegalArgumentException(OperationMessages.STATE_CACHE_MUST_NOT_BE_NULL);
        }
        if (snapshotStore == null) {
            throw new IllegalArgumentException(OperationMessages.SNAPSHOT_STORE_MUST_NOT_BE_NULL);
        }
        if (eventStore == null) {
            throw new IllegalArgumentException(OperationMessages.EVENT_STORE_MUST_NOT_BE_NULL);
        }
        if (clock == null) {
            throw new IllegalArgumentException(OperationMessages.CLOCK_MUST_NOT_BE_NULL);
        }
        if (statusCommand == null || statusCommand.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.STATUS_COMMAND_MUST_NOT_BE_BLANK);
        }
        if (shutdownSignal == null) {
            throw new IllegalArgumentException(OperationMessages.SHUTDOWN_SIGNAL_MUST_NOT_BE_NULL);
        }
        if (eventPolicy == null) {
            throw new IllegalArgumentException(OperationMessages.MONITORING_EVENT_POLICY_MUST_NOT_BE_NULL);
        }

        this.node = node;
        this.stateCache = stateCache;
        this.snapshotStore = snapshotStore;
        this.eventStore = eventStore;
        this.clock = clock;
        this.statusCommand = statusCommand;
        this.shutdownSignal = shutdownSignal;
        this.eventPolicy = eventPolicy;
    }

    @Override
    public void run() {
        PrinterSnapshot previousSnapshot = stateCache.currentOrDisconnected(node.id());

        if (!node.enabled()) {
            PrinterSnapshot snapshot = PrinterSnapshot.error(
                    PrinterState.DISCONNECTED,
                    previousSnapshot.hotendTemperature(),
                    previousSnapshot.bedTemperature(),
                    previousSnapshot.lastResponse(),
                    OperationMessages.PRINTER_NODE_DISABLED,
                    Instant.now(clock)
            );

            eventPolicy.clearPrinter(node.id());
            storeAndCache(
                    snapshot,
                    OperationMessages.EVENT_PRINTER_DISABLED,
                    OperationMessages.PRINTER_NODE_DISABLED,
                    false
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
                PrinterSnapshot snapshot = PrinterSnapshot.error(
                        PrinterState.ERROR,
                        previousSnapshot.hotendTemperature(),
                        previousSnapshot.bedTemperature(),
                        response,
                        OperationMessages.noResponseForCommand(statusCommand),
                        Instant.now(clock)
                );

                storeAndCache(
                        snapshot,
                        OperationMessages.EVENT_PRINTER_TIMEOUT,
                        OperationMessages.noResponseForCommand(statusCommand),
                        true
                );
                return;
            }

            Double hotendTemperature = extractTemperature(HOTEND_PATTERN, response);
            Double bedTemperature = extractTemperature(BED_PATTERN, response);

            PrinterState state = resolveState(response, hotendTemperature);

            PrinterSnapshot snapshot = PrinterSnapshot.fromResponse(
                    state,
                    hotendTemperature != null ? hotendTemperature : previousSnapshot.hotendTemperature(),
                    bedTemperature != null ? bedTemperature : previousSnapshot.bedTemperature(),
                    response,
                    Instant.now(clock)
            );

            if (state == PrinterState.ERROR) {
                storeAndCache(
                        snapshot,
                        OperationMessages.EVENT_PRINTER_ERROR,
                        OperationMessages.PRINTER_RETURNED_ERROR_RESPONSE,
                        true
                );
                return;
            }

            eventPolicy.clearPrinter(node.id());
            storeAndCache(
                    snapshot,
                    OperationMessages.EVENT_PRINTER_POLLED,
                    OperationMessages.PRINTER_POLL_COMPLETED_SUCCESSFULLY,
                    false
            );
        } catch (Exception exception) {
            if (shouldSuppressFailureDuringShutdown(exception)) {
                return;
            }

            String message = safeMessage(exception);

            PrinterSnapshot snapshot = PrinterSnapshot.error(
                    PrinterState.ERROR,
                    previousSnapshot.hotendTemperature(),
                    previousSnapshot.bedTemperature(),
                    previousSnapshot.lastResponse(),
                    message,
                    Instant.now(clock)
            );

            storeAndCache(
                    snapshot,
                    classifyException(exception),
                    message,
                    true
            );
        }
    }

    private void storeAndCache(
            PrinterSnapshot snapshot,
            String eventType,
            String eventMessage,
            boolean persistEvent
    ) {
        stateCache.update(node.id(), snapshot);

        try {
            snapshotStore.save(node.id(), snapshot);
        } catch (Exception exception) {
            System.err.println(OperationMessages.failedToPersistSnapshot(
                    node.id(),
                    safeMessage(exception)
            ));
        }

        if (!persistEvent) {
            return;
        }

        if (!eventPolicy.shouldPersistEvent(node.id(), eventType, eventMessage)) {
            return;
        }

        try {
            eventStore.record(node.id(), null, eventType, eventMessage);
            eventPolicy.rememberPersistedEvent(node.id(), eventType, eventMessage);
        } catch (Exception exception) {
            System.err.println(OperationMessages.failedToPersistEvent(
                    node.id(),
                    safeMessage(exception)
            ));
        }
    }

    private boolean shouldSuppressFailureDuringShutdown(Exception exception) {
        if (!shutdownSignal.getAsBoolean()) {
            return false;
        }

        if (exception instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            return true;
        }

        String message = safeMessage(exception).toLowerCase(Locale.ROOT);

        return Thread.currentThread().isInterrupted()
                || message.contains("interrupted")
                || message.contains("closed")
                || message.contains("shutdown");
    }

    private String classifyException(Exception exception) {
        String message = safeMessage(exception).toLowerCase(Locale.ROOT);

        if (message.contains("timeout")
                || message.contains("no response")) {
            return OperationMessages.EVENT_PRINTER_TIMEOUT;
        }

        if (message.contains("disconnected")
                || message.contains("not connected")
                || message.contains("not open")
                || message.contains("failed to open serial port")) {
            return OperationMessages.EVENT_PRINTER_DISCONNECTED;
        }

        return OperationMessages.EVENT_PRINTER_ERROR;
    }

    private String safeMessage(Exception exception) {
        if (exception == null || exception.getMessage() == null || exception.getMessage().isBlank()) {
            return OperationMessages.UNKNOWN_PRINTER_MONITORING_ERROR;
        }

        return exception.getMessage();
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

        if (hotendTemperature != null
                && hotendTemperature > PrinterProtocolDefaults.DEFAULT_HEATING_TEMPERATURE_THRESHOLD) {
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