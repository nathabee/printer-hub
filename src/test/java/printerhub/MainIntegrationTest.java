package printerhub;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErrNormalized;
import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOutNormalized;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainIntegrationTest {

    @AfterEach
    void clearSimulationProperties() {
        Set<String> keys = System.getProperties().stringPropertyNames();
        for (String key : keys) {
            if (key.startsWith("printerhub.sim.") || key.equals("printerhub.initDelayMs")) {
                System.clearProperty(key);
            }
        }
    }

    @Test
    void main_simulatedMode_success_runsThroughMainWrapper() throws Exception {
        System.setProperty("printerhub.initDelayMs", "1");
        System.setProperty(
                "printerhub.sim.response.M105",
                "ok T:21.80 /0.00 B:21.52 /0.00 @:0 B@:0"
        );

        String output = tapSystemOutNormalized(() ->
                Main.main(new String[]{"SIM_PORT", "M105", "1", "1", "sim"})
        );

        assertTrue(output.contains("[INFO] Connected to SIM_PORT"));
        assertTrue(output.contains("---- Poll 1 of 1 ----"));
        assertTrue(output.contains("[SEND] M105"));
        assertTrue(output.contains("[RECV] ok T:21.80 /0.00 B:21.52 /0.00 @:0 B@:0"));
    }

    @Test
    void run_simulatedMode_success_runsThroughNormalChain() throws Exception {
        System.setProperty("printerhub.initDelayMs", "1");
        System.setProperty(
                "printerhub.sim.response.M105",
                "ok T:21.80 /0.00 B:21.52 /0.00 @:0 B@:0"
        );

        String output = tapSystemOutNormalized(() -> {
            int exitCode = Main.run(new String[]{"SIM_PORT", "M105", "2", "1", "sim"});
            assertEquals(0, exitCode);
        });

        assertTrue(output.contains("[INFO] Connected to SIM_PORT"));
        assertTrue(output.contains("---- Poll 1 of 2 ----"));
        assertTrue(output.contains("---- Poll 2 of 2 ----"));
        assertTrue(output.contains("[SEND] M105"));
        assertTrue(output.contains("[RECV] ok T:21.80 /0.00 B:21.52 /0.00 @:0 B@:0"));
    }

    @Test
    void run_simulatedMode_connectFailure_returnsCode1() throws Exception {
        System.setProperty("printerhub.initDelayMs", "1");
        System.setProperty("printerhub.sim.connect", "false");

        final int[] codeHolder = new int[1];
        final String[] stdoutHolder = new String[1];

        String stderr = tapSystemErrNormalized(() -> {
            stdoutHolder[0] = tapSystemOutNormalized(() -> {
                codeHolder[0] = Main.run(new String[]{"SIM_PORT", "M105", "1", "1", "sim"});
                assertEquals(1, codeHolder[0]);
            });
        });

        TestReportWriter.appendScenario(
                "connect failure",
                codeHolder[0],
                stdoutHolder[0],
                stderr
        );

        assertTrue(stderr.contains(
                "[ERROR] Unexpected failure: Failed to open serial port 'SIM_PORT'."
        ));
    }

    @Test
    void run_simulatedMode_noResponse_returnsCode4() throws Exception {
        System.setProperty("printerhub.initDelayMs", "1");
        System.setProperty("printerhub.sim.noresponse.M105", "true");

        final int[] codeHolder = new int[1];
        final String[] stdoutHolder = new String[1];

        String stderr = tapSystemErrNormalized(() -> {
            stdoutHolder[0] = tapSystemOutNormalized(() -> {
                codeHolder[0] = Main.run(new String[]{"SIM_PORT", "M105", "1", "1", "sim"});
                assertEquals(4, codeHolder[0]);
            });
        });

        TestReportWriter.appendScenario(
                "printer timeout",
                codeHolder[0],
                stdoutHolder[0],
                stderr
        );

        assertTrue(stderr.contains(
                "[ERROR] Printer timeout: No response received from printer within"
        ));
    }

    @Test
    void run_success_usesDefaultInitDelayWhenPropertyMissing() throws Exception {
        System.setProperty("printerhub.sim.response.M105", "ok default-delay");

        String output = tapSystemOutNormalized(() -> {
            int exitCode = Main.run(new String[]{"SIM_PORT", "M105", "1", "1", "sim"});
            assertEquals(0, exitCode);
        });

        assertTrue(output.contains("[INFO] Waiting 2 seconds for printer initialization..."));
        assertTrue(output.contains("[RECV] ok default-delay"));
    }

    @Test
    void main_constructor_canBeCalled() {
        Main main = new Main();
        assertEquals(Main.class, main.getClass());
    }

    @Test
    void run_simDisconnected_returnsCode1() throws Exception {
        System.setProperty("printerhub.initDelayMs", "1");

        final int[] codeHolder = new int[1];

        String stderr = tapSystemErrNormalized(() -> {
            codeHolder[0] = Main.run(new String[]{"SIM_PORT", "M105", "1", "1", "sim-disconnected"});
        });

        assertEquals(1, codeHolder[0]);
        assertTrue(stderr.contains("Failed to open serial port 'SIM_PORT'"));
    }

    @Test
    void run_simTimeout_returnsCode4() throws Exception {
        System.setProperty("printerhub.initDelayMs", "1");

        final int[] codeHolder = new int[1];

        String stderr = tapSystemErrNormalized(() -> {
            codeHolder[0] = Main.run(new String[]{"SIM_PORT", "M105", "1", "1", "sim-timeout"});
        });

        assertEquals(4, codeHolder[0]);
        assertTrue(stderr.contains("No response received from printer within"));
    }

    @Test
    void run_simError_returnsCode0AndReceivesErrorResponse() throws Exception {
        System.setProperty("printerhub.initDelayMs", "1");

        String output = tapSystemOutNormalized(() -> {
            int exitCode = Main.run(new String[]{"SIM_PORT", "M105", "1", "1", "sim-error"});
            assertEquals(0, exitCode);
        });

        assertTrue(output.contains("[RECV] Error: Simulated printer failure"));
    }

}