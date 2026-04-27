package printerhub.farm;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class PrinterFarmStore {

    private static final String DEFAULT_PRINTER_ID = "printer-1";

    private final ConcurrentMap<String, PrinterNode> printers = new ConcurrentHashMap<>();

    public PrinterFarmStore(String primaryPortName, String primaryMode) {

        if (primaryPortName == null || primaryPortName.isBlank()) {
            throw new IllegalArgumentException("primaryPortName must not be blank");
        }

        if (primaryMode == null || primaryMode.isBlank()) {
            throw new IllegalArgumentException("primaryMode must not be blank");
        }

        add(new PrinterNode(
                DEFAULT_PRINTER_ID,
                "Primary printer",
                primaryPortName,
                primaryMode
        ));
    }

    /*public PrinterFarmStore(String primaryPortName, String primaryMode) {
        add(new PrinterNode(DEFAULT_PRINTER_ID, "Primary printer", primaryPortName, primaryMode));
        add(new PrinterNode("printer-2", "Simulated printer 2", "SIM_PORT_2", "sim"));
        add(new PrinterNode("printer-3", "Simulated printer 3", "SIM_PORT_3", "sim"));
    }*/

    public void add(PrinterNode printer) {
        printers.put(printer.getId(), printer);
    }

    public List<PrinterNode> findAll() {
        return new ArrayList<>(printers.values());
    }

    public Optional<PrinterNode> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }

        return Optional.ofNullable(printers.get(id.trim()));
    }

    public PrinterNode getDefaultPrinter() {
        return printers.get(DEFAULT_PRINTER_ID);
    }

    public int size() {
        return printers.size();
    }
}