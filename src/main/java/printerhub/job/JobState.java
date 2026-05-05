package printerhub.job;

public enum JobState {
    CREATED,
    QUEUED,
    ASSIGNED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}