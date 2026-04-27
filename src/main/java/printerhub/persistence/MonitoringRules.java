package printerhub.persistence;

public class MonitoringRules {

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

    public boolean isSnapshotOnStateChange() {
        return snapshotOnStateChange;
    }

    public double getTemperatureThreshold() {
        return temperatureThreshold;
    }

    public long getMinIntervalSeconds() {
        return minIntervalSeconds;
    }
}