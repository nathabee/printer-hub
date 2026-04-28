package printerhub.monitoring;

import printerhub.runtime.PrinterRegistry;
import printerhub.runtime.PrinterRuntimeNode;
import printerhub.runtime.PrinterRuntimeStateCache;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class PrinterMonitoringScheduler {

    private final PrinterRegistry printerRegistry;
    private final PrinterRuntimeStateCache stateCache;
    private final long intervalSeconds;
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

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

        int poolSize = Math.max(1, nodes.size() + 4);
        executorService = Executors.newScheduledThreadPool(poolSize);

        for (PrinterRuntimeNode node : nodes) {
            startMonitoring(node);
        }
    }

    public synchronized void startMonitoring(PrinterRuntimeNode node) {
        if (node == null) {
            throw new IllegalArgumentException("node must not be null");
        }

        ensureExecutorStarted();

        stopMonitoring(node.id());

        stateCache.initializePrinter(node.id());

        ScheduledFuture<?> future = executorService.scheduleWithFixedDelay(
                new PrinterMonitoringTask(node, stateCache),
                0,
                intervalSeconds,
                TimeUnit.SECONDS
        );

        scheduledTasks.put(node.id(), future);
    }

    public synchronized void stopMonitoring(String printerId) {
        if (printerId == null || printerId.isBlank()) {
            return;
        }

        ScheduledFuture<?> future = scheduledTasks.remove(printerId);

        if (future != null) {
            future.cancel(true);
        }
    }

    public synchronized void restartMonitoring(PrinterRuntimeNode node) {
        stopMonitoring(node.id());
        startMonitoring(node);
    }

    public synchronized void stop() {
        for (ScheduledFuture<?> future : scheduledTasks.values()) {
            future.cancel(true);
        }

        scheduledTasks.clear();

        if (executorService == null) {
            return;
        }

        executorService.shutdownNow();
        executorService = null;
    }

    private void ensureExecutorStarted() {
        if (executorService == null || executorService.isShutdown()) {
            executorService = Executors.newScheduledThreadPool(8);
        }
    }
}