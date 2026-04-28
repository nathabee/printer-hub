package printerhub.persistence;

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
            throw new IllegalArgumentException("temperatureThreshold must not be negative");
        }

        if (minIntervalSeconds < 0) {
            throw new IllegalArgumentException("minIntervalSeconds must not be negative");
        }

        this.snapshotOnStateChange = snapshotOnStateChange;
        this.temperatureThreshold = temperatureThreshold;
        this.minIntervalSeconds = minIntervalSeconds;
    }

    public static MonitoringRules defaults() {
        return new MonitoringRules(true, 1.0, 30);
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