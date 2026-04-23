package printerhub;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class PrinterPoller {

    private final PrinterPort port;
    private final long initDelayMs;
    private final long betweenPollsMs;

    public PrinterPoller(PrinterPort port, long initDelayMs, long betweenPollsMs) {
        this.port = port;
        this.initDelayMs = initDelayMs;
        this.betweenPollsMs = betweenPollsMs;
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
                    throw new TimeoutException(
                            OperationMessages.noResponseForCommand(command, i)
                    );
                }

                if (i < repeatCount) {
                    sleep(betweenPollsMs);
                }
            }

        } finally {
            port.disconnect();
        }
    }

    protected void sleep(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }
}