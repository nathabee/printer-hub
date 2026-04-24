package printerhub.farm;

import printerhub.PrinterSnapshot;
import printerhub.PrinterStateTracker;

public class PrinterNode {

    private final String id;
    private final String name;
    private final String portName;
    private final String mode;
    private final PrinterStateTracker stateTracker = new PrinterStateTracker();

    private String assignedJobId;

    public PrinterNode(String id, String name, String portName, String mode) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("printer id must not be blank");
        }

        this.id = id.trim();
        this.name = normalize(name, this.id);
        this.portName = normalize(portName, "SIM_PORT");
        this.mode = normalize(mode, "sim");
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPortName() {
        return portName;
    }

    public String getMode() {
        return mode;
    }

    public PrinterStateTracker getStateTracker() {
        return stateTracker;
    }

    public PrinterSnapshot getSnapshot() {
        return stateTracker.getCurrentSnapshot();
    }

    public synchronized String getAssignedJobId() {
        return assignedJobId;
    }

    public synchronized void assignJob(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            throw new IllegalArgumentException("jobId must not be blank");
        }

        assignedJobId = jobId.trim();
    }

    private String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        return value.trim();
    }
}