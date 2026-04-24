package printerhub;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PrinterStateTracker {

    private static final Pattern HOTEND_PATTERN = Pattern.compile("T:([0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern BED_PATTERN = Pattern.compile("B:([0-9]+(?:\\.[0-9]+)?)");

    private PrinterSnapshot currentSnapshot =
            new PrinterSnapshot(PrinterState.DISCONNECTED, null, null, null, LocalDateTime.now());

    public PrinterSnapshot markConnecting() {
        currentSnapshot = new PrinterSnapshot(
                PrinterState.CONNECTING,
                currentSnapshot.getHotendTemperature(),
                currentSnapshot.getBedTemperature(),
                currentSnapshot.getLastResponse(),
                LocalDateTime.now()
        );
        return currentSnapshot;
    }

    public PrinterSnapshot markDisconnected() {
        currentSnapshot = new PrinterSnapshot(
                PrinterState.DISCONNECTED,
                currentSnapshot.getHotendTemperature(),
                currentSnapshot.getBedTemperature(),
                currentSnapshot.getLastResponse(),
                LocalDateTime.now()
        );
        return currentSnapshot;
    }

    public PrinterSnapshot markError(String response) {
        currentSnapshot = new PrinterSnapshot(
                PrinterState.ERROR,
                currentSnapshot.getHotendTemperature(),
                currentSnapshot.getBedTemperature(),
                response,
                LocalDateTime.now()
        );
        return currentSnapshot;
    }

    public PrinterSnapshot updateFromResponse(String response) {
        if (response == null || response.isBlank()) {
            return markError(response);
        }

        Double hotendTemperature = extractTemperature(HOTEND_PATTERN, response);
        Double bedTemperature = extractTemperature(BED_PATTERN, response);
        PrinterState state = resolveState(response, hotendTemperature);

        currentSnapshot = new PrinterSnapshot(
                state,
                hotendTemperature != null ? hotendTemperature : currentSnapshot.getHotendTemperature(),
                bedTemperature != null ? bedTemperature : currentSnapshot.getBedTemperature(),
                response,
                LocalDateTime.now()
        );

        return currentSnapshot;
    }

    public PrinterSnapshot getCurrentSnapshot() {
        return currentSnapshot;
    }

    private PrinterState resolveState(String response, Double hotendTemperature) {
        String normalized = response.toLowerCase();

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
        } catch (NumberFormatException e) {
            return null;
        }
    }
}