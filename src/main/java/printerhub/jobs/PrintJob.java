package printerhub.jobs;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public final class PrintJob {

    private final String id;
    private final String name;
    private final PrintJobType type;
    private final PrintJobState state;
    private final String assignedPrinterId;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public PrintJob(String id,
                    String name,
                    PrintJobType type,
                    PrintJobState state,
                    String assignedPrinterId,
                    LocalDateTime createdAt,
                    LocalDateTime updatedAt) {
        this.id = normalizeId(id);
        this.name = normalizeName(name);
        this.type = type == null ? PrintJobType.SIMULATED : type;
        this.state = state == null ? PrintJobState.CREATED : state;
        this.assignedPrinterId = normalizeOptional(assignedPrinterId);
        this.createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
        this.updatedAt = updatedAt == null ? this.createdAt : updatedAt;
    }

    public static PrintJob create(String name, PrintJobType type) {
        LocalDateTime now = LocalDateTime.now();

        return new PrintJob(
                UUID.randomUUID().toString(),
                name,
                type,
                PrintJobState.CREATED,
                null,
                now,
                now
        );
    }

    public PrintJob withState(PrintJobState nextState) {
        return new PrintJob(
                id,
                name,
                type,
                nextState,
                assignedPrinterId,
                createdAt,
                LocalDateTime.now()
        );
    }

    public PrintJob assignedTo(String printerId) {
        return new PrintJob(
                id,
                name,
                type,
                PrintJobState.ASSIGNED,
                printerId,
                createdAt,
                LocalDateTime.now()
        );
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public PrintJobType getType() {
        return type;
    }

    public PrintJobState getState() {
        return state;
    }

    public String getAssignedPrinterId() {
        return assignedPrinterId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    private String normalizeId(String id) {
        if (id == null || id.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return id.trim();
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            return "unnamed-job";
        }
        return name.trim();
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    @Override
    public String toString() {
        return "PrintJob{"
                + "id='" + id + '\''
                + ", name='" + name + '\''
                + ", type=" + type
                + ", state=" + state
                + ", assignedPrinterId='" + assignedPrinterId + '\''
                + ", createdAt=" + createdAt
                + ", updatedAt=" + updatedAt
                + '}';
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof PrintJob printJob)) {
            return false;
        }

        return Objects.equals(id, printJob.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}