package spaghettichef.local.runtime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import spaghettichef.local.api.RemoteApiServer;
import spaghettichef.local.command.PrinterCommandService;
import spaghettichef.local.command.SdCardService;
import spaghettichef.local.monitoring.PrinterMonitoringScheduler;
import spaghettichef.local.persistence.DatabaseInitializer;
import spaghettichef.local.persistence.MonitoringRulesStore;
import spaghettichef.local.persistence.PrintFileSettingsStore;
import spaghettichef.local.persistence.PrintFileStore;
import spaghettichef.local.persistence.PrintJobExecutionStepStore;
import spaghettichef.local.persistence.PrinterConfigurationStore;
import spaghettichef.local.persistence.PrinterEventStore;
import spaghettichef.local.persistence.PrinterSdFileStore;
import spaghettichef.local.job.AsyncPrintJobExecutor;
import spaghettichef.local.job.PrintFileService;
import spaghettichef.local.job.PrintJobExecutionService;
import spaghettichef.local.job.PrintJobService;
import spaghettichef.local.job.PrinterActionGuard;
import spaghettichef.local.job.PrinterActionMapper;
import spaghettichef.local.job.PrinterSdFileService;
import spaghettichef.local.persistence.PrintJobStore;
import spaghettichef.local.command.SdCardUploadService;
import spaghettichef.local.persistence.SerialTransferSettingsStore;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SpaghettiChefRuntimeTest {

        @TempDir
        Path tempDir;

        @AfterEach
        void clearDatabaseProperty() {
                System.clearProperty("spaghettichef.databaseFile");
        }

        @Test
        void constructorFailsWhenDatabaseInitializerIsNull() {
                IllegalArgumentException exception = assertThrows(
                                IllegalArgumentException.class,
                                () -> new SpaghettiChefRuntime(
                                                null,
                                                new PrinterConfigurationStore(),
                                                new MonitoringRulesStore(),
                                                new SerialTransferSettingsStore(),
                                                new PrinterRegistry(),
                                                new PrinterRuntimeStateCache(),
                                                new PrinterMonitoringScheduler(new PrinterRegistry(),
                                                                new PrinterRuntimeStateCache()),
                                                createApiServer(new PrinterRegistry(), new PrinterRuntimeStateCache(),
                                                                new PrinterMonitoringScheduler(new PrinterRegistry(),
                                                                                new PrinterRuntimeStateCache()))));

                assertEquals("databaseInitializer must not be null", exception.getMessage());
        }

        @Test
        void constructorFailsWhenPrinterConfigurationStoreIsNull() {
                IllegalArgumentException exception = assertThrows(
                                IllegalArgumentException.class,
                                () -> new SpaghettiChefRuntime(
                                                new DatabaseInitializer(),
                                                null,
                                                new MonitoringRulesStore(),
                                                new SerialTransferSettingsStore(),
                                                new PrinterRegistry(),
                                                new PrinterRuntimeStateCache(),
                                                new PrinterMonitoringScheduler(new PrinterRegistry(),
                                                                new PrinterRuntimeStateCache()),
                                                createApiServer(new PrinterRegistry(), new PrinterRuntimeStateCache(),
                                                                new PrinterMonitoringScheduler(new PrinterRegistry(),
                                                                                new PrinterRuntimeStateCache()))));

                assertEquals("printerConfigurationStore must not be null", exception.getMessage());
        }

        @Test
        void constructorFailsWhenMonitoringRulesStoreIsNull() {
                IllegalArgumentException exception = assertThrows(
                                IllegalArgumentException.class,
                                () -> new SpaghettiChefRuntime(
                                                new DatabaseInitializer(),
                                                new PrinterConfigurationStore(),
                                                null,
                                                new SerialTransferSettingsStore(),
                                                new PrinterRegistry(),
                                                new PrinterRuntimeStateCache(),
                                                new PrinterMonitoringScheduler(new PrinterRegistry(),
                                                                new PrinterRuntimeStateCache()),
                                                createApiServer(new PrinterRegistry(), new PrinterRuntimeStateCache(),
                                                                new PrinterMonitoringScheduler(new PrinterRegistry(),
                                                                                new PrinterRuntimeStateCache()))));

                assertEquals("monitoringRulesStore must not be null", exception.getMessage());
        }

        @Test
        void constructorFailsWhenPrinterRegistryIsNull() {
                IllegalArgumentException exception = assertThrows(
                                IllegalArgumentException.class,
                                () -> new SpaghettiChefRuntime(
                                                new DatabaseInitializer(),
                                                new PrinterConfigurationStore(),
                                                new MonitoringRulesStore(),
                                                new SerialTransferSettingsStore(),
                                                null,
                                                new PrinterRuntimeStateCache(),
                                                new PrinterMonitoringScheduler(new PrinterRegistry(),
                                                                new PrinterRuntimeStateCache()),
                                                createApiServer(new PrinterRegistry(), new PrinterRuntimeStateCache(),
                                                                new PrinterMonitoringScheduler(new PrinterRegistry(),
                                                                                new PrinterRuntimeStateCache()))));

                assertEquals("printerRegistry must not be null", exception.getMessage());
        }

        @Test
        void constructorFailsWhenStateCacheIsNull() {
                IllegalArgumentException exception = assertThrows(
                                IllegalArgumentException.class,
                                () -> new SpaghettiChefRuntime(
                                                new DatabaseInitializer(),
                                                new PrinterConfigurationStore(),
                                                new MonitoringRulesStore(),
                                                new SerialTransferSettingsStore(),
                                                new PrinterRegistry(),
                                                null,
                                                new PrinterMonitoringScheduler(new PrinterRegistry(),
                                                                new PrinterRuntimeStateCache()),
                                                createApiServer(new PrinterRegistry(), new PrinterRuntimeStateCache(),
                                                                new PrinterMonitoringScheduler(new PrinterRegistry(),
                                                                                new PrinterRuntimeStateCache()))));

                assertEquals("stateCache must not be null", exception.getMessage());
        }

        @Test
        void constructorFailsWhenMonitoringSchedulerIsNull() {
                IllegalArgumentException exception = assertThrows(
                                IllegalArgumentException.class,
                                () -> new SpaghettiChefRuntime(
                                                new DatabaseInitializer(),
                                                new PrinterConfigurationStore(),
                                                new MonitoringRulesStore(),
                                                new SerialTransferSettingsStore(),
                                                new PrinterRegistry(),
                                                new PrinterRuntimeStateCache(),
                                                null,
                                                createApiServer(new PrinterRegistry(), new PrinterRuntimeStateCache(),
                                                                new PrinterMonitoringScheduler(new PrinterRegistry(),
                                                                                new PrinterRuntimeStateCache()))));

                assertEquals("monitoringScheduler must not be null", exception.getMessage());
        }

        @Test
        void constructorFailsWhenApiServerIsNull() {
                IllegalArgumentException exception = assertThrows(
                                IllegalArgumentException.class,
                                () -> new SpaghettiChefRuntime(
                                                new DatabaseInitializer(),
                                                new PrinterConfigurationStore(),
                                                new MonitoringRulesStore(),
                                                new SerialTransferSettingsStore(),
                                                new PrinterRegistry(),
                                                new PrinterRuntimeStateCache(),
                                                new PrinterMonitoringScheduler(new PrinterRegistry(),
                                                                new PrinterRuntimeStateCache()),
                                                null));

                assertEquals("apiServer must not be null", exception.getMessage());
        }

        @Test
        void startLoadsConfiguredPrintersAndStartsApi() throws Exception {
                Path dbFile = tempDir.resolve("runtime-test.db");
                System.setProperty("spaghettichef.databaseFile", dbFile.toString());

                DatabaseInitializer databaseInitializer = new DatabaseInitializer();
                databaseInitializer.initialize();

                PrinterConfigurationStore configurationStore = new PrinterConfigurationStore();
                MonitoringRulesStore monitoringRulesStore = new MonitoringRulesStore();
                configurationStore.save(
                                PrinterRuntimeNodeFactory.create(
                                                "printer-1",
                                                "Printer 1",
                                                "SIM_PORT",
                                                "sim",
                                                false));

                PrinterRegistry printerRegistry = new PrinterRegistry();
                PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();
                PrinterMonitoringScheduler monitoringScheduler = new PrinterMonitoringScheduler(
                                printerRegistry,
                                stateCache);

                int port = findFreePort();
                PrinterEventStore printerEventStore = new PrinterEventStore();
                PrintFileStore printFileStore = new PrintFileStore();
                PrintFileService printFileService = new PrintFileService(printFileStore);
                PrinterSdFileService printerSdFileService = new PrinterSdFileService(
                                new PrinterSdFileStore(),
                                printFileStore);
                PrintJobStore printJobStore = new PrintJobStore();

                PrintJobService printJobService = new PrintJobService(
                                printJobStore,
                                printerEventStore);

                PrinterActionGuard printerActionGuard = new PrinterActionGuard();
                SdCardService sdCardService = new SdCardService(printerEventStore);
                SerialTransferSettingsStore serialTransferSettingsStore = new SerialTransferSettingsStore();

                SdCardUploadService sdCardUploadService = new SdCardUploadService(
                                printerRegistry,
                                monitoringScheduler,
                                printerActionGuard,
                                printFileService,
                                sdCardService,
                                printerSdFileService,
                                printerEventStore, monitoringRulesStore, serialTransferSettingsStore);

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
                                port,
                                printerRegistry,
                                stateCache,
                                monitoringScheduler,
                                configurationStore,
                                monitoringRulesStore,
                                new PrintFileSettingsStore(),
                                serialTransferSettingsStore,
                                printerEventStore,
                                new PrinterCommandService(printerEventStore),
                                new SdCardService(printerEventStore),
                                sdCardUploadService,
                                printFileService,
                                printerSdFileService,
                                printJobService,
                                asyncPrintJobExecutor,
                                new PrintJobExecutionStepStore());

                SpaghettiChefRuntime runtime = new SpaghettiChefRuntime(
                                databaseInitializer,
                                configurationStore,
                                monitoringRulesStore,
                                serialTransferSettingsStore,
                                printerRegistry,
                                stateCache,
                                monitoringScheduler,
                                apiServer);

                try {
                        runtime.start();

                        assertTrue(runtime.printerRegistry().findById("printer-1").isPresent());
                        assertSame(printerRegistry, runtime.printerRegistry());
                        assertSame(stateCache, runtime.stateCache());

                        HttpResponse<String> response = httpGet("http://localhost:" + port + "/health");
                        assertEquals(200, response.statusCode());
                        assertEquals("{\"status\":\"ok\"}", response.body());

                        assertTrue(runtime.stateCache().findByPrinterId("printer-1").isPresent());
                } finally {
                        runtime.close();
                }
        }

        @Test
        void closeCanBeCalledAfterStartWithoutFailure() throws Exception {
                Path dbFile = tempDir.resolve("runtime-close.db");
                System.setProperty("spaghettichef.databaseFile", dbFile.toString());

                DatabaseInitializer databaseInitializer = new DatabaseInitializer();
                PrinterConfigurationStore configurationStore = new PrinterConfigurationStore();
                MonitoringRulesStore monitoringRulesStore = new MonitoringRulesStore();
                PrinterRegistry printerRegistry = new PrinterRegistry();
                PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();
                PrinterMonitoringScheduler monitoringScheduler = new PrinterMonitoringScheduler(
                                printerRegistry,
                                stateCache);

                int port = findFreePort();
                PrinterEventStore printerEventStore = new PrinterEventStore();
                PrintFileStore printFileStore = new PrintFileStore();
                PrintFileService printFileService = new PrintFileService(printFileStore);
                PrinterSdFileService printerSdFileService = new PrinterSdFileService(
                                new PrinterSdFileStore(),
                                printFileStore);
                PrintJobStore printJobStore = new PrintJobStore();

                PrintJobService printJobService = new PrintJobService(
                                printJobStore,
                                printerEventStore);

                PrinterActionGuard printerActionGuard = new PrinterActionGuard();
                SdCardService sdCardService = new SdCardService(printerEventStore);
                SerialTransferSettingsStore serialTransferSettingsStore = new SerialTransferSettingsStore();

                SdCardUploadService sdCardUploadService = new SdCardUploadService(
                                printerRegistry,
                                monitoringScheduler,
                                printerActionGuard,
                                printFileService,
                                sdCardService,
                                printerSdFileService,
                                printerEventStore, monitoringRulesStore, serialTransferSettingsStore);

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
                                port,
                                printerRegistry,
                                stateCache,
                                monitoringScheduler,
                                configurationStore,
                                monitoringRulesStore,
                                new PrintFileSettingsStore(),
                                serialTransferSettingsStore,
                                printerEventStore,
                                new PrinterCommandService(printerEventStore),
                                new SdCardService(printerEventStore),
                                sdCardUploadService,
                                printFileService,
                                printerSdFileService,
                                printJobService,
                                asyncPrintJobExecutor,
                                new PrintJobExecutionStepStore());

                SpaghettiChefRuntime runtime = new SpaghettiChefRuntime(
                                databaseInitializer,
                                configurationStore,
                                monitoringRulesStore,
                                serialTransferSettingsStore,
                                printerRegistry,
                                stateCache,
                                monitoringScheduler,
                                apiServer);

                runtime.start();

                assertDoesNotThrow(runtime::close);
        }

        private RemoteApiServer createApiServer(
                        PrinterRegistry printerRegistry,
                        PrinterRuntimeStateCache stateCache,
                        PrinterMonitoringScheduler monitoringScheduler) {
                PrinterEventStore printerEventStore = new PrinterEventStore();
                PrintFileStore printFileStore = new PrintFileStore();
                PrintFileService printFileService = new PrintFileService(printFileStore);
                PrinterSdFileService printerSdFileService = new PrinterSdFileService(
                                new PrinterSdFileStore(),
                                printFileStore);
                PrintJobStore printJobStore = new PrintJobStore();

                PrintJobService printJobService = new PrintJobService(
                                printJobStore,
                                printerEventStore);

                PrinterActionGuard printerActionGuard = new PrinterActionGuard();
                SdCardService sdCardService = new SdCardService(printerEventStore);
                SerialTransferSettingsStore serialTransferSettingsStore = new SerialTransferSettingsStore();
                MonitoringRulesStore monitoringRulesStore = new MonitoringRulesStore();

                SdCardUploadService sdCardUploadService = new SdCardUploadService(
                                printerRegistry,
                                monitoringScheduler,
                                printerActionGuard,
                                printFileService,
                                sdCardService,
                                printerSdFileService,
                                printerEventStore, monitoringRulesStore, serialTransferSettingsStore);

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

                return new RemoteApiServer(
                                findFreePortUnchecked(),
                                printerRegistry,
                                stateCache,
                                monitoringScheduler,
                                new PrinterConfigurationStore(),
                                new MonitoringRulesStore(),
                                new PrintFileSettingsStore(),
                                serialTransferSettingsStore,
                                printerEventStore,
                                new PrinterCommandService(printerEventStore),
                                new SdCardService(printerEventStore),
                                sdCardUploadService,
                                printFileService,
                                printerSdFileService,
                                printJobService,
                                asyncPrintJobExecutor,
                                new PrintJobExecutionStepStore());
        }

        private HttpResponse<String> httpGet(String url) throws Exception {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(url))
                                .GET()
                                .build();

                return client.send(request, HttpResponse.BodyHandlers.ofString());
        }

        private int findFreePort() throws IOException {
                try (ServerSocket socket = new ServerSocket(0)) {
                        return socket.getLocalPort();
                }
        }

        private int findFreePortUnchecked() {
                try {
                        return findFreePort();
                } catch (IOException exception) {
                        throw new IllegalStateException("Failed to allocate free test port", exception);
                }
        }
}
