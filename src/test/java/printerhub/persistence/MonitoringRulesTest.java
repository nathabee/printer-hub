package printerhub.persistence;

import org.junit.jupiter.api.Test;
import printerhub.config.RuntimeDefaults;

import static org.junit.jupiter.api.Assertions.*;

class MonitoringRulesTest {

    @Test
    void constructorStoresValues() {
        MonitoringRules rules = new MonitoringRules(true, 2.5, 45);

        assertTrue(rules.snapshotOnStateChange());
        assertEquals(2.5, rules.temperatureThreshold());
        assertEquals(45, rules.minIntervalSeconds());
    }

    @Test
    void defaultsUseRuntimeDefaults() {
        MonitoringRules rules = MonitoringRules.defaults();

        assertEquals(
                RuntimeDefaults.DEFAULT_SNAPSHOT_ON_STATE_CHANGE,
                rules.snapshotOnStateChange()
        );
        assertEquals(
                RuntimeDefaults.DEFAULT_TEMPERATURE_THRESHOLD,
                rules.temperatureThreshold()
        );
        assertEquals(
                RuntimeDefaults.DEFAULT_MIN_SNAPSHOT_INTERVAL_SECONDS,
                rules.minIntervalSeconds()
        );
    }

    @Test
    void constructorAllowsZeroTemperatureThreshold() {
        MonitoringRules rules = new MonitoringRules(true, 0.0, 30);

        assertEquals(0.0, rules.temperatureThreshold());
    }

    @Test
    void constructorAllowsZeroMinIntervalSeconds() {
        MonitoringRules rules = new MonitoringRules(true, 1.0, 0);

        assertEquals(0, rules.minIntervalSeconds());
    }

    @Test
    void constructorFailsForNegativeTemperatureThreshold() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new MonitoringRules(true, -0.1, 30)
        );

        assertEquals(
                "temperatureThreshold must not be negative",
                exception.getMessage()
        );
    }

    @Test
    void constructorFailsForNegativeMinIntervalSeconds() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new MonitoringRules(true, 1.0, -1)
        );

        assertEquals(
                "minIntervalSeconds must not be negative",
                exception.getMessage()
        );
    }

    @Test
    void constructorStoresFalseSnapshotOnStateChange() {
        MonitoringRules rules = new MonitoringRules(false, 1.0, 30);

        assertFalse(rules.snapshotOnStateChange());
    }
}