package printerhub;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErrNormalized;
import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOutNormalized;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainRobustnessTest {

    @AfterEach
    void clearSimulationProperties() {
        Set<String> keys = System.getProperties().stringPropertyNames();
        for (String key : keys) {
            if (key.startsWith("printerhub.sim.") || key.equals("printerhub.initDelayMs")) {
                System.clearProperty(key);
            }
        }
        Thread.interrupted();
    }

    @Test
    void run_invalidRepeatCount_zero_returnsCode2() throws Exception {
        captureAndReportErrorScenario(
                "invalid repeatCount zero",
                new String[]{"SIM_PORT", "M105", "0", "1", "sim"},
                2,
                "repeatCount must be greater than 0"
        );
    }

    @Test
    void run_invalidRepeatCount_negative_returnsCode2() throws Exception {
        captureAndReportErrorScenario(
                "invalid repeatCount negative",
                new String[]{"SIM_PORT", "M105", "-1", "1", "sim"},
                2,
                "repeatCount must be greater than 0, but was -1"
        );
    }

    @Test
    void run_invalidRepeatCount_nonNumeric_returnsCode2() throws Exception {
        captureAndReportErrorScenario(
                "invalid repeatCount non numeric",
                new String[]{"SIM_PORT", "M105", "abc", "1", "sim"},
                2,
                "repeatCount must be a valid integer, but was 'abc'"
        );
    }

    @Test
    void run_invalidDelayMs_zero_returnsCode2() throws Exception {
        captureAndReportErrorScenario(
                "invalid delayMs zero",
                new String[]{"SIM_PORT", "M105", "1", "0", "sim"},
                2,
                "delayMs must be greater than 0, but was 0"
        );
    }

    @Test
    void run_invalidDelayMs_negative_returnsCode2() throws Exception {
        captureAndReportErrorScenario(
                "invalid delayMs negative",
                new String[]{"SIM_PORT", "M105", "1", "-1", "sim"},
                2,
                "delayMs must be greater than 0, but was -1"
        );
    }

    @Test
    void run_invalidDelayMs_nonNumeric_returnsCode2() throws Exception {
        captureAndReportErrorScenario(
                "invalid delayMs non numeric",
                new String[]{"SIM_PORT", "M105", "1", "abc", "sim"},
                2,
                "delayMs must be a valid number, but was 'abc'"
        );
    }

    @Test
    void run_invalidMode_returnsCode2() throws Exception {
        captureAndReportErrorScenario(
                "invalid mode",
                new String[]{"SIM_PORT", "M105", "1", "1", "banana"},
                2,
                "mode must be one of: real, sim, simulated"
        );
    }

    @Test
    void run_invalidInitDelayProperty_returnsCode2() throws Exception {
        System.setProperty("printerhub.initDelayMs", "abc");

        captureAndReportErrorScenario(
                "invalid initDelay property",
                new String[]{"SIM_PORT", "M105", "1", "1", "sim"},
                2,
                "initDelayMs must be a valid number, but was 'abc'"
        );
    }

    @Test
    void run_interrupted_returnsCode3() throws Exception {
        System.setProperty("printerhub.initDelayMs", "1");
        System.setProperty("printerhub.sim.response.M105", "ok");

        final int[] codeHolder = new int[1];
        final String[] stdoutHolder = new String[1];

        String stderr = tapSystemErrNormalized(() -> {
            stdoutHolder[0] = tapSystemOutNormalized(() -> {
                Thread.currentThread().interrupt();
                try {
                    codeHolder[0] = Main.run(new String[]{"SIM_PORT", "M105", "1", "1", "sim"});
                    assertEquals(3, codeHolder[0]);
                } finally {
                    Thread.interrupted();
                }
            });
        });

        TestReportWriter.appendScenario(
                "interrupted execution",
                codeHolder[0],
                stdoutHolder[0],
                stderr
        );

        assertTrue(stderr.contains("[ERROR] Program interrupted:"));
    }

    private void captureAndReportErrorScenario(String scenarioName,
                                               String[] args,
                                               int expectedExitCode,
                                               String expectedErrorFragment) throws Exception {
        final int[] codeHolder = new int[1];
        final String[] stdoutHolder = new String[1];

        String stderr = tapSystemErrNormalized(() -> {
            stdoutHolder[0] = tapSystemOutNormalized(() -> {
                codeHolder[0] = Main.run(args);
                assertEquals(expectedExitCode, codeHolder[0]);
            });
        });

        TestReportWriter.appendScenario(
                scenarioName,
                codeHolder[0],
                stdoutHolder[0],
                stderr
        );

        assertTrue(stderr.contains("[ERROR]"));
        assertTrue(stderr.contains(expectedErrorFragment));
    }
}