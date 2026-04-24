package printerhub.jobs;

public enum PrintJobState {
    CREATED,
    VALIDATED,
    ASSIGNED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}