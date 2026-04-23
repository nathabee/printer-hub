package printerhub;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErrNormalized;
import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOutNormalized;
import static org.junit.jupiter.api.Assertions.*;

class PrinterPollerTest {

    private static class TestablePrinterPoller extends PrinterPoller {
        public int sleepCalls = 0;

        public TestablePrinterPoller(PrinterPort port, long initDelayMs, long betweenPollsMs) {
            super(port, initDelayMs, betweenPollsMs);
        }

        @Override
        protected void sleep(long millis) {
            sleepCalls++;
        }
    }

    @Test
    void runPolling_success_singlePoll_disconnectsAtEnd() throws Exception {
        FakePrinterPort port = new FakePrinterPort("/dev/ttyUSB0");
        port.queueResponse("ok T:20.31 /0.00 B:20.16 /0.00 @:0 B@:0");

        TestablePrinterPoller poller = new TestablePrinterPoller(port, 2000, 2000);
        poller.runPolling(1, "M105");

        assertEquals(1, port.getSentCommands().size());
        assertEquals("M105", port.getSentCommands().get(0));
        assertTrue(port.disconnectCalled);
        assertEquals(1, poller.sleepCalls);
    }

    @Test
    void runPolling_success_multiplePolls_sendsCommandEachTime() throws Exception {
        FakePrinterPort port = new FakePrinterPort("/dev/ttyUSB0");
        port.queueResponse("ok 1");
        port.queueResponse("ok 2");
        port.queueResponse("ok 3");

        TestablePrinterPoller poller = new TestablePrinterPoller(port, 2000, 2000);
        poller.runPolling(3, "M105");

        assertEquals(3, port.getSentCommands().size());
        assertEquals("M105", port.getSentCommands().get(0));
        assertEquals("M105", port.getSentCommands().get(1));
        assertEquals("M105", port.getSentCommands().get(2));
        assertTrue(port.disconnectCalled);
        assertEquals(3, poller.sleepCalls);
    }

    @Test
    void runPolling_connectReturnsFalse_throwsAndDisconnects() throws Exception {
        FakePrinterPort port = new FakePrinterPort("/dev/ttyUSB0");
        port.connectResult = false;

        TestablePrinterPoller poller = new TestablePrinterPoller(port, 2000, 2000);

        captureAndReportIOExceptionScenario(
                "poller connect returned false",
                poller,
                port,
                "M105",
                "Failed to connect"
        );
    }

    @Test
    void runPolling_connectThrows_throwsAndDisconnects() throws Exception {
        FakePrinterPort port = new FakePrinterPort("/dev/ttyUSB0");
        port.connectException = new IOException("Permission denied");

        TestablePrinterPoller poller = new TestablePrinterPoller(port, 2000, 2000);

        captureAndReportIOExceptionScenario(
                "poller connect threw IOException",
                poller,
                port,
                "M105",
                "Permission denied"
        );
    }

    @Test
    void runPolling_readReturnsNull_throwsTimeoutAndDisconnects() throws Exception {
        FakePrinterPort port = new FakePrinterPort("/dev/ttyUSB0");

        TestablePrinterPoller poller = new TestablePrinterPoller(port, 2000, 2000);

        captureAndReportTimeoutScenario(
                "poller no response null",
                poller,
                port,
                "M105",
                "No response received"
        );
    }

    @Test
    void runPolling_readReturnsBlank_throwsTimeoutAndDisconnects() throws Exception {
        FakePrinterPort port = new FakePrinterPort("/dev/ttyUSB0");
        port.queueResponse("   ");

        TestablePrinterPoller poller = new TestablePrinterPoller(port, 2000, 2000);

        captureAndReportTimeoutScenario(
                "poller blank response",
                poller,
                port,
                "M105",
                "No response received"
        );
    }

    @Test
    void runPolling_readThrows_throwsAndDisconnects() throws Exception {
        FakePrinterPort port = new FakePrinterPort("/dev/ttyUSB0");
        port.readException = new IOException("Read timeout");

        TestablePrinterPoller poller = new TestablePrinterPoller(port, 2000, 2000);

        captureAndReportIOExceptionScenario(
                "poller read threw IOException",
                poller,
                port,
                "M105",
                "Read timeout"
        );
    }

    @Test
    void runPolling_sendThrows_throwsAndDisconnects() throws Exception {
        FakePrinterPort port = new FakePrinterPort("/dev/ttyUSB0");
        port.sendException = new IOException("Write failed");

        TestablePrinterPoller poller = new TestablePrinterPoller(port, 2000, 2000);

        captureAndReportIOExceptionScenario(
                "poller send threw IOException",
                poller,
                port,
                "M105",
                "Write failed"
        );
    }

    @Test
    void runPolling_invalidPollCount_throwsImmediately() throws Exception {
        FakePrinterPort port = new FakePrinterPort("/dev/ttyUSB0");
        TestablePrinterPoller poller = new TestablePrinterPoller(port, 2000, 2000);

        captureAndReportIllegalArgumentScenario(
                "poller invalid repeatCount",
                poller,
                port,
                0,
                "M105",
                "repeatCount must be greater than 0"
        );
    }

    @Test
    void runPolling_blankCommand_throwsImmediately() throws Exception {
        FakePrinterPort port = new FakePrinterPort("/dev/ttyUSB0");
        TestablePrinterPoller poller = new TestablePrinterPoller(port, 2000, 2000);

        captureAndReportIllegalArgumentScenario(
                "poller blank command",
                poller,
                port,
                1,
                " ",
                "command must not be blank"
        );
    }

    private void captureAndReportIOExceptionScenario(String scenarioName,
                                                     TestablePrinterPoller poller,
                                                     FakePrinterPort port,
                                                     String command,
                                                     String expectedMessagePart) throws Exception {
        final String[] stdoutHolder = new String[1];
        final String[] stderrHolder = new String[1];

        IOException ex = assertThrows(IOException.class, () -> {
            stderrHolder[0] = tapSystemErrNormalized(() -> {
                stdoutHolder[0] = tapSystemOutNormalized(() -> poller.runPolling(1, command));
            });
        });

        assertTrue(ex.getMessage().contains(expectedMessagePart));
        assertTrue(port.disconnectCalled);

        TestReportWriter.appendScenario(
                scenarioName,
                1,
                stdoutHolder[0],
                buildExceptionReport(ex, stderrHolder[0])
        );
    }

    private void captureAndReportTimeoutScenario(String scenarioName,
                                                 TestablePrinterPoller poller,
                                                 FakePrinterPort port,
                                                 String command,
                                                 String expectedMessagePart) throws Exception {
        final String[] stdoutHolder = new String[1];
        final String[] stderrHolder = new String[1];

        TimeoutException ex = assertThrows(TimeoutException.class, () -> {
            stderrHolder[0] = tapSystemErrNormalized(() -> {
                stdoutHolder[0] = tapSystemOutNormalized(() -> poller.runPolling(1, command));
            });
        });

        assertTrue(ex.getMessage().contains(expectedMessagePart));
        assertTrue(port.disconnectCalled);

        TestReportWriter.appendScenario(
                scenarioName,
                4,
                stdoutHolder[0],
                buildExceptionReport(ex, stderrHolder[0])
        );
    }

    private void captureAndReportIllegalArgumentScenario(String scenarioName,
                                                         TestablePrinterPoller poller,
                                                         FakePrinterPort port,
                                                         int repeatCount,
                                                         String command,
                                                         String expectedMessagePart) throws Exception {
        final String[] stdoutHolder = new String[1];
        final String[] stderrHolder = new String[1];

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            stderrHolder[0] = tapSystemErrNormalized(() -> {
                stdoutHolder[0] = tapSystemOutNormalized(() -> poller.runPolling(repeatCount, command));
            });
        });

        assertTrue(ex.getMessage().contains(expectedMessagePart));

        TestReportWriter.appendScenario(
                scenarioName,
                2,
                stdoutHolder[0],
                buildExceptionReport(ex, stderrHolder[0])
        );
    }

    private String buildExceptionReport(Exception ex, String capturedStderr) {
        StringBuilder report = new StringBuilder();
        report.append(ex.getClass().getSimpleName()).append(": ").append(ex.getMessage());

        if (capturedStderr != null && !capturedStderr.isBlank()) {
            report.append(System.lineSeparator())
                  .append(System.lineSeparator())
                  .append("Captured stderr:")
                  .append(System.lineSeparator())
                  .append(capturedStderr.stripTrailing());
        }

        return report.toString();
    }
}