package printerhub;

public class Main {

    private static final String DEFAULT_PORT = "/dev/ttyUSB0";
    private static final String DEFAULT_COMMAND = "M105";
    private static final int DEFAULT_REPEAT_COUNT = 3;
    private static final long DEFAULT_DELAY_MS = 2000L;

    public static void main(String[] args) {
        String portName = DEFAULT_PORT;
        String command = DEFAULT_COMMAND;
        int repeatCount = DEFAULT_REPEAT_COUNT;
        long delayMs = DEFAULT_DELAY_MS;

        try {
            if (args.length >= 1 && !args[0].isBlank()) {
                portName = args[0];
            }

            if (args.length >= 2 && !args[1].isBlank()) {
                command = args[1].trim();
            }

            if (args.length >= 3) {
                repeatCount = parsePositiveInt(args[2], "repeatCount");
            }

            if (args.length >= 4) {
                delayMs = parsePositiveLong(args[3], "delayMs");
            }

            validateInputs(portName, command, repeatCount, delayMs);

            runPolling(portName, command, repeatCount, delayMs);

        } catch (IllegalArgumentException e) {
            System.err.println("[ERROR] Invalid input: " + e.getMessage());
            printUsage();
            System.exit(2);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[ERROR] Program interrupted: " + e.getMessage());
            System.exit(3);

        } catch (Exception e) {
            System.err.println("[ERROR] Unexpected failure: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void runPolling(String portName,
                                   String command,
                                   int repeatCount,
                                   long delayMs) throws Exception {

        SerialConnection serial = new SerialConnection();

        try {
            serial.connect(portName);

            System.out.println("[INFO] Waiting 2 seconds for printer initialization...");
            Thread.sleep(2000);

            for (int i = 1; i <= repeatCount; i++) {
                System.out.println("---- Poll " + i + " of " + repeatCount + " ----");

                serial.sendCommand(command);
                String response = serial.readResponse();

                if (response == null || response.isBlank()) {
                    System.err.println(
                            "[WARN] No response received for command '" + command + "'."
                    );
                }

                if (i < repeatCount) {
                    Thread.sleep(delayMs);
                }
            }

        } finally {
            serial.disconnect();
        }
    }

    private static int parsePositiveInt(String value, String fieldName) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new IllegalArgumentException(
                        fieldName + " must be greater than 0, but was " + parsed
                );
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    fieldName + " must be a valid integer, but was '" + value + "'"
            );
        }
    }

    private static long parsePositiveLong(String value, String fieldName) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new IllegalArgumentException(
                        fieldName + " must be greater than 0, but was " + parsed
                );
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    fieldName + " must be a valid number, but was '" + value + "'"
            );
        }
    }

    private static void validateInputs(String portName,
                                       String command,
                                       int repeatCount,
                                       long delayMs) {

        if (portName == null || portName.isBlank()) {
            throw new IllegalArgumentException("portName must not be blank");
        }

        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("command must not be blank");
        }

        if (repeatCount <= 0) {
            throw new IllegalArgumentException("repeatCount must be greater than 0");
        }

        if (delayMs <= 0) {
            throw new IllegalArgumentException("delayMs must be greater than 0");
        }
    }

    private static void printUsage() {
        System.err.println();
        System.err.println("Usage:");
        System.err.println("  mvn exec:java -Dexec.mainClass=\"printerhub.Main\" "
                + "-Dexec.args=\"<port> <command> <repeatCount> <delayMs>\"");
        System.err.println();
        System.err.println("Example:");
        System.err.println("  mvn exec:java -Dexec.mainClass=\"printerhub.Main\" "
                + "-Dexec.args=\"/dev/ttyUSB0 M105 3 2000\"");
        System.err.println();
        System.err.println("Defaults:");
        System.err.println("  port        = " + DEFAULT_PORT);
        System.err.println("  command     = " + DEFAULT_COMMAND);
        System.err.println("  repeatCount = " + DEFAULT_REPEAT_COUNT);
        System.err.println("  delayMs     = " + DEFAULT_DELAY_MS);
    }
}