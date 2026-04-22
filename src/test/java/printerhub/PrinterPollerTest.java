package printerhub;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

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
    void runPolling_connectReturnsFalse_throwsAndDisconnects() {
        FakePrinterPort port = new FakePrinterPort("/dev/ttyUSB0");
        port.connectResult = false;

        TestablePrinterPoller poller = new TestablePrinterPoller(port, 2000, 2000);

        IOException ex = assertThrows(IOException.class, () -> poller.runPolling(1, "M105"));
        assertTrue(ex.getMessage().contains("Failed to connect"));
        assertTrue(port.disconnectCalled);
    }

    @Test
    void runPolling_connectThrows_throwsAndDisconnects() {
        FakePrinterPort port = new FakePrinterPort("/dev/ttyUSB0");
        port.connectException = new IOException("Permission denied");

        TestablePrinterPoller poller = new TestablePrinterPoller(port, 2000, 2000);

        IOException ex = assertThrows(IOException.class, () -> poller.runPolling(1, "M105"));
        assertEquals("Permission denied", ex.getMessage());
        assertTrue(port.disconnectCalled);
    }

    @Test
    void runPolling_readReturnsNull_throwsTimeoutAndDisconnects() {
        FakePrinterPort port = new FakePrinterPort("/dev/ttyUSB0");

        TestablePrinterPoller poller = new TestablePrinterPoller(port, 2000, 2000);

        TimeoutException ex = assertThrows(TimeoutException.class, () -> poller.runPolling(1, "M105"));
        assertTrue(ex.getMessage().contains("No response received"));
        assertTrue(port.disconnectCalled);
    }

    @Test
    void runPolling_readReturnsBlank_throwsTimeoutAndDisconnects() {
        FakePrinterPort port = new FakePrinterPort("/dev/ttyUSB0");
        port.queueResponse("   ");

        TestablePrinterPoller poller = new TestablePrinterPoller(port, 2000, 2000);

        TimeoutException ex = assertThrows(TimeoutException.class, () -> poller.runPolling(1, "M105"));
        assertTrue(ex.getMessage().contains("No response received"));
        assertTrue(port.disconnectCalled);
    }

    @Test
    void runPolling_readThrows_throwsAndDisconnects() {
        FakePrinterPort port = new FakePrinterPort("/dev/ttyUSB0");
        port.readException = new IOException("Read timeout");

        TestablePrinterPoller poller = new TestablePrinterPoller(port, 2000, 2000);

        IOException ex = assertThrows(IOException.class, () -> poller.runPolling(1, "M105"));
        assertEquals("Read timeout", ex.getMessage());
        assertTrue(port.disconnectCalled);
    }

    @Test
    void runPolling_sendThrows_throwsAndDisconnects() {
        FakePrinterPort port = new FakePrinterPort("/dev/ttyUSB0");
        port.sendException = new IOException("Write failed");

        TestablePrinterPoller poller = new TestablePrinterPoller(port, 2000, 2000);

        IOException ex = assertThrows(IOException.class, () -> poller.runPolling(1, "M105"));
        assertEquals("Write failed", ex.getMessage());
        assertTrue(port.disconnectCalled);
    }

    @Test
    void runPolling_invalidPollCount_throwsImmediately() {
        FakePrinterPort port = new FakePrinterPort("/dev/ttyUSB0");
        TestablePrinterPoller poller = new TestablePrinterPoller(port, 2000, 2000);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> poller.runPolling(0, "M105"));
        assertEquals("repeatCount must be greater than 0", ex.getMessage());
    }

    @Test
    void runPolling_blankCommand_throwsImmediately() {
        FakePrinterPort port = new FakePrinterPort("/dev/ttyUSB0");
        TestablePrinterPoller poller = new TestablePrinterPoller(port, 2000, 2000);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> poller.runPolling(1, " "));
        assertEquals("command must not be blank", ex.getMessage());
    }
}