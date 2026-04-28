package printerhub.runtime;

import printerhub.PrinterPort;
import printerhub.SerialConnection;
import printerhub.serial.SimulatedPrinterPort;

public final class PrinterRuntimeNodeFactory {

    private static final int DEFAULT_BAUD_RATE = 115200;

    private PrinterRuntimeNodeFactory() {
    }

    public static PrinterRuntimeNode create(
            String id,
            String displayName,
            String portName,
            String mode,
            boolean enabled
    ) {
        validateRequired("id", id);
        validateRequired("displayName", displayName);
        validateRequired("portName", portName);
        validateRequired("mode", mode);

        PrinterPort printerPort = createPort(portName, mode);

        return new PrinterRuntimeNode(
                id.trim(),
                displayName.trim(),
                portName.trim(),
                mode.trim(),
                printerPort,
                enabled
        );
    }

    private static PrinterPort createPort(String portName, String mode) {
        String normalizedMode = mode.trim().toLowerCase();

        if ("real".equals(normalizedMode)) {
            return new SerialConnection(portName.trim(), DEFAULT_BAUD_RATE);
        }

        if ("sim".equals(normalizedMode)
                || "simulated".equals(normalizedMode)
                || "sim-disconnected".equals(normalizedMode)
                || "sim-timeout".equals(normalizedMode)
                || "sim-error".equals(normalizedMode)) {
            return new SimulatedPrinterPort(portName.trim(), normalizedMode);
        }

        throw new IllegalArgumentException(
                "mode must be one of: real, sim, simulated, sim-disconnected, sim-timeout, sim-error"
        );
    }

    private static void validateRequired(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}