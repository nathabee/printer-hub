package printerhub.monitoring;

import printerhub.OperationMessages;
import printerhub.PrinterSnapshot;
import printerhub.PrinterState;
import printerhub.config.PrinterProtocolDefaults;
import printerhub.config.RuntimeDefaults;
import printerhub.persistence.PrinterEventStore;
import printerhub.persistence.PrinterSnapshotStore;
import printerhub.runtime.PrinterRegistry;
import printerhub.runtime.PrinterRuntimeNode;
import printerhub.runtime.PrinterRuntimeStateCache;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PrinterMonitoringScheduler {

    private final PrinterRegistry printerRegistry;
    private final PrinterRuntimeStateCache stateCache;
    private final PrinterSnapshotStore snapshotStore;
    private final PrinterEventStore eventStore;
    private final long intervalSeconds;
    private final Clock clock;
    private final MonitoringEventPolicy eventPolicy;
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    private ScheduledExecutorService executorService;

    public PrinterMonitoringScheduler(
            PrinterRegistry printerRegistry,
            PrinterRuntimeStateCache stateCache,
            long intervalSeconds
    ) {
        this(
                printerRegistry,
                stateCache,
                new PrinterSnapshotStore(),
                new PrinterEventStore(),
                intervalSeconds
        );
    }

    public PrinterMonitoringScheduler(
            PrinterRegistry printerRegistry,
            PrinterRuntimeStateCache stateCache,
            PrinterSnapshotStore snapshotStore,
            PrinterEventStore eventStore,
            long intervalSeconds
    ) {
        if (printerRegistry == null) {
            throw new IllegalArgumentException(OperationMessages.PRINTER_REGISTRY_MUST_NOT_BE_NULL);
        }
        if (stateCache == null) {
            throw new IllegalArgumentException(OperationMessages.STATE_CACHE_MUST_NOT_BE_NULL);
        }
        if (snapshotStore == null) {
            throw new IllegalArgumentException(OperationMessages.SNAPSHOT_STORE_MUST_NOT_BE_NULL);
        }
        if (eventStore == null) {
            throw new IllegalArgumentException(OperationMessages.EVENT_STORE_MUST_NOT_BE_NULL);
        }
        if (intervalSeconds <= 0) {
            throw new IllegalArgumentException(OperationMessages.INTERVAL_SECONDS_MUST_BE_GREATER_THAN_ZERO);
        }

        this.printerRegistry = printerRegistry;
        this.stateCache = stateCache;
        this.snapshotStore = snapshotStore;
        this.eventStore = eventStore;
        this.intervalSeconds = intervalSeconds;
        this.clock = Clock.systemUTC();
        this.eventPolicy = new MonitoringEventPolicy(
                clock,
                Duration.ofSeconds(RuntimeDefaults.MONITORING_EVENT_DEDUP_WINDOW_SECONDS)
        );
    }

    public synchronized void start() {
        if (executorService != null && !executorService.isShutdown()) {
            return;
        }

        shuttingDown.set(false);

        Collection<PrinterRuntimeNode> nodes = printerRegistry.all();

        int poolSize = Math.max(
                RuntimeDefaults.MIN_MONITORING_EXECUTOR_POOL_SIZE,
                nodes.size() + RuntimeDefaults.MONITORING_EXECUTOR_EXTRA_THREADS
        );
        executorService = Executors.newScheduledThreadPool(poolSize);

        for (PrinterRuntimeNode node : nodes) {
            if (node.enabled()) {
                startMonitoring(node);
            } else {
                initializeDisabledPrinter(node);
            }
        }
    }

    public synchronized void startMonitoring(PrinterRuntimeNode node) {
        if (node == null) {
            throw new IllegalArgumentException(OperationMessages.NODE_MUST_NOT_BE_NULL);
        }

        ensureExecutorStarted();

        stopMonitoring(node.id());

        if (!node.enabled()) {
            initializeDisabledPrinter(node);
            return;
        }

        stateCache.initializePrinter(node.id());

        ScheduledFuture<?> future = executorService.scheduleWithFixedDelay(
                new PrinterMonitoringTask(
                        node,
                        stateCache,
                        snapshotStore,
                        eventStore,
                        clock,
                        PrinterProtocolDefaults.DEFAULT_STATUS_COMMAND,
                        shuttingDown::get,
                        eventPolicy
                ),
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

        eventPolicy.clearPrinter(printerId);
    }

    public synchronized void restartMonitoring(PrinterRuntimeNode node) {
        if (node == null) {
            throw new IllegalArgumentException(OperationMessages.NODE_MUST_NOT_BE_NULL);
        }

        stopMonitoring(node.id());
        startMonitoring(node);
    }

    public synchronized void stop() {
        shuttingDown.set(true);

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

    private void initializeDisabledPrinter(PrinterRuntimeNode node) {
        stateCache.update(
                node.id(),
                PrinterSnapshot.error(
                        PrinterState.DISCONNECTED,
                        null,
                        null,
                        null,
                        OperationMessages.PRINTER_NODE_DISABLED,
                        Instant.now(clock)
                )
        );
        eventPolicy.clearPrinter(node.id());
    }

    private void ensureExecutorStarted() {
        if (executorService == null || executorService.isShutdown()) {
            shuttingDown.set(false);
            executorService = Executors.newScheduledThreadPool(
                    RuntimeDefaults.DEFAULT_MONITORING_EXECUTOR_POOL_SIZE
            );
        }
    }
}