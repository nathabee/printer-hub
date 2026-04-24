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

class RemoteApiServerTest {

    private static final Pattern UPDATED_AT_PATTERN =
            Pattern.compile("\"updatedAt\"\\s*:\\s*\"([^\"]+)\"");

    private RemoteApiServer apiServer;

    @AfterEach
    void cleanup() {
        if (apiServer != null) {
            apiServer.stop();
        }

        Set<String> keys = System.getProperties().stringPropertyNames();
        for (String key : keys) {
            if (key.startsWith("printerhub.sim.") || key.equals("printerhub.initDelayMs")) {
                System.clearProperty(key);
            }
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
}