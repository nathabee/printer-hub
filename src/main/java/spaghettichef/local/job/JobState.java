package spaghettichef.local.job;

public enum JobState {
    CREATED,
    QUEUED,
    ASSIGNED,
    RUNNING,
    PAUSED,
    CANCELLING,
    COMPLETED,
    FAILED,
    CANCELLED
}