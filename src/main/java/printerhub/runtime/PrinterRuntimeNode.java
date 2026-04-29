package printerhub.runtime;

import printerhub.OperationMessages;
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
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("id"));
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("displayName"));
        }
        if (portName == null || portName.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("portName"));
        }
        if (mode == null || mode.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank("mode"));
        }
        if (printerPort == null) {
            throw new IllegalArgumentException(OperationMessages.PRINTER_PORT_MUST_NOT_BE_NULL);
        }

        this.id = id.trim();
        this.displayName = displayName.trim();
        this.portName = portName.trim();
        this.mode = mode.trim();
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
        } catch (Exception exception) {
            System.err.println(OperationMessages.failedToDisconnectPrinterNode(
                    id,
                    OperationMessages.safeDetail(
                            exception.getMessage(),
                            OperationMessages.UNKNOWN_RUNTIME_CLOSE_ERROR
                    )
            ));
        }
    }
}