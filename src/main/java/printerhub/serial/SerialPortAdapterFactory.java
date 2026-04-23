/*
File: src/main/java/printerhub/serial/SerialPortAdapterFactory.java

Purpose:

central place choosing real implementation
later can choose fake/simulated one
*/

package printerhub.serial;

public final class SerialPortAdapterFactory {

    private SerialPortAdapterFactory() {
    }

    public static SerialPortAdapter createReal(String portName) {
        return new JSerialCommPortAdapter(portName);
    }

    public static SerialPortAdapter createSimulated(String portName) {
        return new SimulatedSerialPortAdapter(portName);
    }

    public static SerialPortAdapter create(String portName, String mode) {
        String normalizedMode = mode == null ? "real" : mode.trim().toLowerCase();

        SerialPortAdapter result;
        switch (normalizedMode) {
            case "real":
                result = createReal(portName);
                break;
            case "sim":
            case "simulated":
                result = createSimulated(portName);
                break;
            default:
                throw new IllegalArgumentException(
                        "mode must be one of: real, sim, simulated"
                );
        }
        return result;
    }
}