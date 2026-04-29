package printerhub.persistence;

import printerhub.OperationMessages;
import printerhub.config.RuntimeDefaults;

public final class MonitoringRules {

    private final boolean snapshotOnStateChange;
    private final double temperatureThreshold;
    private final long minIntervalSeconds;

    public MonitoringRules(
            boolean snapshotOnStateChange,
            double temperatureThreshold,
            long minIntervalSeconds
    ) {
        if (temperatureThreshold < 0) {
            throw new IllegalArgumentException(
                    OperationMessages.TEMPERATURE_THRESHOLD_MUST_NOT_BE_NEGATIVE
            );
        }

        if (minIntervalSeconds < 0) {
            throw new IllegalArgumentException(
                    OperationMessages.MIN_INTERVAL_SECONDS_MUST_NOT_BE_NEGATIVE
            );
        }

        this.snapshotOnStateChange = snapshotOnStateChange;
        this.temperatureThreshold = temperatureThreshold;
        this.minIntervalSeconds = minIntervalSeconds;
    }

    public static MonitoringRules defaults() {
        return new MonitoringRules(
                RuntimeDefaults.DEFAULT_SNAPSHOT_ON_STATE_CHANGE,
                RuntimeDefaults.DEFAULT_TEMPERATURE_THRESHOLD,
                RuntimeDefaults.DEFAULT_MIN_SNAPSHOT_INTERVAL_SECONDS
        );
    }

    public boolean snapshotOnStateChange() {
        return snapshotOnStateChange;
    }

    public double temperatureThreshold() {
        return temperatureThreshold;
    }

    public long minIntervalSeconds() {
        return minIntervalSeconds;
    }
}