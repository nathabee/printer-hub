package printerhub;

import printerhub.serial.SerialPortAdapterFactory;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class Main {

    private static final String DEFAULT_PORT = "/dev/ttyUSB0";
    private static final String DEFAULT_COMMAND = "M105";
    private static final int DEFAULT_REPEAT_COUNT = 3;
    private static final long DEFAULT_DELAY_MS = 2000L;
    private static final long DEFAULT_INIT_DELAY_MS = 2000L;
    private static final int DEFAULT_BAUD_RATE = 115200;
    private static final String DEFAULT_MODE = "real";
    private static final int DEFAULT_API_PORT = 18080;

    public static void main(String[] args) {
        int exitCode = run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args) {
        try {
            if (args.length >= 1 && "api".equalsIgnoreCase(args[0].trim())) {
                return runApiMode(args);
            }

            return runCliMode(args);

        } catch (IllegalArgumentException e) {
            System.err.println(OperationMessages.INVALID_INPUT_PREFIX + e.getMessage());
            printUsage();
            return 2;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println(OperationMessages.INTERRUPTED_PREFIX + e.getMessage());
            return 3;

        } catch (TimeoutException e) {
            System.err.println(OperationMessages.TIMEOUT_PREFIX + e.getMessage());
            return 4;

        } catch (IOException e) {
            System.err.println(OperationMessages.RUNTIME_FAILURE_PREFIX + e.getMessage());
            return 1;

        } catch (Exception e) {
            System.err.println(OperationMessages.RUNTIME_FAILURE_PREFIX + e.getMessage());
            return 1;
        }
    }

    private static int runCliMode(String[] args)
            throws IOException, TimeoutException, InterruptedException {

        String portName = DEFAULT_PORT;
        String command = DEFAULT_COMMAND;
        int repeatCount = DEFAULT_REPEAT_COUNT;
        long delayMs = DEFAULT_DELAY_MS;
        String mode = DEFAULT_MODE;

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

        if (args.length >= 5 && !args[4].isBlank()) {
            mode = args[4].trim();
        }

        validateInputs(portName, command, repeatCount, delayMs, mode);

        long initDelayMs = resolveInitDelayMs();

        PrinterPort port = new SerialConnection(
                portName,
                DEFAULT_BAUD_RATE,
                SerialPortAdapterFactory.create(portName, mode)
        );

        PrinterPoller poller = new PrinterPoller(port, initDelayMs, delayMs);
        poller.runPolling(repeatCount, command);
        return 0;
    }

    private static int runApiMode(String[] args) throws IOException, InterruptedException {
        String portName = DEFAULT_PORT;
        String mode = DEFAULT_MODE;
        int apiPort = DEFAULT_API_PORT;

        if (args.length >= 2 && !args[1].isBlank()) {
            portName = args[1].trim();
        }

        if (args.length >= 3 && !args[2].isBlank()) {
            mode = args[2].trim();
        }

        if (args.length >= 4 && !args[3].isBlank()) {
            apiPort = parsePositiveInt(args[3], "apiPort");
        }

        validateApiInputs(portName, mode, apiPort);

        RemoteApiServer apiServer = new RemoteApiServer(
                apiPort,
                portName,
                mode,
                DEFAULT_BAUD_RATE,
                resolveInitDelayMs(),
                DEFAULT_DELAY_MS
        );

        Runtime.getRuntime().addShutdownHook(new Thread(apiServer::stop));

        apiServer.start();

        Thread.currentThread().join();
        return 0;
    }

    private static long resolveInitDelayMs() {
        String value = System.getProperty("printerhub.initDelayMs");
        if (value == null || value.isBlank()) {
            return DEFAULT_INIT_DELAY_MS;
        }
        return parsePositiveLong(value, "initDelayMs");
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
                                       long delayMs,
                                       String mode) {

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

        validateMode(mode);
    }

    private static void validateApiInputs(String portName, String mode, int apiPort) {
        if (portName == null || portName.isBlank()) {
            throw new IllegalArgumentException("portName must not be blank");
        }

        if (apiPort <= 0) {
            throw new IllegalArgumentException("apiPort must be greater than 0");
        }

        validateMode(mode);
    }

    private static void validateMode(String mode) {
        String normalizedMode = mode == null ? "" : mode.trim().toLowerCase();
        if (!normalizedMode.equals("real")
                && !normalizedMode.equals("sim")
                && !normalizedMode.equals("simulated")) {
            throw new IllegalArgumentException("mode must be one of: real, sim, simulated");
        }
    }

    private static void printUsage() {
        System.err.println();
        System.err.println("Usage:");
        System.err.println("  CLI mode:");
        System.err.println("  mvn exec:java -Dexec.mainClass=\"printerhub.Main\" "
                + "-Dexec.args=\"<port> <command> <repeatCount> <delayMs> [mode]\"");
        System.err.println();
        System.err.println("  API mode:");
        System.err.println("  mvn exec:java -Dexec.mainClass=\"printerhub.Main\" "
                + "-Dexec.args=\"api <port> [mode] [apiPort]\"");
        System.err.println();
        System.err.println("Arguments:");
        System.err.println("  port        serial port path, for example /dev/ttyUSB0");
        System.err.println("  command     G-code command, for example M105");
        System.err.println("  repeatCount number of polling attempts, must be > 0");
        System.err.println("  delayMs     delay between polls in milliseconds, must be > 0");
        System.err.println("  mode        real | sim | simulated");
        System.err.println("  apiPort     HTTP port for API mode, default 18080");
        System.err.println();
        System.err.println("Examples:");
        System.err.println("  mvn exec:java -Dexec.mainClass=\"printerhub.Main\" "
                + "-Dexec.args=\"/dev/ttyUSB0 M105 3 2000 real\"");
        System.err.println("  mvn exec:java -Dexec.mainClass=\"printerhub.Main\" "
                + "-Dexec.args=\"SIM_PORT M105 3 100 sim\"");
        System.err.println("  mvn exec:java -Dexec.mainClass=\"printerhub.Main\" "
                + "-Dexec.args=\"api SIM_PORT sim 18080\"");
        System.err.println();
        System.err.println("Defaults:");
        System.err.println("  port        = " + DEFAULT_PORT);
        System.err.println("  command     = " + DEFAULT_COMMAND);
        System.err.println("  repeatCount = " + DEFAULT_REPEAT_COUNT);
        System.err.println("  delayMs     = " + DEFAULT_DELAY_MS);
        System.err.println("  mode        = " + DEFAULT_MODE);
        System.err.println("  apiPort     = " + DEFAULT_API_PORT);
        System.err.println();
        System.err.println("Exit codes:");
        System.err.println("  0  success");
        System.err.println("  1  runtime failure");
        System.err.println("  2  invalid input");
        System.err.println("  3  interrupted execution");
        System.err.println("  4  printer timeout / no response");
    }
}