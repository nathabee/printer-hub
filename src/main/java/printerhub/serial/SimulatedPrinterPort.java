package printerhub.serial;

import printerhub.PrinterPort;

public final class SimulatedPrinterPort implements PrinterPort {

    private final String portName;
    private boolean connected;

    public SimulatedPrinterPort(String portName) {
        this.portName = portName;
    }

    @Override
    public void connect() {
        connected = true;
    }

    @Override
    public String sendCommand(String command) {
        if (!connected) {
            throw new IllegalStateException("Simulated printer is not connected: " + portName);
        }

        if ("M105".equals(command)) {
            return "ok T:20.0 /0.0 B:20.0 /0.0";
        }

        return "ok";
    }

    @Override
    public void disconnect() {
        connected = false;
    }
}