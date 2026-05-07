package printerhub.serial;

import printerhub.OperationMessages;
import printerhub.PrinterPort;
import printerhub.config.PrinterProtocolDefaults;

import java.util.Locale;

public final class SimulatedPrinterPort implements PrinterPort {

    private final String portName;
    private final String mode;
    private boolean connected;

    public SimulatedPrinterPort(String portName) {
        this(portName, PrinterProtocolDefaults.SIM_MODE);
    }

    public SimulatedPrinterPort(String portName, String mode) {
        if (portName == null || portName.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.PORT_NAME_MUST_NOT_BE_BLANK);
        }

        this.portName = portName.trim();
        this.mode = (mode == null || mode.isBlank())
                ? PrinterProtocolDefaults.SIM_MODE
                : mode.trim();
    }

    @Override
    public void connect() {
        if (isDisconnectedMode()) {
            connected = false;
            throw new IllegalStateException(OperationMessages.simulatedPrinterDisconnected(portName));
        }

        connected = true;
    }

    @Override
    public String sendCommand(String command) {
        if (!connected) {
            throw new IllegalStateException(OperationMessages.simulatedPrinterNotConnected(portName));
        }

        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.COMMAND_MUST_NOT_BE_BLANK);
        }

        if (isTimeoutMode()) {
            return "";
        }

        if (isErrorMode()) {
            return OperationMessages.SIMULATED_PRINTER_FAILURE_RESPONSE;
        }

        return defaultResponseFor(command);
    }

    @Override
    public void disconnect() {
        connected = false;
    }

    private String defaultResponseFor(String command) {
        String normalized = command.trim().toUpperCase(Locale.ROOT);

        return switch (normalized) {
            case "M105" -> PrinterProtocolDefaults.SIMULATED_RESPONSE_M105;
            case "M114" -> PrinterProtocolDefaults.SIMULATED_RESPONSE_M114;
            case "M115" -> PrinterProtocolDefaults.SIMULATED_RESPONSE_M115;
            case "M20" -> PrinterProtocolDefaults.SIMULATED_RESPONSE_M20;
            default -> PrinterProtocolDefaults.SIMULATED_RESPONSE_DEFAULT_OK;
        };
    }

    private boolean isDisconnectedMode() {
        return normalizedMode().equals(PrinterProtocolDefaults.SIM_DISCONNECTED_MODE);
    }

    private boolean isTimeoutMode() {
        return normalizedMode().equals(PrinterProtocolDefaults.SIM_TIMEOUT_MODE);
    }

    private boolean isErrorMode() {
        return normalizedMode().equals(PrinterProtocolDefaults.SIM_ERROR_MODE);
    }

    private String normalizedMode() {
        return mode.trim().toLowerCase(Locale.ROOT);
    }
}
