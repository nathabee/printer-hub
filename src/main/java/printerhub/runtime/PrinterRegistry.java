package printerhub.runtime;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class PrinterRegistry implements AutoCloseable {

    private final ConcurrentMap<String, PrinterRuntimeNode> printers = new ConcurrentHashMap<>();

    public void initialize() {
        // 0.1.0 backbone:
        // Printers are registered during bootstrap.
        // Later this will load persisted printer configuration.
    }

    public void register(PrinterRuntimeNode node) {
        printers.put(node.id(), node);
    }

    public Optional<PrinterRuntimeNode> findById(String printerId) {
        return Optional.ofNullable(printers.get(printerId));
    }

    public Collection<PrinterRuntimeNode> all() {
        return printers.values();
    }

    @Override
    public void close() {
        for (PrinterRuntimeNode node : printers.values()) {
            node.close();
        }
    }
}