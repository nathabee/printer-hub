package printerhub.monitoring;

import printerhub.PrinterSnapshot;
import printerhub.PrinterState;
import printerhub.runtime.PrinterRuntimeNode;
import printerhub.runtime.PrinterRuntimeStateCache;

import java.time.Instant;

public final class PrinterMonitoringTask implements Runnable {

    private final PrinterRuntimeNode node;
    private final PrinterRuntimeStateCache stateCache;

    public PrinterMonitoringTask(
            PrinterRuntimeNode node,
            PrinterRuntimeStateCache stateCache
    ) {
        this.node = node;
        this.stateCache = stateCache;
    }

    @Override
    public void run() {
        if (!node.enabled()) {
            return;
        }

        try {
            node.printerPort().connect();

            String response = node.printerPort().sendCommand("M105");

            PrinterSnapshot snapshot = PrinterSnapshot.fromResponse(
                    PrinterState.IDLE,
                    response,
                    Instant.now()
            );

            stateCache.update(node.id(), snapshot);
        } catch (Exception ex) {
            PrinterSnapshot snapshot = PrinterSnapshot.error(
                    PrinterState.ERROR,
                    ex.getMessage(),
                    Instant.now()
            );

            stateCache.update(node.id(), snapshot);
        }
    }
}