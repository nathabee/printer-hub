package printerhub.runtime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import printerhub.api.RemoteApiServer;
import printerhub.monitoring.PrinterMonitoringScheduler;
import printerhub.persistence.DatabaseInitializer;
import printerhub.persistence.PrinterConfigurationStore;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PrinterHubRuntimeTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void clearDatabaseProperty() {
        System.clearProperty("printerhub.databaseFile");
    }

    @Test
    void constructorFailsWhenDatabaseInitializerIsNull() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new PrinterHubRuntime(
                        null,
                        new PrinterConfigurationStore(),
                        new PrinterRegistry(),
                        new PrinterRuntimeStateCache(),
                        new PrinterMonitoringScheduler(new PrinterRegistry(), new PrinterRuntimeStateCache(), 60),
                        createApiServer(new PrinterRegistry(), new PrinterRuntimeStateCache(), new PrinterMonitoringScheduler(new PrinterRegistry(), new PrinterRuntimeStateCache(), 60))
                )
        );

        assertEquals("databaseInitializer must not be null", exception.getMessage());
    }

    @Test
    void constructorFailsWhenPrinterConfigurationStoreIsNull() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new PrinterHubRuntime(
                        new DatabaseInitializer(),
                        null,
                        new PrinterRegistry(),
                        new PrinterRuntimeStateCache(),
                        new PrinterMonitoringScheduler(new PrinterRegistry(), new PrinterRuntimeStateCache(), 60),
                        createApiServer(new PrinterRegistry(), new PrinterRuntimeStateCache(), new PrinterMonitoringScheduler(new PrinterRegistry(), new PrinterRuntimeStateCache(), 60))
                )
        );

        assertEquals("printerConfigurationStore must not be null", exception.getMessage());
    }

    @Test
    void constructorFailsWhenPrinterRegistryIsNull() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new PrinterHubRuntime(
                        new DatabaseInitializer(),
                        new PrinterConfigurationStore(),
                        null,
                        new PrinterRuntimeStateCache(),
                        new PrinterMonitoringScheduler(new PrinterRegistry(), new PrinterRuntimeStateCache(), 60),
                        createApiServer(new PrinterRegistry(), new PrinterRuntimeStateCache(), new PrinterMonitoringScheduler(new PrinterRegistry(), new PrinterRuntimeStateCache(), 60))
                )
        );

        assertEquals("printerRegistry must not be null", exception.getMessage());
    }

    @Test
    void constructorFailsWhenStateCacheIsNull() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new PrinterHubRuntime(
                        new DatabaseInitializer(),
                        new PrinterConfigurationStore(),
                        new PrinterRegistry(),
                        null,
                        new PrinterMonitoringScheduler(new PrinterRegistry(), new PrinterRuntimeStateCache(), 60),
                        createApiServer(new PrinterRegistry(), new PrinterRuntimeStateCache(), new PrinterMonitoringScheduler(new PrinterRegistry(), new PrinterRuntimeStateCache(), 60))
                )
        );

        assertEquals("stateCache must not be null", exception.getMessage());
    }

    @Test
    void constructorFailsWhenMonitoringSchedulerIsNull() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new PrinterHubRuntime(
                        new DatabaseInitializer(),
                        new PrinterConfigurationStore(),
                        new PrinterRegistry(),
                        new PrinterRuntimeStateCache(),
                        null,
                        createApiServer(new PrinterRegistry(), new PrinterRuntimeStateCache(), new PrinterMonitoringScheduler(new PrinterRegistry(), new PrinterRuntimeStateCache(), 60))
                )
        );

        assertEquals("monitoringScheduler must not be null", exception.getMessage());
    }

    @Test
    void constructorFailsWhenApiServerIsNull() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new PrinterHubRuntime(
                        new DatabaseInitializer(),
                        new PrinterConfigurationStore(),
                        new PrinterRegistry(),
                        new PrinterRuntimeStateCache(),
                        new PrinterMonitoringScheduler(new PrinterRegistry(), new PrinterRuntimeStateCache(), 60),
                        null
                )
        );

        assertEquals("apiServer must not be null", exception.getMessage());
    }

    @Test
    void startLoadsConfiguredPrintersAndStartsApi() throws Exception {
        Path dbFile = tempDir.resolve("runtime-test.db");
        System.setProperty("printerhub.databaseFile", dbFile.toString());

        DatabaseInitializer databaseInitializer = new DatabaseInitializer();
        databaseInitializer.initialize();

        PrinterConfigurationStore configurationStore = new PrinterConfigurationStore();
        configurationStore.save(
                PrinterRuntimeNodeFactory.create(
                        "printer-1",
                        "Printer 1",
                        "SIM_PORT",
                        "sim",
                        false
                )
        );

        PrinterRegistry printerRegistry = new PrinterRegistry();
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();
        PrinterMonitoringScheduler monitoringScheduler = new PrinterMonitoringScheduler(
                printerRegistry,
                stateCache,
                60
        );

        int port = findFreePort();
        RemoteApiServer apiServer = new RemoteApiServer(
                port,
                printerRegistry,
                stateCache,
                monitoringScheduler,
                configurationStore
        );

        PrinterHubRuntime runtime = new PrinterHubRuntime(
                databaseInitializer,
                configurationStore,
                printerRegistry,
                stateCache,
                monitoringScheduler,
                apiServer
        );

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
        System.setProperty("printerhub.databaseFile", dbFile.toString());

        DatabaseInitializer databaseInitializer = new DatabaseInitializer();
        PrinterConfigurationStore configurationStore = new PrinterConfigurationStore();
        PrinterRegistry printerRegistry = new PrinterRegistry();
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();
        PrinterMonitoringScheduler monitoringScheduler = new PrinterMonitoringScheduler(
                printerRegistry,
                stateCache,
                60
        );

        int port = findFreePort();
        RemoteApiServer apiServer = new RemoteApiServer(
                port,
                printerRegistry,
                stateCache,
                monitoringScheduler,
                configurationStore
        );

        PrinterHubRuntime runtime = new PrinterHubRuntime(
                databaseInitializer,
                configurationStore,
                printerRegistry,
                stateCache,
                monitoringScheduler,
                apiServer
        );

        runtime.start();

        assertDoesNotThrow(runtime::close);
    }

    private RemoteApiServer createApiServer(
            PrinterRegistry printerRegistry,
            PrinterRuntimeStateCache stateCache,
            PrinterMonitoringScheduler monitoringScheduler
    ) {
        return new RemoteApiServer(
                findFreePortUnchecked(),
                printerRegistry,
                stateCache,
                monitoringScheduler,
                new PrinterConfigurationStore()
        );
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