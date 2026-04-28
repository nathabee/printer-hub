package printerhub;

import printerhub.api.RemoteApiServer;
import printerhub.monitoring.PrinterMonitoringScheduler;
import printerhub.persistence.DatabaseInitializer;
import printerhub.runtime.PrinterHubRuntime;
import printerhub.runtime.PrinterRegistry; 
import printerhub.runtime.PrinterRuntimeStateCache; 
import java.util.concurrent.CountDownLatch;
import printerhub.persistence.PrinterConfigurationStore;

 
 


public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws InterruptedException {
        int apiPort = readIntProperty("printerhub.api.port", 8080);
        long monitoringIntervalSeconds = readLongProperty("printerhub.monitoring.intervalSeconds", 5);

        PrinterRegistry printerRegistry = new PrinterRegistry();
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();

       
        DatabaseInitializer databaseInitializer = new DatabaseInitializer();
 
        PrinterConfigurationStore printerConfigurationStore = new PrinterConfigurationStore();

        

        PrinterMonitoringScheduler monitoringScheduler = new PrinterMonitoringScheduler(
                printerRegistry,
                stateCache,
                monitoringIntervalSeconds);

        RemoteApiServer apiServer = new RemoteApiServer(
            apiPort,
            printerRegistry,
            stateCache,
            monitoringScheduler,
            printerConfigurationStore
        );


        PrinterHubRuntime runtime = new PrinterHubRuntime(
        databaseInitializer,
        printerConfigurationStore,
        printerRegistry,
        stateCache,
        monitoringScheduler,
        apiServer
);

        Runtime.getRuntime().addShutdownHook(new Thread(runtime::close));

        runtime.start();

        System.out.println("[PrinterHub] Local runtime started");
        System.out.println("[PrinterHub] Health:   http://localhost:" + apiPort + "/health");
        System.out.println("[PrinterHub] Printers: http://localhost:" + apiPort + "/printers");

        new CountDownLatch(1).await();
    }

    private static int readIntProperty(String key, int defaultValue) {
        String value = System.getProperty(key);

        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        return Integer.parseInt(value);
    }

    private static long readLongProperty(String key, long defaultValue) {
        String value = System.getProperty(key);

        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        return Long.parseLong(value);
    }
}