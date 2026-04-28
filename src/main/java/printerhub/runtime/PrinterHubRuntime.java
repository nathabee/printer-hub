package printerhub.runtime;

import printerhub.api.RemoteApiServer;
import printerhub.monitoring.PrinterMonitoringScheduler;
import printerhub.persistence.DatabaseInitializer;
import printerhub.persistence.PrinterConfigurationStore;

public final class PrinterHubRuntime implements AutoCloseable {

    private final DatabaseInitializer databaseInitializer;
    private final PrinterRegistry printerRegistry;
    private final PrinterRuntimeStateCache stateCache;
    private final PrinterMonitoringScheduler monitoringScheduler;
    private final RemoteApiServer apiServer;
    private final PrinterConfigurationStore printerConfigurationStore;

    public PrinterHubRuntime(
            DatabaseInitializer databaseInitializer,
            PrinterConfigurationStore printerConfigurationStore,
            PrinterRegistry printerRegistry,
            PrinterRuntimeStateCache stateCache,
            PrinterMonitoringScheduler monitoringScheduler,
            RemoteApiServer apiServer
    ) {
        this.databaseInitializer = databaseInitializer;
        this.printerConfigurationStore = printerConfigurationStore;
        this.printerRegistry = printerRegistry;
        this.stateCache = stateCache;
        this.monitoringScheduler = monitoringScheduler;
        this.apiServer = apiServer;
    }

    public void start() {
        databaseInitializer.initialize();
        loadConfiguredPrintersIfAvailable();
        printerRegistry.initialize();
        monitoringScheduler.start();
        apiServer.start();
    }

    private void loadConfiguredPrintersIfAvailable() {
        for (var node : printerConfigurationStore.findAll()) {
            printerRegistry.register(node);
        }
    }

    @Override
    public void close() {
        apiServer.stop();
        monitoringScheduler.stop();
        printerRegistry.close();
    }

    public PrinterRegistry printerRegistry() {
        return printerRegistry;
    }

    public PrinterRuntimeStateCache stateCache() {
        return stateCache;
    }
}