package printerhub.serial;

import printerhub.PrinterPort;

import java.util.Locale;

public final class SimulatedPrinterPort implements PrinterPort {

    private final String portName;
    private final String mode;
    private boolean connected;

    public SimulatedPrinterPort(String portName) {
        this(portName, "sim");
    }

    public SimulatedPrinterPort(String portName, String mode) {
        if (portName == null || portName.isBlank()) {
            throw new IllegalArgumentException("portName must not be blank");
        }

        this.portName = portName;
        this.mode = mode == null || mode.isBlank() ? "sim" : mode;
    }

    @Override
    public void connect() {
        if (isDisconnectedMode()) {
            connected = false;
            throw new IllegalStateException("Simulated printer is disconnected: " + portName);
        }

        connected = true;
    }

    @Override
    public String sendCommand(String command) {
        if (!connected) {
            throw new IllegalStateException("Simulated printer is not connected: " + portName);
        }

        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("command must not be blank");
        }

        if (isTimeoutMode()) {
            return "";
        }

        if (isErrorMode()) {
            return "Error: Simulated printer failure";
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
            case "M105" -> "ok T:21.80 /0.00 B:21.52 /0.00 @:0 B@:0";
            case "M114" -> "X:0.00 Y:0.00 Z:0.00 E:0.00 Count X:0 Y:0 Z:0";
            case "M115" -> "FIRMWARE_NAME:Marlin SIMULATED PROTOCOL_VERSION:1.0 MACHINE_TYPE:Ender-3 V2 Neo EXTRUDER_COUNT:1";
            default -> "ok";
        };
    }

    private boolean isDisconnectedMode() {
        return normalizedMode().equals("sim-disconnected");
    }

    private boolean isTimeoutMode() {
        return normalizedMode().equals("sim-timeout");
    }

    private boolean isErrorMode() {
        return normalizedMode().equals("sim-error");
    }

    private String normalizedMode() {
        return mode.trim().toLowerCase(Locale.ROOT);
    }
}