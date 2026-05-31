package spaghettichef.local;

import spaghettichef.local.api.RemoteApiServer;
import spaghettichef.local.command.PrinterCommandService;
import spaghettichef.local.command.SdCardService;
import spaghettichef.shared.config.RuntimeDefaults;
import spaghettichef.local.job.AsyncPrintJobExecutor;
import spaghettichef.local.job.PrintFileService;
import spaghettichef.local.job.PrintJobExecutionService;
import spaghettichef.local.job.PrintJobService;
import spaghettichef.local.job.PrinterSdFileService;
import spaghettichef.local.job.PrinterActionGuard;
import spaghettichef.local.job.PrinterActionMapper;
import spaghettichef.local.monitoring.PrinterMonitoringScheduler;
import spaghettichef.local.persistence.DatabaseInitializer;
import spaghettichef.local.persistence.MonitoringRulesStore;
import spaghettichef.local.persistence.PrintFileSettingsStore;
import spaghettichef.local.persistence.PrintFileStore;
import spaghettichef.local.persistence.PrintJobStore;
import spaghettichef.local.persistence.PrinterConfigurationStore;
import spaghettichef.local.persistence.PrinterEventStore;
import spaghettichef.local.persistence.PrintJobExecutionStepStore;
import spaghettichef.local.persistence.PrinterSdFileStore;
import spaghettichef.local.runtime.SpaghettiChefRuntime;
import spaghettichef.local.runtime.PrinterRegistry;
import spaghettichef.local.runtime.PrinterRuntimeStateCache;
import spaghettichef.local.command.SdCardUploadService;
import java.util.concurrent.CountDownLatch;
import spaghettichef.local.persistence.SerialTransferSettingsStore;
import spaghettichef.shared.OperationMessages;

public final class LocalMain {

        private LocalMain() {
        }

        public static void main(String[] args) throws InterruptedException {
                try {
                        int apiPort = readIntProperty(
                                        RuntimeDefaults.API_PORT_PROPERTY,
                                        RuntimeDefaults.DEFAULT_API_PORT);

                        PrinterRegistry printerRegistry = new PrinterRegistry();
                        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();
                        DatabaseInitializer databaseInitializer = new DatabaseInitializer();
                        PrinterConfigurationStore printerConfigurationStore = new PrinterConfigurationStore();
                        MonitoringRulesStore monitoringRulesStore = new MonitoringRulesStore();
                        SerialTransferSettingsStore serialTransferSettingsStore = new SerialTransferSettingsStore();
                        PrintFileSettingsStore printFileSettingsStore = new PrintFileSettingsStore();
                        PrinterEventStore printerEventStore = new PrinterEventStore();
                        PrintFileStore printFileStore = new PrintFileStore();
                        PrinterSdFileStore printerSdFileStore = new PrinterSdFileStore();
                        PrintJobStore printJobStore = new PrintJobStore();

                        PrinterCommandService printerCommandService = new PrinterCommandService(printerEventStore);
                        SdCardService sdCardService = new SdCardService(printerEventStore);

                        PrinterMonitoringScheduler monitoringScheduler = new PrinterMonitoringScheduler(
                                        printerRegistry,
                                        stateCache);

                        PrintJobService printJobService = new PrintJobService(
                                        printJobStore,
                                        printerEventStore);
                        PrintFileService printFileService = new PrintFileService(
                                        printFileStore,
                                        printFileSettingsStore,
                                        java.time.Clock.systemUTC());
                        PrinterSdFileService printerSdFileService = new PrinterSdFileService(
                                        printerSdFileStore,
                                        printFileStore);

                        PrinterActionGuard printerActionGuard = new PrinterActionGuard();

                        SdCardUploadService sdCardUploadService = new SdCardUploadService(
                                        printerRegistry,
                                        monitoringScheduler,
                                        printerActionGuard,
                                        printFileService,
                                        sdCardService,
                                        printerSdFileService,
                                        printerEventStore,
                                        monitoringRulesStore,
                                        serialTransferSettingsStore);

                        PrintJobExecutionService printJobExecutionService = new PrintJobExecutionService(
                                        printJobService,
                                        printerRegistry,
                                        monitoringScheduler,
                                        printerActionGuard,
                                        new PrinterActionMapper(),
                                        new PrintJobExecutionStepStore());

                        AsyncPrintJobExecutor asyncPrintJobExecutor = new AsyncPrintJobExecutor(
                                        printJobService,
                                        printerRegistry,
                                        printerActionGuard,
                                        printJobExecutionService);

                        RemoteApiServer apiServer = new RemoteApiServer(
                                        apiPort,
                                        printerRegistry,
                                        stateCache,
                                        monitoringScheduler,
                                        printerConfigurationStore,
                                        monitoringRulesStore,
                                        printFileSettingsStore,
                                        serialTransferSettingsStore,
                                        printerEventStore,
                                        printerCommandService,
                                        sdCardService,
                                        sdCardUploadService,
                                        printFileService,
                                        printerSdFileService,
                                        printJobService,
                                        asyncPrintJobExecutor,
                                        new PrintJobExecutionStepStore());

                        SpaghettiChefRuntime runtime = new SpaghettiChefRuntime(
                                        databaseInitializer,
                                        printerConfigurationStore,
                                        monitoringRulesStore,
                                        serialTransferSettingsStore,
                                        printerRegistry,
                                        stateCache,
                                        monitoringScheduler,
                                        apiServer);

                        Runtime.getRuntime().addShutdownHook(new Thread(runtime::close));

                        runtime.start();

                        System.out.println(OperationMessages.localRuntimeStarted());
                        System.out.println(OperationMessages.healthEndpoint(apiPort));
                        System.out.println(OperationMessages.printersEndpoint(apiPort));
                        System.out.println(OperationMessages.monitoringSettingsEndpoint(apiPort));

                        new CountDownLatch(1).await();
                } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        System.err.println(OperationMessages.runtimeStartupFailed(
                                        OperationMessages.safeDetail(
                                                        exception.getMessage(),
                                                        OperationMessages.UNKNOWN_STARTUP_ERROR)));
                        throw exception;
                } catch (IllegalArgumentException exception) {
                        System.err.println(OperationMessages.runtimeStartupFailed(
                                        OperationMessages.safeDetail(
                                                        exception.getMessage(),
                                                        OperationMessages.UNKNOWN_STARTUP_ERROR)));
                        System.exit(RuntimeDefaults.ERROR_EXIT_CODE);
                } catch (Exception exception) {
                        System.err.println(OperationMessages.runtimeStartupFailed(
                                        OperationMessages.safeDetail(
                                                        exception.getMessage(),
                                                        OperationMessages.UNKNOWN_STARTUP_ERROR)));
                        exception.printStackTrace(System.err);
                        System.exit(RuntimeDefaults.ERROR_EXIT_CODE);
                }
        }

        private static int readIntProperty(String key, int defaultValue) {
                String value = System.getProperty(key);

                if (value == null || value.isBlank()) {
                        return defaultValue;
                }

                try {
                        return Integer.parseInt(value);
                } catch (NumberFormatException exception) {
                        throw new IllegalArgumentException(
                                        OperationMessages.invalidIntegerSystemProperty(key, value),
                                        exception);
                }
        }
}
