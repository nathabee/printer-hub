package printerhub.runtime;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class PrinterRegistry implements AutoCloseable {

    private final ConcurrentMap<String, PrinterRuntimeNode> printers = new ConcurrentHashMap<>();

    public void initialize() {
        // 0.1.x migration bootstrap.
        // Later this will load persisted printer configuration.
    }

    public void register(PrinterRuntimeNode node) {
        validateNode(node);
        printers.put(node.id(), node);
    }

    public Optional<PrinterRuntimeNode> findById(String printerId) {
        if (printerId == null || printerId.isBlank()) {
            return Optional.empty();
        }

        return Optional.ofNullable(printers.get(printerId));
    }

    public Collection<PrinterRuntimeNode> all() {
        return printers.values();
    }

    public PrinterRuntimeNode remove(String printerId) {
        if (printerId == null || printerId.isBlank()) {
            throw new IllegalArgumentException("printerId must not be blank");
        }

        PrinterRuntimeNode removed = printers.remove(printerId);

        if (removed != null) {
            removed.close();
        }

        return removed;
    }

    private void validateNode(PrinterRuntimeNode node) {
        if (node == null) {
            throw new IllegalArgumentException("node must not be null");
        }
        if (node.id() == null || node.id().isBlank()) {
            throw new IllegalArgumentException("printer id must not be blank");
        }
    }

    @Override
    public void close() {
        for (PrinterRuntimeNode node : printers.values()) {
            node.close();
        }
    }
}