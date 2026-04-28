package printerhub.runtime;

import printerhub.PrinterPort;

public final class PrinterRuntimeNode {

    private final String id;
    private final String displayName;
    private final String portName;
    private final String mode;
    private final PrinterPort printerPort;
    private volatile boolean enabled;

    public PrinterRuntimeNode(
            String id,
            String displayName,
            String portName,
            String mode,
            PrinterPort printerPort,
            boolean enabled
    ) {
        this.id = id;
        this.displayName = displayName;
        this.portName = portName;
        this.mode = mode;
        this.printerPort = printerPort;
        this.enabled = enabled;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String portName() {
        return portName;
    }

    public String mode() {
        return mode;
    }

    public PrinterPort printerPort() {
        return printerPort;
    }

    public boolean enabled() {
        return enabled;
    }

    public void enable() {
        enabled = true;
    }

    public void disable() {
        enabled = false;
    }

    public void close() {
        try {
            printerPort.disconnect();
        } catch (Exception ignored) {
            // Continue shutdown even if one printer fails to disconnect.
        }
    }
}