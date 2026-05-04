package printerhub.runtime;

import org.junit.jupiter.api.Test;
import printerhub.PrinterPort;

import static org.junit.jupiter.api.Assertions.*;

class PrinterRuntimeNodeTest {

    @Test
    void initialExecutionStateIsIdle() {
        PrinterRuntimeNode node = createNode();

        assertFalse(node.executionInProgress());
        assertNull(node.activeJobId());
    }

    @Test
    void beginJobExecutionMarksNodeBusy() {
        PrinterRuntimeNode node = createNode();

        node.beginJobExecution("job-1");

        assertTrue(node.executionInProgress());
        assertEquals("job-1", node.activeJobId());
    }

    @Test
    void secondBeginWhileBusyThrows() {
        PrinterRuntimeNode node = createNode();
        node.beginJobExecution("job-1");

        assertThrows(IllegalStateException.class, () -> node.beginJobExecution("job-2"));
    }

    @Test
    void endJobExecutionClearsBusyState() {
        PrinterRuntimeNode node = createNode();
        node.beginJobExecution("job-1");

        node.endJobExecution();

        assertFalse(node.executionInProgress());
        assertNull(node.activeJobId());
    }

    private PrinterRuntimeNode createNode() {
        return new PrinterRuntimeNode(
                "printer-1",
                "Printer 1",
                "SIM_PORT",
                "sim",
                new NoOpPrinterPort(),
                true
        );
    }

    private static final class NoOpPrinterPort implements PrinterPort {
        @Override
        public void connect() {
        }

        @Override
        public String sendCommand(String command) {
            return "ok";
        }

        @Override
        public void disconnect() {
        }
    }
}