package printerhub.runtime;

import printerhub.PrinterSnapshot;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class PrinterRuntimeStateCache {

    private final ConcurrentMap<String, PrinterSnapshot> snapshots = new ConcurrentHashMap<>();

    public void update(String printerId, PrinterSnapshot snapshot) {
        snapshots.put(printerId, snapshot);
    }

    public Optional<PrinterSnapshot> findByPrinterId(String printerId) {
        return Optional.ofNullable(snapshots.get(printerId));
    }

    public Collection<Map.Entry<String, PrinterSnapshot>> all() {
        return snapshots.entrySet();
    }
}