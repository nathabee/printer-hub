package printerhub.runtime;

import printerhub.PrinterSnapshot;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class PrinterRuntimeStateCache {

    private final Map<String, PrinterSnapshot> snapshotsByPrinterId = new ConcurrentHashMap<>();

    public void initializePrinter(String printerId) {
        update(printerId, PrinterSnapshot.disconnected(Instant.now()));
    }

    public void update(String printerId, PrinterSnapshot snapshot) {
        if (printerId == null || printerId.isBlank()) {
            throw new IllegalArgumentException("printerId must not be blank");
        }
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }

        snapshotsByPrinterId.put(printerId, snapshot);
    }

    public Optional<PrinterSnapshot> findByPrinterId(String printerId) {
        if (printerId == null || printerId.isBlank()) {
            return Optional.empty();
        }

        return Optional.ofNullable(snapshotsByPrinterId.get(printerId));
    }

    public PrinterSnapshot currentOrDisconnected(String printerId) {
        return findByPrinterId(printerId)
                .orElseGet(() -> PrinterSnapshot.disconnected(Instant.now()));
    }

    public Collection<PrinterSnapshot> findAll() {
        return snapshotsByPrinterId.values();
    }

    public void clear() {
        snapshotsByPrinterId.clear();
    }

    public void remove(String printerId) {
        if (printerId == null || printerId.isBlank()) {
            return;
        }

        snapshotsByPrinterId.remove(printerId);
    }
}