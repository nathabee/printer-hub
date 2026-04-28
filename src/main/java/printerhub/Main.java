package printerhub;

import printerhub.api.RemoteApiServer;
import printerhub.monitoring.PrinterMonitoringScheduler;
import printerhub.persistence.DatabaseInitializer;
import printerhub.runtime.PrinterHubRuntime;
import printerhub.runtime.PrinterRegistry;
import printerhub.runtime.PrinterRuntimeNode;
import printerhub.runtime.PrinterRuntimeStateCache;
import printerhub.serial.SimulatedPrinterPort;

import java.util.concurrent.CountDownLatch;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws InterruptedException {
        int apiPort = readIntProperty("printerhub.api.port", 8080);
        long monitoringIntervalSeconds = readLongProperty("printerhub.monitoring.intervalSeconds", 5);

        PrinterRegistry printerRegistry = new PrinterRegistry();
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();

        printerRegistry.register(new PrinterRuntimeNode(
                "printer-1",
                "Simulated Printer 1",
                "SIM_PORT_1",
                "simulated",
                new SimulatedPrinterPort("SIM_PORT_1"),
                true
        ));

        printerRegistry.register(new PrinterRuntimeNode(
                "printer-2",
                "Simulated Printer 2",
                "SIM_PORT_2",
                "simulated",
                new SimulatedPrinterPort("SIM_PORT_2"),
                true
        ));

        printerRegistry.register(new PrinterRuntimeNode(
                "printer-3",
                "Simulated Printer 3",
                "SIM_PORT_3",
                "simulated",
                new SimulatedPrinterPort("SIM_PORT_3"),
                true
        ));

        DatabaseInitializer databaseInitializer = new DatabaseInitializer();

        PrinterMonitoringScheduler monitoringScheduler = new PrinterMonitoringScheduler(
                printerRegistry,
                stateCache,
                monitoringIntervalSeconds
        );

        RemoteApiServer apiServer = new RemoteApiServer(
                apiPort,
                printerRegistry,
                stateCache
        );

        PrinterHubRuntime runtime = new PrinterHubRuntime(
                databaseInitializer,
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