package printerhub;

public final class OperationMessages {

    private OperationMessages() {
    }

    public static final String ERROR_PREFIX = "[ERROR] ";
    public static final String INFO_PREFIX = "[INFO] ";
    public static final String SEND_PREFIX = "[SEND] ";
    public static final String RECV_PREFIX = "[RECV] ";

    public static final String INVALID_INPUT_PREFIX = ERROR_PREFIX + "Invalid input: ";
    public static final String INTERRUPTED_PREFIX = ERROR_PREFIX + "Program interrupted: ";
    public static final String TIMEOUT_PREFIX = ERROR_PREFIX + "Printer timeout: ";
    public static final String RUNTIME_FAILURE_PREFIX = ERROR_PREFIX + "Unexpected failure: ";

    public static final String WAITING_FOR_PRINTER_INITIALIZATION =
            INFO_PREFIX + "Waiting 2 seconds for printer initialization...";

    public static final String DISCONNECTED = "Disconnected.";
    public static final String SERIAL_NOT_OPEN =
            "Serial connection is not open. Call connect() before sending commands.";
    public static final String COMMAND_MUST_NOT_BE_BLANK = "command must not be blank";
    public static final String PORT_NAME_MUST_NOT_BE_BLANK = "portName must not be blank";
    public static final String PORT_ADAPTER_MUST_NOT_BE_NULL = "portAdapter must not be null";

    public static final String REPEAT_COUNT_MUST_BE_GREATER_THAN_ZERO =
        "repeatCount must be greater than 0";

    public static String failedToConnectForPolling(String portName) {
        return "Failed to connect to printer on port '" + portName + "' before polling started.";
    }

    public static String failedDuringPoll(String command, int pollNumber, String detail) {
        return "Failure during poll " + pollNumber + " for command '" + command + "': " + detail;
    }

    public static String pollHeader(int currentPoll, int totalPolls) {
        return "---- Poll " + currentPoll + " of " + totalPolls + " ----";
    }

    public static String noResponseForCommand(String command, int pollNumber) {
        return "No response received for command '" + command + "' on poll " + pollNumber + ".";
    }

    public static String connectionAlreadyOpen(String portName) {
        return "Connection already open on port " + portName;
    }

    public static String failedToOpenSerialPort(String portName) {
        return "Failed to open serial port '" + portName + "'. "
                + "Possible causes: device path is wrong, permission is missing, "
                + "or the port is already in use.";
    }

    public static String streamInitializationFailed(String portName) {
        return "Serial port '" + portName
                + "' opened, but input/output streams could not be initialized.";
    }

    public static String failedToSendCommand(String command, String portName) {
        return "Failed to send command '" + command + "' to port '" + portName + "'.";
    }

    public static String failedWhileReading(String portName) {
        return "Failed while reading from serial port '" + portName + "'.";
    }

    public static String noResponseWithinTimeout(int timeoutMs) {
        return "No response received from printer within " + timeoutMs + " ms.";
    }

    public static String failedToCloseSerialPort(String portName) {
        return "Failed to close serial port " + portName;
    }

    public static String errorWhileClosingSerialPort(String detail) {
        return "Error while closing serial port: " + detail;
    }

    public static String failedToCloseResource(String resourceName, String detail) {
        return "Failed to close " + resourceName + ": " + detail;
    }

    public static String sentMessage(String message) {
        return SEND_PREFIX + message;
    }

    public static String receivedMessage(String message) {
        return RECV_PREFIX + message;
    }

    public static String infoMessage(String message) {
        return INFO_PREFIX + message;
    }

    public static String errorMessage(String message) {
        return ERROR_PREFIX + message;
    }
}