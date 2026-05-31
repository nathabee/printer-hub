package spaghettichef.central.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import spaghettichef.central.persistence.CentralDatabase;
import spaghettichef.central.persistence.CentralDatabaseConfig;
import spaghettichef.central.persistence.CentralDatabaseInitializer;
import spaghettichef.central.persistence.CentralFarmStore;
import spaghettichef.central.service.CentralFarmService;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;

import static org.junit.jupiter.api.Assertions.*;

class CentralApiServerTest {
    @TempDir
    Path tempDir;

    @AfterEach
    void clearProperties() {
        System.clearProperty(CentralDatabaseConfig.CENTRAL_DATABASE_FILE_PROPERTY);
        System.clearProperty("spaghettichef.databaseFile");
    }

    @Test
    void firstFarmRegistrationCreatesFarm() throws Exception {
        TestContext context = createContext("register.db");
        try {
            HttpResponse<String> response = register(context, "runtime-1", "Home Farm");

            assertEquals(201, response.statusCode());
            assertTrue(response.body().contains("\"farmId\":\"farm-"));
            assertTrue(response.body().contains("\"farmSecret\":"));
            assertTrue(response.body().contains("\"runtimeInstanceId\":\"runtime-1\""));
        } finally {
            context.close();
        }
    }

    @Test
    void registrationTokenIsRequiredWhenConfigured() throws Exception {
        TestContext context = createContext("register-token.db", "secret-token");
        try {
            HttpResponse<String> missing = context.request("POST", "/api/central/farms/register", "{"
                    + "\"runtimeInstanceId\":\"runtime-1\","
                    + "\"farmName\":\"Home Farm\""
                    + "}");

            assertEquals(403, missing.statusCode());
            assertTrue(missing.body().contains("registration_token_mismatch"));

            HttpResponse<String> accepted = context.request(
                    "POST",
                    "/api/central/farms/register",
                    "{"
                            + "\"runtimeInstanceId\":\"runtime-1\","
                            + "\"farmName\":\"Home Farm\""
                            + "}",
                    CentralApiServer.REGISTRATION_TOKEN_HEADER,
                    "secret-token");

            assertEquals(201, accepted.statusCode());
            assertTrue(accepted.body().contains("\"farmId\":\"farm-"));
        } finally {
            context.close();
        }
    }

    @Test
    void registrationTokenCanBeSentAsBearerToken() throws Exception {
        TestContext context = createContext("register-bearer-token.db", "secret-token");
        try {
            HttpResponse<String> response = context.request(
                    "POST",
                    "/api/central/farms/register",
                    "{"
                            + "\"runtimeInstanceId\":\"runtime-1\","
                            + "\"farmName\":\"Home Farm\""
                            + "}",
                    "Authorization",
                    "Bearer secret-token");

            assertEquals(201, response.statusCode());
            assertTrue(response.body().contains("\"farmId\":\"farm-"));
        } finally {
            context.close();
        }
    }

    @Test
    void repeatedRegistrationBySameRuntimeInstanceIdDoesNotCreateDuplicate() throws Exception {
        TestContext context = createContext("register-repeat.db");
        try {
            String firstFarmId = extractJsonString(register(context, "runtime-1", "Home Farm").body(), "farmId");
            String secondFarmId = extractJsonString(register(context, "runtime-1", "Home Farm Renamed").body(), "farmId");

            assertEquals(firstFarmId, secondFarmId);
            assertEquals(1, countFarms());
        } finally {
            context.close();
        }
    }

    @Test
    void heartbeatUpdatesLastSeenAtAndSummaryCounters() throws Exception {
        TestContext context = createContext("heartbeat.db");
        try {
            Registration registration = registration(context);

            HttpResponse<String> response = heartbeat(context, registration, "runtime-1", """
                    "printerCount":3,
                    "cameraCount":2,
                    "activePrintCount":1,
                    "warningCount":4,
                    "errorCount":5,
                    "spaghettiAlertCount":6,
                    "message":"ok"
                    """);

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"lastSeenAt\":\""));
            assertTrue(response.body().contains("\"printerCount\":3"));
            assertTrue(response.body().contains("\"cameraCount\":2"));
            assertTrue(response.body().contains("\"activePrintCount\":1"));
            assertTrue(response.body().contains("\"spaghettiAlertCount\":6"));
        } finally {
            context.close();
        }
    }

    @Test
    void unknownFarmHeartbeatIsRejected() throws Exception {
        TestContext context = createContext("heartbeat-unknown.db");
        try {
            HttpResponse<String> response = context.request("POST", "/api/central/farms/farm-missing/heartbeat", """
                    {"runtimeInstanceId":"runtime-1","farmSecret":"secret"}
                    """);

            assertEquals(403, response.statusCode());
            assertTrue(response.body().contains("unknown_farm"));
        } finally {
            context.close();
        }
    }

    @Test
    void mismatchedRuntimeInstanceHeartbeatIsRejected() throws Exception {
        TestContext context = createContext("heartbeat-mismatch.db");
        try {
            Registration registration = registration(context);

            HttpResponse<String> response = heartbeat(context, registration, "runtime-other", "\"printerCount\":1");

            assertEquals(403, response.statusCode());
            assertTrue(response.body().contains("runtime_instance_mismatch"));
        } finally {
            context.close();
        }
    }

    @Test
    void disabledFarmHeartbeatIsRejected() throws Exception {
        TestContext context = createContext("heartbeat-disabled.db");
        try {
            Registration registration = registration(context);
            try (Connection connection = CentralDatabase.getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "UPDATE central_farm SET enabled = 0 WHERE farm_id = ?")) {
                statement.setString(1, registration.farmId());
                statement.executeUpdate();
            }

            HttpResponse<String> response = heartbeat(context, registration, "runtime-1", "\"printerCount\":1");

            assertEquals(403, response.statusCode());
            assertTrue(response.body().contains("farm_disabled"));
        } finally {
            context.close();
        }
    }

    @Test
    void overviewReturnsOnlineStaleOfflineState() throws Exception {
        TestContext context = createContext("overview.db");
        try {
            Registration online = registration(context, "runtime-online", "Online Farm");
            heartbeat(context, online, "runtime-online", "\"printerCount\":1");
            Registration stale = registration(context, "runtime-stale", "Stale Farm");
            heartbeat(context, stale, "runtime-stale", "\"printerCount\":1");
            Registration offline = registration(context, "runtime-offline", "Offline Farm");

            setLastSeen(stale.farmId(), "2020-01-01T00:07:00Z");
            setLastSeen(offline.farmId(), "2019-12-31T23:59:00Z");

            HttpResponse<String> response = context.get("/api/central/farms/overview");

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"status\":\"ONLINE\""));
            assertTrue(response.body().contains("\"status\":\"STALE\""));
            assertTrue(response.body().contains("\"status\":\"OFFLINE\""));
        } finally {
            context.close();
        }
    }

    @Test
    void getFarmReturnsOneFarmWithoutSecret() throws Exception {
        TestContext context = createContext("get-farm.db");
        try {
            Registration registration = registration(context);
            heartbeat(context, registration, "runtime-1", """
                    "printerCount":3,
                    "cameraCount":2,
                    "activePrintCount":1
                    """);

            HttpResponse<String> response = context.get("/api/central/farms/" + registration.farmId());

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"status\":\"ONLINE\""));
            assertTrue(response.body().contains("\"farmId\":\"" + registration.farmId() + "\""));
            assertTrue(response.body().contains("\"printerCount\":3"));
            assertFalse(response.body().contains("farmSecret"));
        } finally {
            context.close();
        }
    }

    @Test
    void getUnknownFarmIsRejected() throws Exception {
        TestContext context = createContext("get-farm-missing.db");
        try {
            HttpResponse<String> response = context.get("/api/central/farms/farm-missing");

            assertEquals(403, response.statusCode());
            assertTrue(response.body().contains("unknown_farm"));
        } finally {
            context.close();
        }
    }

    @Test
    void structureSnapshotCanBePushedAndReturned() throws Exception {
        TestContext context = createContext("structure.db");
        try {
            Registration registration = registration(context);

            HttpResponse<String> push = pushStructure(context, registration, "runtime-1", """
                    "generatedAt":"2026-05-31T10:30:00Z",
                    "printers":[{"printerId":"p1","displayName":"Ender 3","enabled":true,"status":"PRINTING"}],
                    "cameras":[{"cameraId":"cam1","displayName":"Front Camera","printerId":"p1","enabled":true}]
                    """);

            assertEquals(200, push.statusCode());
            assertTrue(push.body().contains("\"accepted\":true"));
            assertTrue(push.body().contains("\"structureUpdatedAt\":\""));

            HttpResponse<String> get = context.get("/api/central/farms/" + registration.farmId() + "/structure");

            assertEquals(200, get.statusCode());
            assertTrue(get.body().contains("\"farmId\":\"" + registration.farmId() + "\""));
            assertTrue(get.body().contains("\"printerId\":\"p1\""));
            assertTrue(get.body().contains("\"cameraId\":\"cam1\""));
            assertTrue(get.body().contains("\"structureUpdatedAt\":\""));
            assertFalse(get.body().contains("farmSecret"));
        } finally {
            context.close();
        }
    }

    @Test
    void structurePushRejectsUnknownFarm() throws Exception {
        TestContext context = createContext("structure-unknown.db");
        try {
            HttpResponse<String> response = context.request("POST", "/api/central/farms/farm-missing/structure", "{"
                    + "\"runtimeInstanceId\":\"runtime-1\","
                    + "\"farmSecret\":\"secret\","
                    + "\"printers\":[],"
                    + "\"cameras\":[]"
                    + "}");

            assertEquals(403, response.statusCode());
            assertTrue(response.body().contains("unknown_farm"));
        } finally {
            context.close();
        }
    }

    @Test
    void structurePushRejectsMismatchedRuntimeInstanceId() throws Exception {
        TestContext context = createContext("structure-mismatch.db");
        try {
            Registration registration = registration(context);

            HttpResponse<String> response = pushStructure(context, registration, "runtime-other",
                    "\"printers\":[],\"cameras\":[]");

            assertEquals(403, response.statusCode());
            assertTrue(response.body().contains("runtime_instance_mismatch"));
        } finally {
            context.close();
        }
    }

    @Test
    void structurePushRejectsDisabledFarm() throws Exception {
        TestContext context = createContext("structure-disabled.db");
        try {
            Registration registration = registration(context);
            try (Connection connection = CentralDatabase.getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "UPDATE central_farm SET enabled = 0 WHERE farm_id = ?")) {
                statement.setString(1, registration.farmId());
                statement.executeUpdate();
            }

            HttpResponse<String> response = pushStructure(context, registration, "runtime-1",
                    "\"printers\":[],\"cameras\":[]");

            assertEquals(403, response.statusCode());
            assertTrue(response.body().contains("farm_disabled"));
        } finally {
            context.close();
        }
    }

    @Test
    void structurePushRejectsUnsafeFields() throws Exception {
        TestContext context = createContext("structure-unsafe.db");
        try {
            Registration registration = registration(context);

            HttpResponse<String> response = pushStructure(context, registration, "runtime-1", """
                    "printers":[{"printerId":"p1","displayName":"Ender","serialPort":"/dev/ttyUSB0"}],
                    "cameras":[]
                    """);

            assertEquals(400, response.statusCode());
            assertTrue(response.body().contains("forbidden field serialport"));
        } finally {
            context.close();
        }
    }

    @Test
    void localDatabasePropertyDoesNotConfigureCentralDatabase() throws Exception {
        Path localDb = tempDir.resolve("local.db");
        Path centralDb = tempDir.resolve("central.db");
        System.setProperty("spaghettichef.databaseFile", localDb.toString());
        System.setProperty(CentralDatabaseConfig.CENTRAL_DATABASE_FILE_PROPERTY, centralDb.toString());

        new CentralDatabaseInitializer().initialize();

        assertFalse(java.nio.file.Files.exists(localDb));
        assertTrue(java.nio.file.Files.exists(centralDb));
    }

    @Test
    void centralDashboardIsReadOnlyAndListsFarms() throws Exception {
        TestContext context = createContext("dashboard.db");
        try {
            register(context, "runtime-1", "Home Farm");

            HttpResponse<String> response = context.get("/central-dashboard");

            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("SpaghettiChef Central"));
            assertTrue(response.body().contains("/central-dashboard/favicon.svg"));
            assertFalse(response.body().toLowerCase(java.util.Locale.ROOT).contains("start print"));
            assertFalse(response.body().toLowerCase(java.util.Locale.ROOT).contains("emergency stop"));
        } finally {
            context.close();
        }
    }

    @Test
    void centralDashboardFaviconReusesLocalDashboardIcon() throws Exception {
        TestContext context = createContext("dashboard-favicon.db");
        try {
            HttpResponse<String> response = context.get("/central-dashboard/favicon.svg");

            assertEquals(200, response.statusCode());
            assertTrue(response.headers().firstValue("content-type").orElse("").contains("image/svg+xml"));
            assertTrue(response.body().contains("<svg"));
        } finally {
            context.close();
        }
    }

    private TestContext createContext(String dbName) throws Exception {
        return createContext(dbName, null);
    }

    private TestContext createContext(String dbName, String registrationToken) throws Exception {
        System.setProperty(CentralDatabaseConfig.CENTRAL_DATABASE_FILE_PROPERTY, tempDir.resolve(dbName).toString());
        new CentralDatabaseInitializer().initialize();
        int port = findFreePort();
        CentralApiServer server = new CentralApiServer(
                port,
                new CentralFarmService(new CentralFarmStore(), java.time.Clock.fixed(
                        java.time.Instant.parse("2020-01-01T00:10:00Z"),
                        java.time.ZoneOffset.UTC)),
                registrationToken);
        server.start();
        return new TestContext(port, server);
    }

    private HttpResponse<String> register(TestContext context, String runtimeInstanceId, String farmName)
            throws Exception {
        return context.request("POST", "/api/central/farms/register", "{"
                + "\"runtimeInstanceId\":\"" + runtimeInstanceId + "\","
                + "\"farmName\":\"" + farmName + "\","
                + "\"runtimeVersion\":\"1.0.0\","
                + "\"hostname\":\"private-host\""
                + "}");
    }

    private Registration registration(TestContext context) throws Exception {
        return registration(context, "runtime-1", "Home Farm");
    }

    private Registration registration(TestContext context, String runtimeInstanceId, String farmName) throws Exception {
        String body = register(context, runtimeInstanceId, farmName).body();
        return new Registration(extractJsonString(body, "farmId"), extractJsonString(body, "farmSecret"));
    }

    private HttpResponse<String> heartbeat(
            TestContext context,
            Registration registration,
            String runtimeInstanceId,
            String fields) throws Exception {
        return context.request("POST", "/api/central/farms/" + registration.farmId() + "/heartbeat", "{"
                + "\"runtimeInstanceId\":\"" + runtimeInstanceId + "\","
                + "\"farmSecret\":\"" + registration.farmSecret() + "\","
                + "\"runtimeVersion\":\"1.0.0\","
                + fields
                + "}");
    }

    private HttpResponse<String> pushStructure(
            TestContext context,
            Registration registration,
            String runtimeInstanceId,
            String fields) throws Exception {
        return context.request("POST", "/api/central/farms/" + registration.farmId() + "/structure", "{"
                + "\"runtimeInstanceId\":\"" + runtimeInstanceId + "\","
                + "\"farmSecret\":\"" + registration.farmSecret() + "\","
                + fields
                + "}");
    }

    private int countFarms() throws Exception {
        try (Connection connection = CentralDatabase.getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM central_farm");
                java.sql.ResultSet resultSet = statement.executeQuery()) {
            assertTrue(resultSet.next());
            return resultSet.getInt(1);
        }
    }

    private void setLastSeen(String farmId, String instant) throws Exception {
        try (Connection connection = CentralDatabase.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE central_farm SET last_seen_at = ? WHERE farm_id = ?")) {
            statement.setString(1, instant);
            statement.setString(2, farmId);
            statement.executeUpdate();
        }
    }

    private int findFreePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private String extractJsonString(String body, String fieldName) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                "\"" + java.util.regex.Pattern.quote(fieldName) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(body);
        return matcher.find() ? matcher.group(1) : null;
    }

    private record Registration(String farmId, String farmSecret) {
    }

    private static final class TestContext {
        private final int port;
        private final CentralApiServer server;
        private final HttpClient httpClient = HttpClient.newHttpClient();

        private TestContext(int port, CentralApiServer server) {
            this.port = port;
            this.server = server;
        }

        private HttpResponse<String> get(String path) throws Exception {
            return request("GET", path, null);
        }

        private HttpResponse<String> request(String method, String path, String body) throws Exception {
            return request(method, path, body, null, null);
        }

        private HttpResponse<String> request(
                String method,
                String path,
                String body,
                String headerName,
                String headerValue) throws Exception {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + path));
            if (body == null) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                builder.method(method, HttpRequest.BodyPublishers.ofString(body))
                        .header("Content-Type", "application/json");
            }
            if (headerName != null && headerValue != null) {
                builder.header(headerName, headerValue);
            }
            return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }

        private void close() {
            server.stop();
        }
    }
}
