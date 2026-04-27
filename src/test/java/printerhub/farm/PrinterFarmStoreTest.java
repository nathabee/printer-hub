package printerhub.farm;

import org.junit.jupiter.api.Test;
import printerhub.PrinterState;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PrinterFarmStoreTest {

    @Test
    void constructor_createsConfiguredPrinterFarm() {
        PrinterFarmStore store = new PrinterFarmStore(List.of(
                new PrinterNode("printer-1", "Primary printer", "SIM_PORT", "sim")
        ));

        assertEquals(1, store.size());
        assertTrue(store.findById("printer-1").isPresent());
    }

    @Test
    void defaultPrinter_usesConfiguredPortAndMode() {
        PrinterFarmStore store = new PrinterFarmStore(List.of(
                new PrinterNode("printer-1", "Primary printer", "CUSTOM_PORT", "sim-error")
        ));

        PrinterNode defaultPrinter = store.getDefaultPrinter();

        assertEquals("printer-1", defaultPrinter.getId());
        assertEquals("CUSTOM_PORT", defaultPrinter.getPortName());
        assertEquals("sim-error", defaultPrinter.getMode());
    }

    @Test
    void findById_blank_returnsEmpty() {
        PrinterFarmStore store = new PrinterFarmStore(List.of(
                new PrinterNode("printer-1", "Primary printer", "SIM_PORT", "sim")
        ));

        assertTrue(store.findById(null).isEmpty());
        assertTrue(store.findById(" ").isEmpty());
    }

    @Test
    void printerNode_initialStateIsDisconnected() {
        PrinterNode node = new PrinterNode("printer-x", "Printer X", "SIM_PORT_X", "sim");

        assertEquals(PrinterState.DISCONNECTED, node.getSnapshot().getState());
    }

    @Test
    void printerNode_assignJob_setsAssignedJobId() {
        PrinterNode node = new PrinterNode("printer-x", "Printer X", "SIM_PORT_X", "sim");

        node.assignJob("job-1");

        assertEquals("job-1", node.getAssignedJobId());
    }

    @Test
    void printerNode_blankId_isRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PrinterNode(" ", "Printer X", "SIM_PORT_X", "sim")
        );
    }
}