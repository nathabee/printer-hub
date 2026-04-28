package printerhub.monitoring;

import printerhub.runtime.PrinterRegistry;
import printerhub.runtime.PrinterRuntimeNode;
import printerhub.runtime.PrinterRuntimeStateCache;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class PrinterMonitoringScheduler {

    private final PrinterRegistry printerRegistry;
    private final PrinterRuntimeStateCache stateCache;
    private final ScheduledExecutorService executor;
    private final long intervalSeconds;

    public PrinterMonitoringScheduler(
            PrinterRegistry printerRegistry,
            PrinterRuntimeStateCache stateCache,
            long intervalSeconds
    ) {
        this.printerRegistry = printerRegistry;
        this.stateCache = stateCache;
        this.intervalSeconds = intervalSeconds;
        this.executor = Executors.newScheduledThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors())
        );
    }

    public void start() {
        for (PrinterRuntimeNode node : printerRegistry.all()) {
            PrinterMonitoringTask task = new PrinterMonitoringTask(node, stateCache);
            executor.scheduleWithFixedDelay(task, 0, intervalSeconds, TimeUnit.SECONDS);
        }
    }

    public void stop() {
        executor.shutdownNow();
    }
}