package printerhub.persistence;

import java.time.LocalDateTime;

/**
 * Persistent audit event for printer and job activity.
 */
public final class PrinterEvent {

    private final long id;
    private final String printerId;
    private final String jobId;
    private final String eventType;
    private final String message;
    private final LocalDateTime createdAt;

    public PrinterEvent(long id,
                        String printerId,
                        String jobId,
                        String eventType,
                        String message,
                        LocalDateTime createdAt) {
        this.id = id;
        this.printerId = normalizeOptional(printerId);
        this.jobId = normalizeOptional(jobId);
        this.eventType = normalizeRequired(eventType, "UNKNOWN_EVENT");
        this.message = normalizeOptional(message);
        this.createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
    }

    public static PrinterEvent create(String printerId,
                                      String jobId,
                                      String eventType,
                                      String message) {
        return new PrinterEvent(
                0L,
                printerId,
                jobId,
                eventType,
                message,
                LocalDateTime.now()
        );
    }

    public long getId() {
        return id;
    }

    public String getPrinterId() {
        return printerId;
    }

    public String getJobId() {
        return jobId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getMessage() {
        return message;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeRequired(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}