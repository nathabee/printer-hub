package printerhub;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import printerhub.persistence.DatabaseInitializer;

import java.nio.file.Files;
import java.nio.file.Path;


class RemoteApiServerTest {

    private static final Pattern UPDATED_AT_PATTERN =
            Pattern.compile("\"updatedAt\"\\s*:\\s*\"([^\"]+)\"");

    private RemoteApiServer apiServer;
    private Path databaseFile;


    @BeforeEach
    void setUpDatabase() throws IOException {
        databaseFile = Files.createTempFile("printerhub-remote-api-test-", ".db");
        System.setProperty("printerhub.databaseFile", databaseFile.toString());

        DatabaseInitializer.initialize();
    }

    @AfterEach
    void cleanup() throws IOException {
        if (apiServer != null) {
            apiServer.stop();
        }

        Set<String> keys = System.getProperties().stringPropertyNames();
        for (String key : keys) {
            if (key.startsWith("printerhub.sim.")
                    || key.equals("printerhub.initDelayMs")
                    || key.equals("printerhub.databaseFile")) {
                System.clearProperty(key);
            }
        }

        if (databaseFile != null) {
            Files.deleteIfExists(databaseFile);
        }
    }

    @Test
    void health_get_returnsUp() throws Exception {
        int port = freePort();
        startApi(port);

        HttpResponse<String> response = send("GET", url(port, "/health"));

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"status\": \"UP\""));
        assertTrue(response.body().contains("\"service\": \"PrinterHub\""));
    }

    @Test
    void printerStatus_get_returnsSnapshotJson() throws Exception {
        int port = freePort();
        startApi(port);

        waitForStatusContaining(port, "\"state\"");

        HttpResponse<String> response = send("GET", url(port, "/printer/status"));

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"state\""));
        assertTrue(response.body().contains("\"hotendTemperature\""));
        assertTrue(response.body().contains("\"bedTemperature\""));
        assertTrue(response.body().contains("\"updatedAt\""));
    }
 
    @Test
    void printerPoll_post_returnsCurrentSnapshot() throws Exception {
        int port = freePort();
        startApi(port, 1000);

        waitForStatusContaining(port, "ok T:21.80");

        HttpResponse<String> response = send("POST", url(port, "/printer/poll"));

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"state\""));
        assertTrue(response.body().contains("\"lastResponse\""));
    }

    @Test
    void invalidMethod_returns405() throws Exception {
        int port = freePort();
        startApi(port);

        HttpResponse<String> response = send("POST", url(port, "/health"));

        assertEquals(405, response.statusCode());
        assertTrue(response.body().contains("Method not allowed"));
    }

    @Test
    void backgroundPolling_refreshesStatusAutomatically() throws Exception {
        int port = freePort();
        startApi(port, 50);

        String firstBody = waitForStatusContaining(port, "\"updatedAt\"");
        String firstUpdatedAt = extractUpdatedAt(firstBody);

        String refreshedBody = waitForUpdatedAtChange(port, firstUpdatedAt);
        String refreshedUpdatedAt = extractUpdatedAt(refreshedBody);

        assertTrue(refreshedBody.contains("\"state\""));
        assertTrue(refreshedBody.contains("\"lastResponse\""));
        assertTrue(refreshedBody.contains("ok T:21.80"));
        assertTrue(!firstUpdatedAt.equals(refreshedUpdatedAt));
    }

    private void startApi(int port) throws IOException {
        startApi(port, 1000);
    }

    private void startApi(int port, long pollDelayMs) throws IOException {
        System.setProperty(
                "printerhub.sim.response.M105",
                "ok T:21.80 /0.00 B:21.52 /0.00 @:0 B@:0"
        );

        apiServer = new RemoteApiServer(
                port,
                "SIM_PORT",
                "sim",
                115200,
                1,
                pollDelayMs
        );

        apiServer.start();
    }

    private HttpResponse<String> send(String method, String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();

        return HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String waitForStatusContaining(int port, String expectedText) throws Exception {
        long deadline = System.currentTimeMillis() + 3000;

        while (System.currentTimeMillis() < deadline) {
            HttpResponse<String> response = send("GET", url(port, "/printer/status"));

            if (response.statusCode() == 200 && response.body().contains(expectedText)) {
                return response.body();
            }

            Thread.sleep(50);
        }

        throw new AssertionError("Status did not contain expected text: " + expectedText);
    }

    private String waitForUpdatedAtChange(int port, String previousUpdatedAt) throws Exception {
        long deadline = System.currentTimeMillis() + 3000;

        while (System.currentTimeMillis() < deadline) {
            HttpResponse<String> response = send("GET", url(port, "/printer/status"));
            String updatedAt = extractUpdatedAt(response.body());

            if (!previousUpdatedAt.equals(updatedAt)) {
                return response.body();
            }

            Thread.sleep(50);
        }

        throw new AssertionError("updatedAt did not change during background polling");
    }

    private String extractUpdatedAt(String body) {
        Matcher matcher = UPDATED_AT_PATTERN.matcher(body);

        if (!matcher.find()) {
            throw new AssertionError("No updatedAt field found in body: " + body);
        }

        return matcher.group(1);
    }

    private String url(int port, String path) {
        return "http://localhost:" + port + path;
    }

    private int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @Test
    void jobs_get_initiallyReturnsEmptyList() throws Exception {
        int port = freePort();
        startApi(port);

        HttpResponse<String> response = send("GET", url(port, "/jobs"));

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"jobs\""));
        assertTrue(response.body().contains("["));
        assertTrue(response.body().contains("]"));
    }

    @Test
    void jobs_post_createsSimulatedJob() throws Exception {
        int port = freePort();
        startApi(port);

        HttpResponse<String> response = send("POST", url(port, "/jobs"));

        assertEquals(201, response.statusCode());
        assertTrue(response.body().contains("\"id\""));
        assertTrue(response.body().contains("\"name\": \"simulated-job\""));
        assertTrue(response.body().contains("\"type\": \"SIMULATED\""));
        assertTrue(response.body().contains("\"state\": \"CREATED\""));
    }

    @Test
    void jobs_getAfterPost_returnsCreatedJob() throws Exception {
        int port = freePort();
        startApi(port);

        send("POST", url(port, "/jobs"));

        HttpResponse<String> response = send("GET", url(port, "/jobs"));

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"jobs\""));
        assertTrue(response.body().contains("\"name\": \"simulated-job\""));
        assertTrue(response.body().contains("\"state\": \"CREATED\""));
    }

    @Test
    void jobById_get_returnsCreatedJob() throws Exception {
        int port = freePort();
        startApi(port);

        HttpResponse<String> created = send("POST", url(port, "/jobs"));
        String id = extractJsonString(created.body(), "id");

        HttpResponse<String> response = send("GET", url(port, "/jobs/" + id));

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"id\": \"" + id + "\""));
        assertTrue(response.body().contains("\"name\": \"simulated-job\""));
    }

    @Test
    void jobById_getUnknown_returns404() throws Exception {
        int port = freePort();
        startApi(port);

        HttpResponse<String> response = send("GET", url(port, "/jobs/unknown-job"));

        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains("Job not found"));
    }

    @Test
    void jobs_invalidMethod_returns405() throws Exception {
        int port = freePort();
        startApi(port);

        HttpResponse<String> response = send("PUT", url(port, "/jobs"));

        assertEquals(405, response.statusCode());
        assertTrue(response.body().contains("Method not allowed"));
    }

    private String extractJsonString(String body, String fieldName) {
        Pattern pattern = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(body);

        if (!matcher.find()) {
            throw new AssertionError(
                    "No JSON string field found: " + fieldName + " in body: " + body
            );
        }

        return matcher.group(1);
    }


    @Test
    void printers_get_returnsConfiguredPrinterOnly() throws Exception {
        int port = freePort();
        startApi(port);

        HttpResponse<String> response = send("GET", url(port, "/printers"));

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"printers\""));
        assertTrue(response.body().contains("\"id\": \"printer-1\""));
        assertTrue(response.body().contains("\"name\": \"Primary printer\""));
        assertTrue(response.body().contains("\"portName\": \"SIM_PORT\""));

        assertTrue(!response.body().contains("\"id\": \"printer-2\""));
        assertTrue(!response.body().contains("\"id\": \"printer-3\""));
    }

    @Test
    void printerStatusById_get_returnsPrinterStatus() throws Exception {
        int port = freePort();
        startApi(port);

        HttpResponse<String> response = send("GET", url(port, "/printers/printer-1/status"));

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"id\": \"printer-1\""));
        assertTrue(response.body().contains("\"state\""));
        assertTrue(response.body().contains("\"updatedAt\""));
    }

    @Test
    void printerPollById_post_returnsPrinterStatus() throws Exception {
        int port = freePort();
        startApi(port, 1000);

        HttpResponse<String> response = send("POST", url(port, "/printers/printer-1/poll"));

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"id\": \"printer-1\""));
        assertTrue(response.body().contains("\"state\""));
    }

    @Test
    void printerJobAssignment_post_createsAssignedJob() throws Exception {
        int port = freePort();
        startApi(port);

        HttpResponse<String> response = send("POST", url(port, "/printers/printer-1/jobs"));

        assertEquals(201, response.statusCode());
        assertTrue(response.body().contains("\"type\": \"SIMULATED\""));
        assertTrue(response.body().contains("\"state\": \"ASSIGNED\""));
        assertTrue(response.body().contains("\"assignedPrinterId\": \"printer-1\""));
    }

    @Test
    void printerStatusAfterJobAssignment_containsAssignedJobId() throws Exception {
        int port = freePort();
        startApi(port);

        HttpResponse<String> created = send("POST", url(port, "/printers/printer-1/jobs"));
        String jobId = extractJsonString(created.body(), "id");

        HttpResponse<String> response = send("GET", url(port, "/printers/printer-1/status"));

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"assignedJobId\": \"" + jobId + "\""));
    }

    @Test
    void printerById_unknown_returns404() throws Exception {
        int port = freePort();
        startApi(port);

        HttpResponse<String> response = send("GET", url(port, "/printers/missing/status"));

        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains("Printer not found"));
    }

    @Test
    void printerById_invalidMethod_returns405() throws Exception {
        int port = freePort();
        startApi(port);

        HttpResponse<String> response = send("GET", url(port, "/printers/printer-1/poll"));

        assertEquals(405, response.statusCode());
        assertTrue(response.body().contains("Method not allowed"));
    }

    @Test
    void dashboard_get_returnsHtml() throws Exception {
        int port = freePort();
        startApi(port);

        HttpResponse<String> response = send("GET", url(port, "/dashboard"));

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("PrinterHub Dashboard"));
        assertTrue(response.body().contains("/dashboard/dashboard.css"));
        assertTrue(response.body().contains("/dashboard/dashboard.js"));
    }

    @Test
    void dashboardCss_get_returnsCss() throws Exception {
        int port = freePort();
        startApi(port);

        HttpResponse<String> response = send("GET", url(port, "/dashboard/dashboard.css"));

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains(".printer-grid"));
    }

    @Test
    void dashboardJs_get_returnsJavaScript() throws Exception {
        int port = freePort();
        startApi(port);

        HttpResponse<String> response = send("GET", url(port, "/dashboard/dashboard.js"));

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("fetch(\"/printers\")"));
    }

    @Test
    void backgroundExecution_movesAssignedJobToCompleted() throws Exception {
        int port = freePort();
        startApi(port, 50);

        HttpResponse<String> created =
                send("POST", url(port, "/printers/printer-1/jobs"));

        String jobId = extractJsonString(created.body(), "id");

        String jobBody = waitForJobContaining(port, jobId, "\"state\": \"COMPLETED\"");

        assertTrue(jobBody.contains("\"id\": \"" + jobId + "\""));
        assertTrue(jobBody.contains("\"state\": \"COMPLETED\""));
    }


    private String waitForJobContaining(int port, String jobId, String expectedText) throws Exception {
    long deadline = System.currentTimeMillis() + 3000;

    while (System.currentTimeMillis() < deadline) {
        HttpResponse<String> response = send("GET", url(port, "/jobs/" + jobId));

        if (response.statusCode() == 200 && response.body().contains(expectedText)) {
            return response.body();
        }

        Thread.sleep(50);
    }

    throw new AssertionError("Job did not contain expected text: " + expectedText);
}
}