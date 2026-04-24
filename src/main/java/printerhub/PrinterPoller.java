package printerhub;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class PrinterPoller {

    private final PrinterPort port;
    private final long initDelayMs;
    private final long betweenPollsMs;
    private final PrinterStateTracker stateTracker;

    public PrinterPoller(PrinterPort port, long initDelayMs, long betweenPollsMs) {
        this(port, initDelayMs, betweenPollsMs, new PrinterStateTracker());
    }

    public PrinterPoller(PrinterPort port,
                         long initDelayMs,
                         long betweenPollsMs,
                         PrinterStateTracker stateTracker) {
        if (stateTracker == null) {
            throw new IllegalArgumentException("stateTracker must not be null");
        }

        this.port = port;
        this.initDelayMs = initDelayMs;
        this.betweenPollsMs = betweenPollsMs;
        this.stateTracker = stateTracker;
    }

    public void runPolling(int repeatCount, String command)
            throws IOException, TimeoutException, InterruptedException {

        if (repeatCount <= 0) {
            throw new IllegalArgumentException(OperationMessages.REPEAT_COUNT_MUST_BE_GREATER_THAN_ZERO);
        }

        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.COMMAND_MUST_NOT_BE_BLANK);
        }

        try {
            logState(stateTracker.markConnecting());

            if (!port.connect()) {
                throw new IOException(
                        OperationMessages.failedToConnectForPolling(port.getPortName())
                );
            }

            System.out.println(OperationMessages.WAITING_FOR_PRINTER_INITIALIZATION);
            sleep(initDelayMs);

            for (int i = 1; i <= repeatCount; i++) {
                System.out.println(OperationMessages.pollHeader(i, repeatCount));

                port.sendCommand(command);
                String response = port.readLine();

                if (response == null || response.isBlank()) {
                    PrinterSnapshot errorSnapshot = stateTracker.markError(response);
                    logState(errorSnapshot);

                    throw new TimeoutException(
                            OperationMessages.noResponseForCommand(command, i)
                    );
                }

                PrinterSnapshot snapshot = stateTracker.updateFromResponse(response);
                logState(snapshot);

                if (i < repeatCount) {
                    sleep(betweenPollsMs);
                }
            }

        } finally {
            port.disconnect();
            logState(stateTracker.markDisconnected());
        }
    }

    public PrinterSnapshot getCurrentSnapshot() {
        return stateTracker.getCurrentSnapshot();
    }

    protected void sleep(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }

    private void logState(PrinterSnapshot snapshot) {
        System.out.println(OperationMessages.infoMessage(
                "Printer state: " + snapshot.getState()
                        + ", hotend=" + formatTemperature(snapshot.getHotendTemperature())
                        + ", bed=" + formatTemperature(snapshot.getBedTemperature())
        ));
    }

    private String formatTemperature(Double value) {
        if (value == null) {
            return "n/a";
        }

        return String.format("%.1f°C", value);
    }
}