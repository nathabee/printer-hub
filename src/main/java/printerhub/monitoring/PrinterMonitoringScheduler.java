package printerhub.monitoring;

import printerhub.runtime.PrinterRegistry;
import printerhub.runtime.PrinterRuntimeNode;
import printerhub.runtime.PrinterRuntimeStateCache;

import java.util.Collection;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class PrinterMonitoringScheduler {

    private final PrinterRegistry printerRegistry;
    private final PrinterRuntimeStateCache stateCache;
    private final long intervalSeconds;

    private ScheduledExecutorService executorService;

    public PrinterMonitoringScheduler(
            PrinterRegistry printerRegistry,
            PrinterRuntimeStateCache stateCache,
            long intervalSeconds
    ) {
        if (printerRegistry == null) {
            throw new IllegalArgumentException("printerRegistry must not be null");
        }
        if (stateCache == null) {
            throw new IllegalArgumentException("stateCache must not be null");
        }
        if (intervalSeconds <= 0) {
            throw new IllegalArgumentException("intervalSeconds must be greater than zero");
        }

        this.printerRegistry = printerRegistry;
        this.stateCache = stateCache;
        this.intervalSeconds = intervalSeconds;
    }

    public synchronized void start() {
        if (executorService != null && !executorService.isShutdown()) {
            return;
        }

        Collection<PrinterRuntimeNode> nodes = printerRegistry.all();

        int poolSize = Math.max(1, nodes.size());
        executorService = Executors.newScheduledThreadPool(poolSize);

        for (PrinterRuntimeNode node : nodes) {
            stateCache.initializePrinter(node.id());

            executorService.scheduleWithFixedDelay(
                    new PrinterMonitoringTask(node, stateCache),
                    0,
                    intervalSeconds,
                    TimeUnit.SECONDS
            );
        }
    }

    public synchronized void stop() {
        if (executorService == null) {
            return;
        }

        executorService.shutdownNow();
        executorService = null;
    }
}