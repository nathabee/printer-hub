package printerhub.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import printerhub.OperatorMessageReportWriter;
import printerhub.PrinterSnapshot;
import printerhub.PrinterState;
import printerhub.command.PrinterCommandService;
import printerhub.job.AsyncPrintJobExecutor;
import printerhub.job.PrintJobExecutionService;
import printerhub.job.PrintJobService;
import printerhub.job.PrinterActionGuard;
import printerhub.job.PrinterActionMapper;
import printerhub.monitoring.PrinterMonitoringScheduler;
import printerhub.persistence.DatabaseInitializer;
import printerhub.persistence.MonitoringRulesStore;
import printerhub.persistence.PrintJobStore;
import printerhub.persistence.PrinterConfigurationStore;
import printerhub.persistence.PrinterEventStore;
import printerhub.persistence.PrintJobExecutionStepStore;
import printerhub.runtime.PrinterRegistry;
import printerhub.runtime.PrinterRuntimeNodeFactory;
import printerhub.runtime.PrinterRuntimeStateCache;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class RemoteApiServerTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void clearDatabaseProperty() {
        System.clearProperty("printerhub.databaseFile");
    }

    @Test
    void dashboardApiJsReturnsJavaScript() throws Exception {
        TestContext context = createContext("dashboard-api-js.db");

        try {
            HttpResponse<String> response = context.get("/dashboard/api.js");

            assertEquals(200, response.statusCode());
            assertTrue(response.headers().firstValue("content-type").orElse("").contains("application/javascript"));
        } finally {
            context.close();
        }
    }

    @Test
    void dashboardViewModuleReturnsJavaScript() throws Exception {
        TestContext context = createContext("dashboard-view-module.db");

        try {
            HttpResponse<String> response = context.get("/dashboard/views/farm-home.js");

            assertEquals(200, response.statusCode());
            assertTrue(response.headers().firstValue("content-type").orElse("").contains("application/javascript"));
        } finally {
            context.close();
        }
    }

    @Test
    void dashboardComponentModuleReturnsJavaScript() throws Exception {
        TestContext context = createContext("dashboard-component-module.db");

        try {
            HttpResponse<String> response = context.get("/dashboard/components/nav.js");

            assertEquals(200, response.statusCode());
            assertTrue(response.headers().firstValue("content-type").orElse("").contains("application/javascript"));
        } finally {
            context.close();
        }
    }

    @Test
    void constructorFailsForInvalidPort() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> createApiServerForConstructorTest(0));

        assertEquals("port must be between 1 and 65535", exception.getMessage());
    }

    @Test
    void getHealthReturnsOk() throws Exception {
        TestContext context = createContext("health.db");

        try {
            HttpResponse<String> response = context.get("/health");

            assertEquals(200, response.statusCode());
            assertEquals("{\"status\":\"ok\"}", response.body());
        } finally {
            context.close();
        }
    }

    @Test
    void wrongMethodOnHealthReturns405() throws Exception {
        TestContext context = createContext("health-405.db");

        try {
            HttpResponse<String> response = context.request("POST", "/health", null);

            assertEquals(405, response.statusCode());
            assertEquals("{\"error\":\"method_not_allowed\"}", response.body());
        } finally {
            context.close();
        }
    }

    @Test
    void getPrintersReturnsEmptyListInitially() throws Exception {
        TestContext context = createContext("printers-empty.db");

        try {
            HttpResponse<String> response = context.get("/printers");

            assertEquals(200, response.statusCode());
            assertEquals("{\"printers\":[]}", response.body());
        } finally {
            context.close();
        }
    }

    @Test
    void getMonitoringSettingsReturnsDefaults() throws Exception {
        TestContext context = createContext("monitoring-settings-get.db");

        try {
            HttpResponse<String> response = context.get("/settings/monitoring");

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"pollIntervalSeconds\":"));
            assertTrue(response.body().contains("\"snapshotMinimumIntervalSeconds\":"));
            assertTrue(response.body().contains("\"temperatureDeltaThreshold\":"));
            assertTrue(response.body().contains("\"eventDeduplicationWindowSeconds\":"));
            assertTrue(response.body().contains("\"errorPersistenceBehavior\":\"DEDUPLICATED\""));
        } finally {
            context.close();
        }
    }

    @Test
    void putMonitoringSettingsUpdatesRules() throws Exception {
        TestContext context = createContext("monitoring-settings-put.db");

        try {
            HttpResponse<String> response = context.request(
                    "PUT",
                    "/settings/monitoring",
                    """
                            {"pollIntervalSeconds":12,"snapshotMinimumIntervalSeconds":45,"temperatureDeltaThreshold":2.5,"eventDeduplicationWindowSeconds":90,"errorPersistenceBehavior":"ALWAYS"}
                            """);

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"pollIntervalSeconds\":12"));
            assertTrue(response.body().contains("\"snapshotMinimumIntervalSeconds\":45"));
            assertTrue(response.body().contains("\"temperatureDeltaThreshold\":2.50"));
            assertTrue(response.body().contains("\"eventDeduplicationWindowSeconds\":90"));
            assertTrue(response.body().contains("\"errorPersistenceBehavior\":\"ALWAYS\""));
        } finally {
            context.close();
        }
    }

    @Test
    void postPrintersCreatesPrinter() throws Exception {
        TestContext context = createContext("printers-post.db");

        try {
            HttpResponse<String> response = context.request(
                    "POST",
                    "/printers",
                    """
                            {"id":"printer-1","displayName":"Printer 1","portName":"SIM_PORT","mode":"sim","enabled":true}
                            """);

            assertEquals(201, response.statusCode());
            assertTrue(response.body().contains("\"id\":\"printer-1\""));
            assertTrue(response.body().contains("\"displayName\":\"Printer 1\""));
            assertTrue(response.body().contains("\"portName\":\"SIM_PORT\""));
            assertTrue(response.body().contains("\"mode\":\"sim\""));
            assertTrue(response.body().contains("\"enabled\":true"));

            assertTrue(context.printerRegistry.findById("printer-1").isPresent());
        } finally {
            context.close();
        }
    }

    @Test
    void optionsPrintersAllowsDashboardPreflight() throws Exception {
        TestContext context = createContext("printers-options.db");

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + context.port + "/printers"))
                    .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                    .header("Origin", "http://localhost:5500")
                    .header("Access-Control-Request-Method", "POST")
                    .header("Access-Control-Request-Headers", "Content-Type")
                    .build();

            HttpResponse<String> response = context.httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(204, response.statusCode());
            assertEquals("*", response.headers().firstValue("access-control-allow-origin").orElse(""));
            assertTrue(response.headers()
                    .firstValue("access-control-allow-methods")
                    .orElse("")
                    .contains("POST"));
            assertTrue(response.headers()
                    .firstValue("access-control-allow-headers")
                    .orElse("")
                    .contains("Content-Type"));
        } finally {
            context.close();
        }
    }

    @Test
    void postPrintersWithMissingRequiredFieldReturns400() throws Exception {
        TestContext context = createContext("printers-post-400.db");

        try {
            HttpResponse<String> response = context.request(
                    "POST",
                    "/printers",
                    """
                            {"id":"printer-1","portName":"SIM_PORT","mode":"sim"}
                            """);

            assertEquals(400, response.statusCode());
            assertEquals("{\"error\":\"displayName must not be blank\"}", response.body());
        } finally {
            context.close();
        }
    }

    @Test
    void postPrinterCommandExecutesAllowedReadCommand() throws Exception {
        TestContext context = createContext("printer-command-m105.db");

        try {
            context.configurationStore.save(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));
            context.printerRegistry.register(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));

            HttpResponse<String> response = context.request(
                    "POST",
                    "/printers/printer-1/commands",
                    """
                            {"command":"M105"}
                            """);

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"printerId\":\"printer-1\""));
            assertTrue(response.body().contains("\"command\":\"M105\""));
            assertTrue(response.body().contains("\"sentCommand\":\"M105\""));
            assertTrue(response.body().contains("\"response\":\"ok T:21.80 /0.00 B:21.52 /0.00 @:0 B@:0\""));
        } finally {
            context.close();
        }
    }

    @Test
    void postPrinterCommandExecutesTemperatureCommandWithParameter() throws Exception {
        TestContext context = createContext("printer-command-m104.db");

        try {
            context.configurationStore.save(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));
            context.printerRegistry.register(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));

            HttpResponse<String> response = context.request(
                    "POST",
                    "/printers/printer-1/commands",
                    """
                            {"command":"M104","targetTemperature":200}
                            """);

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"command\":\"M104\""));
            assertTrue(response.body().contains("\"sentCommand\":\"M104 S200\""));
        } finally {
            context.close();
        }
    }

    @Test
    void postPrinterCommandFailsForMissingTemperatureParameter() throws Exception {
        TestContext context = createContext("printer-command-m104-400.db");

        try {
            context.configurationStore.save(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));
            context.printerRegistry.register(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));

            HttpResponse<String> response = context.request(
                    "POST",
                    "/printers/printer-1/commands",
                    """
                            {"command":"M104"}
                            """);

            assertEquals(400, response.statusCode());
            assertEquals("{\"error\":\"targetTemperature is required for command M104\"}", response.body());
        } finally {
            context.close();
        }
    }

    @Test
    void postPrinterCommandFailsForInvalidCommand() throws Exception {
        TestContext context = createContext("printer-command-invalid.db");

        try {
            context.configurationStore.save(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));
            context.printerRegistry.register(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));

            HttpResponse<String> response = context.request(
                    "POST",
                    "/printers/printer-1/commands",
                    """
                            {"command":"G0"}
                            """);

            assertEquals(400, response.statusCode());
            assertEquals("{\"error\":\"Invalid printer command: G0\"}", response.body());
        } finally {
            context.close();
        }
    }

    @Test
    void getPrinterEventsReturnsPersistedCommandEvents() throws Exception {
        TestContext context = createContext("printer-events.db");

        try {
            context.configurationStore.save(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));
            context.printerRegistry.register(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));

            HttpResponse<String> commandResponse = context.request(
                    "POST",
                    "/printers/printer-1/commands",
                    """
                            {"command":"M114"}
                            """);

            assertEquals(200, commandResponse.statusCode());

            HttpResponse<String> eventsResponse = context.get("/printers/printer-1/events");

            assertEquals(200, eventsResponse.statusCode());
            assertTrue(eventsResponse.body().contains("\"events\":["));
            assertTrue(eventsResponse.body().contains("\"eventType\":\"COMMAND_EXECUTED\""));
            assertTrue(eventsResponse.body().contains("Manual command executed: M114"));
        } finally {
            context.close();
        }
    }

    @Test
    void getPrinterByIdReturnsPrinter() throws Exception {
        TestContext context = createContext("printer-get.db");

        try {
            context.configurationStore.save(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", false));
            context.printerRegistry.register(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", false));
            context.stateCache.update(
                    "printer-1",
                    PrinterSnapshot.disconnected(Instant.parse("2026-04-29T10:00:00Z")));

            HttpResponse<String> response = context.get("/printers/printer-1");

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"id\":\"printer-1\""));
            assertTrue(response.body().contains("\"displayName\":\"Printer 1\""));
        } finally {
            context.close();
        }
    }

    @Test
    void getPrinterByIdReturns404WhenMissing() throws Exception {
        TestContext context = createContext("printer-get-404.db");

        try {
            HttpResponse<String> response = context.get("/printers/missing");

            assertEquals(404, response.statusCode());
            assertEquals("{\"error\":\"printer_not_found\"}", response.body());
        } finally {
            context.close();
        }
    }

    @Test
    void getPrinterStatusReturnsUnknownWhenNoSnapshotExists() throws Exception {
        TestContext context = createContext("printer-status-unknown.db");

        try {
            context.configurationStore.save(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", false));
            context.printerRegistry.register(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", false));

            HttpResponse<String> response = context.get("/printers/printer-1/status");

            assertEquals(200, response.statusCode());
            assertEquals(
                    "{\"state\":\"UNKNOWN\",\"hotendTemperature\":null,\"bedTemperature\":null,"
                            + "\"lastResponse\":null,\"errorMessage\":null,\"updatedAt\":null}",
                    response.body());
        } finally {
            context.close();
        }
    }

    @Test
    void getPrinterStatusReturnsSnapshot() throws Exception {
        TestContext context = createContext("printer-status.db");

        try {
            context.configurationStore.save(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", false));
            context.printerRegistry.register(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", false));
            context.stateCache.update(
                    "printer-1",
                    PrinterSnapshot.error(
                            PrinterState.ERROR,
                            55.0,
                            25.0,
                            "Error: heater",
                            "Heater failure",
                            Instant.parse("2026-04-29T10:01:00Z")));

            HttpResponse<String> response = context.get("/printers/printer-1/status");

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"state\":\"ERROR\""));
            assertTrue(response.body().contains("\"hotendTemperature\":55.00"));
            assertTrue(response.body().contains("\"bedTemperature\":25.00"));
            assertTrue(response.body().contains("\"errorMessage\":\"Heater failure\""));
        } finally {
            context.close();
        }
    }

    @Test
    void putPrinterUpdatesPrinter() throws Exception {
        TestContext context = createContext("printer-put.db");

        try {
            context.configurationStore.save(
                    PrinterRuntimeNodeFactory.create("printer-1", "Old Printer", "SIM_PORT", "sim", true));
            context.printerRegistry.register(
                    PrinterRuntimeNodeFactory.create("printer-1", "Old Printer", "SIM_PORT", "sim", true));

            HttpResponse<String> response = context.request(
                    "PUT",
                    "/printers/printer-1",
                    """
                            {"displayName":"Updated Printer","portName":"SIM_PORT_2","mode":"sim-error","enabled":false}
                            """);

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"displayName\":\"Updated Printer\""));
            assertTrue(response.body().contains("\"portName\":\"SIM_PORT_2\""));
            assertTrue(response.body().contains("\"mode\":\"sim-error\""));
            assertTrue(response.body().contains("\"enabled\":false"));

            assertEquals("Updated Printer", context.printerRegistry.findById("printer-1").orElseThrow().displayName());
        } finally {
            context.close();
        }
    }

    @Test
    void putPrinterReturns404WhenMissing() throws Exception {
        TestContext context = createContext("printer-put-404.db");

        try {
            HttpResponse<String> response = context.request(
                    "PUT",
                    "/printers/missing",
                    """
                            {"displayName":"Updated Printer","portName":"SIM_PORT_2","mode":"sim","enabled":true}
                            """);

            assertEquals(404, response.statusCode());
            assertEquals("{\"error\":\"printer_not_found\"}", response.body());
        } finally {
            context.close();
        }
    }

    @Test
    void deletePrinterRemovesPrinter() throws Exception {
        TestContext context = createContext("printer-delete.db");

        try {
            context.configurationStore.save(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", false));
            context.printerRegistry.register(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", false));
            context.stateCache.update(
                    "printer-1",
                    PrinterSnapshot.disconnected(Instant.parse("2026-04-29T10:00:00Z")));

            HttpResponse<String> response = context.request("DELETE", "/printers/printer-1", null);

            assertEquals(200, response.statusCode());
            assertEquals("{\"deleted\":\"printer-1\"}", response.body());
            assertTrue(context.printerRegistry.findById("printer-1").isEmpty());
            assertTrue(context.stateCache.findByPrinterId("printer-1").isEmpty());
        } finally {
            context.close();
        }
    }

    @Test
    void deletePrinterReturns404WhenMissing() throws Exception {
        TestContext context = createContext("printer-delete-404.db");

        try {
            HttpResponse<String> response = context.request("DELETE", "/printers/missing", null);

            assertEquals(404, response.statusCode());
            assertEquals("{\"error\":\"printer_not_found\"}", response.body());
        } finally {
            context.close();
        }
    }

    @Test
    void enablePrinterSetsEnabledTrue() throws Exception {
        TestContext context = createContext("printer-enable.db");

        try {
            context.configurationStore.save(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", false));
            context.printerRegistry.register(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", false));

            HttpResponse<String> response = context.request("POST", "/printers/printer-1/enable", null);

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"enabled\":true"));
            assertTrue(context.printerRegistry.findById("printer-1").orElseThrow().enabled());
        } finally {
            context.close();
        }
    }

    @Test
    void disablePrinterSetsEnabledFalse() throws Exception {
        TestContext context = createContext("printer-disable.db");

        try {
            context.configurationStore.save(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));
            context.printerRegistry.register(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));

            HttpResponse<String> response = context.request("POST", "/printers/printer-1/disable", null);

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"enabled\":false"));
            assertFalse(context.printerRegistry.findById("printer-1").orElseThrow().enabled());
        } finally {
            context.close();
        }
    }

    @Test
    void wrongMethodOnPrinterEnableReturns405() throws Exception {
        TestContext context = createContext("printer-enable-405.db");

        try {
            context.configurationStore.save(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", false));
            context.printerRegistry.register(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", false));

            HttpResponse<String> response = context.request("GET", "/printers/printer-1/enable", null);

            assertEquals(405, response.statusCode());
            assertEquals("{\"error\":\"method_not_allowed\"}", response.body());
        } finally {
            context.close();
        }
    }

    @Test
    void unknownPrinterEndpointReturns404() throws Exception {
        TestContext context = createContext("printer-endpoint-404.db");

        try {
            HttpResponse<String> response = context.get("/printers/printer-1/unknown");

            assertEquals(404, response.statusCode());
            assertEquals("{\"error\":\"printer_endpoint_not_found\"}", response.body());
        } finally {
            context.close();
        }
    }

    @Test
    void dashboardReturnsHtml() throws Exception {
        TestContext context = createContext("dashboard.db");

        try {
            HttpResponse<String> response = context.get("/dashboard");

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("<!DOCTYPE html>") || response.body().contains("<html"));
        } finally {
            context.close();
        }
    }

    @Test
    void dashboardCssReturnsCss() throws Exception {
        TestContext context = createContext("dashboard-css.db");

        try {
            HttpResponse<String> response = context.get("/dashboard/dashboard.css");

            assertEquals(200, response.statusCode());
            assertTrue(response.headers().firstValue("content-type").orElse("").contains("text/css"));
        } finally {
            context.close();
        }
    }

    @Test
    void dashboardJsReturnsJavaScript() throws Exception {
        TestContext context = createContext("dashboard-js.db");

        try {
            HttpResponse<String> response = context.get("/dashboard/dashboard.js");

            assertEquals(200, response.statusCode());
            assertTrue(response.headers().firstValue("content-type").orElse("").contains("application/javascript"));
        } finally {
            context.close();
        }
    }

    @Test
    void dashboardWrongMethodReturns405() throws Exception {
        TestContext context = createContext("dashboard-405.db");

        try {
            HttpResponse<String> response = context.request("POST", "/dashboard", null);

            assertEquals(405, response.statusCode());
            assertEquals("{\"error\":\"method_not_allowed\"}", response.body());
        } finally {
            context.close();
        }
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        TestContext context = createContext("printers-malformed-json.db");

        try {
            HttpResponse<String> response = context.request(
                    "POST",
                    "/printers",
                    """
                            {"id":"printer-1","displayName":"Printer 1","portName":"SIM_PORT","mode":"sim
                            """);

            assertEquals(400, response.statusCode());
            assertTrue(response.body().contains("\"error\":"));
        } finally {
            context.close();
        }
    }

    @Test
    void persistenceFailureReturnsControlled500() throws Exception {
        Path invalidDatabasePath = tempDir.resolve("not-a-db-dir");
        assertDoesNotThrow(() -> java.nio.file.Files.createDirectories(invalidDatabasePath));
        System.setProperty("printerhub.databaseFile", invalidDatabasePath.toString());

        PrinterRegistry printerRegistry = new PrinterRegistry();
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();
        PrinterConfigurationStore configurationStore = new PrinterConfigurationStore();
        MonitoringRulesStore monitoringRulesStore = new MonitoringRulesStore();
        PrinterEventStore printerEventStore = new PrinterEventStore();
        PrinterMonitoringScheduler monitoringScheduler = new PrinterMonitoringScheduler(
                printerRegistry,
                stateCache);

        int port = findFreePort();

        PrintJobStore printJobStore = new PrintJobStore();

        PrintJobService printJobService = new PrintJobService(
                printJobStore,
                printerEventStore);

        PrinterActionGuard printerActionGuard = new PrinterActionGuard();

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

        RemoteApiServer server = new RemoteApiServer(
                port,
                printerRegistry,
                stateCache,
                monitoringScheduler,
                configurationStore,
                monitoringRulesStore,
                printerEventStore,
                new PrinterCommandService(printerEventStore),
                printJobService,
                asyncPrintJobExecutor,
                new PrintJobExecutionStepStore());

        server.start();

        TestContext context = new TestContext(
                port,
                server,
                monitoringScheduler,
                printerRegistry,
                stateCache,
                configurationStore);

        try {
            HttpResponse<String> response = context.request(
                    "POST",
                    "/printers",
                    """
                            {"id":"printer-1","displayName":"Printer 1","portName":"SIM_PORT","mode":"sim","enabled":true}
                            """);

            assertEquals(500, response.statusCode());
            assertTrue(response.body().contains("\"error\":"));
            assertTrue(context.printerRegistry.findById("printer-1").isEmpty());
        } finally {
            context.close();
        }
    }

    @Test
    void postJobsCreatesAssignedJob() throws Exception {
        TestContext context = createContext("jobs-post.db");

        try {
            HttpResponse<String> response = context.request(
                    "POST",
                    "/jobs",
                    """
                            {"name":"Home axes","type":"HOME_AXES","printerId":"printer-1"}
                            """);

            assertEquals(201, response.statusCode());
            assertTrue(response.body().contains("\"name\":\"Home axes\""));
            assertTrue(response.body().contains("\"type\":\"HOME_AXES\""));
            assertTrue(response.body().contains("\"state\":\"ASSIGNED\""));
            assertTrue(response.body().contains("\"printerId\":\"printer-1\""));
        } finally {
            context.close();
        }
    }

    @Test
    void getJobsReturnsCreatedJobs() throws Exception {
        TestContext context = createContext("jobs-get.db");

        try {
            HttpResponse<String> createResponse = context.request(
                    "POST",
                    "/jobs",
                    """
                            {"name":"Read firmware","type":"READ_FIRMWARE_INFO","printerId":"printer-1"}
                            """);

            assertEquals(201, createResponse.statusCode());

            HttpResponse<String> response = context.get("/jobs");

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"jobs\":["));
            assertTrue(response.body().contains("\"name\":\"Read firmware\""));
            assertTrue(response.body().contains("\"type\":\"READ_FIRMWARE_INFO\""));
        } finally {
            context.close();
        }
    }

    @Test
    void getJobByIdReturnsJob() throws Exception {
        TestContext context = createContext("job-get.db");

        try {
            HttpResponse<String> createResponse = context.request(
                    "POST",
                    "/jobs",
                    """
                            {"name":"Read temperature","type":"READ_TEMPERATURE","printerId":"printer-1"}
                            """);

            assertEquals(201, createResponse.statusCode());
            String jobId = extractJsonString(createResponse.body(), "id");
            assertNotNull(jobId);

            HttpResponse<String> response = context.get("/jobs/" + jobId);

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"id\":\"" + jobId + "\""));
            assertTrue(response.body().contains("\"name\":\"Read temperature\""));
        } finally {
            context.close();
        }
    }

    @Test
    void postJobStartStartsAssignedJobAsynchronously() throws Exception {
        TestContext context = createContext("job-start.db");

        try {
            HttpResponse<String> createResponse = context.request(
                    "POST",
                    "/jobs",
                    """
                            {"name":"Read firmware","type":"READ_FIRMWARE_INFO","printerId":"printer-1"}
                            """);

            assertEquals(201, createResponse.statusCode());
            String jobId = extractJsonString(createResponse.body(), "id");
            assertNotNull(jobId);

            context.printerRegistry.register(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));

            HttpResponse<String> response = context.request(
                    "POST",
                    "/jobs/" + jobId + "/start",
                    null);

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"success\":true"));
            assertTrue(response.body().contains("\"accepted\":true"));
            assertTrue(response.body().contains("\"outcome\":\"QUEUED\""));
            assertTrue(response.body().contains("\"state\":\"RUNNING\""));

            String completedJob = waitForJobState(context, jobId, "COMPLETED");
            assertTrue(completedJob.contains("\"state\":\"COMPLETED\""));
        } finally {
            context.close();
        }
    }

    @Test
    void postJobCancelCancelsJob() throws Exception {
        TestContext context = createContext("job-cancel.db");

        try {
            HttpResponse<String> createResponse = context.request(
                    "POST",
                    "/jobs",
                    """
                            {"name":"Fan off","type":"TURN_FAN_OFF","printerId":"printer-1"}
                            """);

            assertEquals(201, createResponse.statusCode());
            String jobId = extractJsonString(createResponse.body(), "id");
            assertNotNull(jobId);

            HttpResponse<String> response = context.request(
                    "POST",
                    "/jobs/" + jobId + "/cancel",
                    null);

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"id\":\"" + jobId + "\""));
            assertTrue(response.body().contains("\"state\":\"CANCELLED\""));
        } finally {
            context.close();
        }
    }

    @Test
    void deleteJobRemovesJob() throws Exception {
        TestContext context = createContext("job-delete.db");

        try {
            HttpResponse<String> createResponse = context.request(
                    "POST",
                    "/jobs",
                    """
                            {"name":"Read position","type":"READ_POSITION","printerId":"printer-1"}
                            """);

            assertEquals(201, createResponse.statusCode());
            String jobId = extractJsonString(createResponse.body(), "id");
            assertNotNull(jobId);

            HttpResponse<String> deleteResponse = context.request(
                    "DELETE",
                    "/jobs/" + jobId,
                    null);

            assertEquals(200, deleteResponse.statusCode());
            assertEquals("{\"deleted\":\"" + jobId + "\"}", deleteResponse.body());

            HttpResponse<String> getResponse = context.get("/jobs/" + jobId);
            assertEquals(404, getResponse.statusCode());
            assertEquals("{\"error\":\"job_not_found\"}", getResponse.body());
        } finally {
            context.close();
        }
    }

    @Test
    void deleteJobReturns404WhenMissing() throws Exception {
        TestContext context = createContext("job-delete-404.db");

        try {
            HttpResponse<String> response = context.request(
                    "DELETE",
                    "/jobs/missing-job",
                    null);

            assertEquals(404, response.statusCode());
            assertEquals("{\"error\":\"job_not_found\"}", response.body());
        } finally {
            context.close();
        }
    }

    @Test
    void getMissingJobReturns404() throws Exception {
        TestContext context = createContext("job-get-404.db");

        try {
            HttpResponse<String> response = context.get("/jobs/missing-job");

            assertEquals(404, response.statusCode());
            assertEquals("{\"error\":\"job_not_found\"}", response.body());
        } finally {
            context.close();
        }
    }

    @Test
    void writesOperatorMessageReportScenario() throws Exception {
        OperatorMessageReportWriter.appendScenario(
                "remote api verification summary",
                "Remote API unit verification covered health, printer CRUD, monitoring settings, command execution, event reads, dashboard access, and controlled error handling.",
                "[PrinterHub] API server started on port ...\n"
                        + "[PrinterHub] API server stopped\n"
                        + "[PrinterHub] API operation failed: Failed to save printer configuration",
                "GET /health -> 200\n"
                        + "GET /printers -> 200\n"
                        + "GET /settings/monitoring -> 200\n"
                        + "PUT /settings/monitoring -> 200\n"
                        + "POST /printers -> 201\n"
                        + "POST /printers/{id}/commands -> 200\n"
                        + "GET /printers/{id}/events -> 200\n"
                        + "PUT /printers/{id} -> 200\n"
                        + "DELETE /printers/{id} -> 200\n"
                        + "DELETE /jobs/{id} -> 200\n"
                        + "invalid POST -> 400\n"
                        + "unknown printer -> 404\n"
                        + "wrong method -> 405\n"
                        + "persistence failure -> 500",
                "Printer configuration persistence was exercised through create/update/delete flows.\n"
                        + "Monitoring settings persistence was exercised through GET/PUT flows.\n"
                        + "Manual command execution and event retrieval were exercised through dedicated endpoints.\n"
                        + "Controlled persistence failure path was also verified.");

        assertTrue(java.nio.file.Files.exists(java.nio.file.Path.of("target", "operator-message-report.md")));
    }

    @Test
    void getJobExecutionStepsReturnsStructuredExecutionDiagnostics() throws Exception {
        TestContext context = createContext("job-execution-steps-get.db");

        try {
            HttpResponse<String> createResponse = context.request(
                    "POST",
                    "/jobs",
                    """
                            {"name":"Home axes","type":"HOME_AXES","printerId":"printer-1"}
                            """);

            assertEquals(201, createResponse.statusCode());
            String jobId = extractJsonString(createResponse.body(), "id");
            assertNotNull(jobId);

            context.printerRegistry.register(
                    PrinterRuntimeNodeFactory.create("printer-1", "Printer 1", "SIM_PORT", "sim", true));

            HttpResponse<String> startResponse = context.request(
                    "POST",
                    "/jobs/" + jobId + "/start",
                    null);

            assertEquals(200, startResponse.statusCode());
            waitForJobState(context, jobId, "COMPLETED");

            HttpResponse<String> response = context.get("/jobs/" + jobId + "/execution-steps");

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"executionSteps\":["));
            assertTrue(response.body().contains("\"jobId\":\"" + jobId + "\""));
            assertTrue(response.body().contains("\"stepIndex\":0"));
            assertTrue(response.body().contains("\"stepName\":\"validate-position-before-home\""));
            assertTrue(response.body().contains("\"wireCommand\":\"M114\""));
            assertTrue(response.body().contains("\"outcome\":\"SUCCESS\""));
            assertTrue(response.body().contains("\"success\":true"));
            assertTrue(response.body().contains("\"stepIndex\":1"));
            assertTrue(response.body().contains("\"stepName\":\"home-axes\""));
            assertTrue(response.body().contains("\"wireCommand\":\"G28\""));
        } finally {
            context.close();
        }
    }

    @Test
    void getJobExecutionStepsReturns404WhenJobIsMissing() throws Exception {
        TestContext context = createContext("job-execution-steps-404.db");

        try {
            HttpResponse<String> response = context.get("/jobs/missing-job/execution-steps");

            assertEquals(404, response.statusCode());
            assertEquals("{\"error\":\"job_not_found\"}", response.body());
        } finally {
            context.close();
        }
    }

    private TestContext createContext(String dbName) throws Exception {
        Path dbFile = tempDir.resolve(dbName);
        System.setProperty("printerhub.databaseFile", dbFile.toString());
        new DatabaseInitializer().initialize();

        PrinterRegistry printerRegistry = new PrinterRegistry();
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();
        PrinterConfigurationStore configurationStore = new PrinterConfigurationStore();
        MonitoringRulesStore monitoringRulesStore = new MonitoringRulesStore();
        PrinterEventStore printerEventStore = new PrinterEventStore();
        PrintJobStore printJobStore = new PrintJobStore();

        PrinterMonitoringScheduler monitoringScheduler = new PrinterMonitoringScheduler(
                printerRegistry,
                stateCache);

        PrintJobService printJobService = new PrintJobService(
                printJobStore,
                printerEventStore);

        PrinterActionGuard printerActionGuard = new PrinterActionGuard();

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

        int port = findFreePort();

        RemoteApiServer server = new RemoteApiServer(
                port,
                printerRegistry,
                stateCache,
                monitoringScheduler,
                configurationStore,
                monitoringRulesStore,
                printerEventStore,
                new PrinterCommandService(printerEventStore),
                printJobService,
                asyncPrintJobExecutor,
                new PrintJobExecutionStepStore());
        server.start();

        return new TestContext(
                port,
                server,
                monitoringScheduler,
                printerRegistry,
                stateCache,
                configurationStore);
    }

    private int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private String extractJsonString(String body, String fieldName) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "\"" + java.util.regex.Pattern.quote(fieldName) + "\"\\s*:\\s*\"([^\"]*)\"");
        java.util.regex.Matcher matcher = pattern.matcher(body);

        if (!matcher.find()) {
            return null;
        }

        return matcher.group(1);
    }

    private String waitForJobState(TestContext context, String jobId, String expectedState) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000L;
        String body = "";

        while (System.currentTimeMillis() < deadline) {
            HttpResponse<String> response = context.get("/jobs/" + jobId);
            body = response.body();

            if (body.contains("\"state\":\"" + expectedState + "\"")) {
                return body;
            }

            Thread.sleep(50L);
        }

        fail("Timed out waiting for job " + jobId + " to reach state " + expectedState + ". Last body: " + body);
        return body;
    }

    private RemoteApiServer createApiServerForConstructorTest(int port) {
        PrinterRegistry printerRegistry = new PrinterRegistry();
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();
        PrinterMonitoringScheduler monitoringScheduler = new PrinterMonitoringScheduler(
                printerRegistry,
                stateCache);
        PrinterConfigurationStore configurationStore = new PrinterConfigurationStore();
        MonitoringRulesStore monitoringRulesStore = new MonitoringRulesStore();
        PrinterEventStore printerEventStore = new PrinterEventStore();
        PrintJobStore printJobStore = new PrintJobStore();

        PrintJobService printJobService = new PrintJobService(
                printJobStore,
                printerEventStore);

        PrinterActionGuard printerActionGuard = new PrinterActionGuard();

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
                port,
                printerRegistry,
                stateCache,
                monitoringScheduler,
                configurationStore,
                monitoringRulesStore,
                printerEventStore,
                new PrinterCommandService(printerEventStore),
                printJobService,
                asyncPrintJobExecutor,
                new PrintJobExecutionStepStore());
    }

    private static final class TestContext {
        private final int port;
        private final RemoteApiServer server;
        private final PrinterMonitoringScheduler monitoringScheduler;
        private final PrinterRegistry printerRegistry;
        private final PrinterRuntimeStateCache stateCache;
        private final PrinterConfigurationStore configurationStore;
        private final HttpClient httpClient = HttpClient.newHttpClient();

        private TestContext(
                int port,
                RemoteApiServer server,
                PrinterMonitoringScheduler monitoringScheduler,
                PrinterRegistry printerRegistry,
                PrinterRuntimeStateCache stateCache,
                PrinterConfigurationStore configurationStore) {
            this.port = port;
            this.server = server;
            this.monitoringScheduler = monitoringScheduler;
            this.printerRegistry = printerRegistry;
            this.stateCache = stateCache;
            this.configurationStore = configurationStore;
        }

        private HttpResponse<String> get(String path) throws Exception {
            return request("GET", path, null);
        }

        private HttpResponse<String> request(String method, String path, String body) throws Exception {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + path));

            if (body == null) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                builder.method(method, HttpRequest.BodyPublishers.ofString(body))
                        .header("Content-Type", "application/json");
            }

            return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }

        private void close() {
            try {
                monitoringScheduler.stop();
            } finally {
                server.stop();
            }
        }
    }
}
