package printerhub.farm;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class PrinterFarmStore {

    private final ConcurrentMap<String, PrinterNode> printers = new ConcurrentHashMap<>();

    public PrinterFarmStore(List<PrinterNode> configuredPrinters) {
        reload(configuredPrinters);
    }

    public void reload(List<PrinterNode> configuredPrinters) {
        if (configuredPrinters == null || configuredPrinters.isEmpty()) {
            throw new IllegalArgumentException("configuredPrinters must not be empty");
        }

        printers.clear();

        for (PrinterNode printerNode : configuredPrinters) {
            add(printerNode);
        }
    }

    public void add(PrinterNode printer) {
        if (printer == null) {
            throw new IllegalArgumentException("printer must not be null");
        }

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
        return findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No configured printer available"));
    }

    public int size() {
        return printers.size();
    }
}