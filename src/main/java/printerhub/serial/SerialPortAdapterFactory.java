/*
File: src/main/java/printerhub/serial/SerialPortAdapterFactory.java

Purpose:

central place choosing real implementation
or simulated runtime implementation
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

        if ("real".equals(normalizedMode)) {
            SimulationProfile.clearManagedProperties();
            return createReal(portName);
        }

        SimulationProfile profile = SimulationProfile.fromMode(normalizedMode);

        if (profile != null) {
            profile.apply();
            return createSimulated(portName);
        }

        throw new IllegalArgumentException(
                "mode must be one of: real, sim, simulated, sim-disconnected, sim-timeout, sim-error"
        );
    }
}