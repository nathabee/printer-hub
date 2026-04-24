package printerhub.serial;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SimulationProfileTest {

    @AfterEach
    void cleanup() {
        SimulationProfile.clearManagedProperties();
    }

    @Test
    void fromMode_acceptsNormalSimulationModes() {
        assertEquals(SimulationProfile.NORMAL, SimulationProfile.fromMode("sim"));
        assertEquals(SimulationProfile.NORMAL, SimulationProfile.fromMode("simulated"));
        assertEquals(SimulationProfile.NORMAL, SimulationProfile.fromMode(" SIM "));
    }

    @Test
    void fromMode_acceptsFailureSimulationModes() {
        assertEquals(SimulationProfile.DISCONNECTED, SimulationProfile.fromMode("sim-disconnected"));
        assertEquals(SimulationProfile.TIMEOUT, SimulationProfile.fromMode("sim-timeout"));
        assertEquals(SimulationProfile.ERROR, SimulationProfile.fromMode("sim-error"));
    }

    @Test
    void fromMode_unknownMode_returnsNull() {
        assertNull(SimulationProfile.fromMode("banana"));
        assertNull(SimulationProfile.fromMode(null));
    }

    @Test
    void disconnectedProfile_setsConnectFalse() {
        SimulationProfile.DISCONNECTED.apply();

        assertEquals("false", System.getProperty("printerhub.sim.connect"));
        assertNull(System.getProperty("printerhub.sim.noresponse.M105"));
        assertNull(System.getProperty("printerhub.sim.response.M105"));
    }

    @Test
    void timeoutProfile_setsNoResponseForM105() {
        SimulationProfile.TIMEOUT.apply();

        assertEquals("true", System.getProperty("printerhub.sim.noresponse.M105"));
        assertNull(System.getProperty("printerhub.sim.connect"));
        assertNull(System.getProperty("printerhub.sim.response.M105"));
    }

    @Test
    void errorProfile_setsErrorResponseForM105() {
        SimulationProfile.ERROR.apply();

        assertEquals(
                "Error: Simulated printer failure",
                System.getProperty("printerhub.sim.response.M105")
        );
        assertNull(System.getProperty("printerhub.sim.connect"));
        assertNull(System.getProperty("printerhub.sim.noresponse.M105"));
    }

    @Test
    void normalProfile_keepsExplicitSimulationOverrides() {
        System.setProperty("printerhub.sim.connect", "false");
        System.setProperty("printerhub.sim.noresponse.M105", "true");
        System.setProperty("printerhub.sim.response.M105", "old");

        SimulationProfile.NORMAL.apply();

        assertEquals("false", System.getProperty("printerhub.sim.connect"));
        assertEquals("true", System.getProperty("printerhub.sim.noresponse.M105"));
        assertEquals("old", System.getProperty("printerhub.sim.response.M105"));
    }
}