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
            throw new IllegalArgumentException("repeatCount must be greater than 0");
        }

        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("command must not be blank");
        }

        try {
            if (!port.connect()) {
                throw new IOException("Failed to connect to " + port.getPortName());
            }

            System.out.println("[INFO] Waiting 2 seconds for printer initialization...");
            sleep(initDelayMs);

            for (int i = 1; i <= repeatCount; i++) {
                System.out.println("---- Poll " + i + " of " + repeatCount + " ----");

                port.sendCommand(command);
                String response = port.readLine();

                if (response == null || response.isBlank()) {
                    throw new TimeoutException(
                            "No response received for command '" + command + "' on poll " + i + "."
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