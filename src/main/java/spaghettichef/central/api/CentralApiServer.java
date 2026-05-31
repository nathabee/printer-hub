package spaghettichef.central.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import spaghettichef.shared.AppVersion;
import spaghettichef.shared.OperationMessages;
import spaghettichef.shared.SpaghettiChefLog;
import spaghettichef.central.service.CentralFarm;
import spaghettichef.central.service.CentralFarmOverview;
import spaghettichef.central.service.CentralFarmService;
import spaghettichef.central.service.FarmHeartbeatRequest;
import spaghettichef.central.service.FarmRegistrationRequest;
import spaghettichef.shared.config.RuntimeDefaults;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CentralApiServer {
    private final int port;
    private final CentralFarmService farmService;
    private HttpServer server;

    public CentralApiServer(int port, CentralFarmService farmService) {
        if (port < RuntimeDefaults.MIN_PORT || port > RuntimeDefaults.MAX_PORT) {
            throw new IllegalArgumentException(OperationMessages.PORT_MUST_BE_IN_VALID_RANGE);
        }
        this.port = port;
        this.farmService = farmService;
    }

    public void start() {
        if (server != null) {
            return;
        }
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.setExecutor(Executors.newFixedThreadPool(RuntimeDefaults.DEFAULT_API_THREAD_POOL_SIZE));
            server.createContext("/health", exchange -> safeHandle(exchange, this::handleHealth));
            server.createContext("/version", exchange -> safeHandle(exchange, this::handleVersion));
            server.createContext("/api/central/farms", exchange -> safeHandle(exchange, this::handleFarms));
            server.createContext("/central-dashboard", exchange -> safeHandle(exchange, this::handleDashboard));
            server.start();
            SpaghettiChefLog.info(OperationMessages.apiServerStarted(port));
        } catch (IOException exception) {
            throw new IllegalStateException(OperationMessages.failedToStartApiServer(port), exception);
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
            return;
        }
        sendJson(exchange, 200, "{\"status\":\"ok\",\"mode\":\"central\"}");
    }

    private void handleVersion(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
            return;
        }
        sendJson(exchange, 200, "{\"version\":\"" + escapeJson(AppVersion.current()) + "\",\"mode\":\"central\"}");
    }

    private void handleFarms(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if ("/api/central/farms/register".equals(path)) {
            handleRegister(exchange);
            return;
        }
        if ("/api/central/farms/overview".equals(path)) {
            handleOverview(exchange);
            return;
        }

        Matcher heartbeat = Pattern.compile("^/api/central/farms/([^/]+)/heartbeat$").matcher(path);
        if (heartbeat.matches()) {
            handleHeartbeat(exchange, heartbeat.group(1));
            return;
        }

        if ("/api/central/farms".equals(path) || "/api/central/farms/".equals(path)) {
            handleOverview(exchange);
            return;
        }

        sendJson(exchange, 404, errorJson(OperationMessages.resourceNotFound(path)));
    }

    private void handleRegister(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
            return;
        }
        String body = readBody(exchange);
        CentralFarm farm = farmService.register(new FarmRegistrationRequest(
                requiredJsonString(body, "runtimeInstanceId"),
                requiredJsonString(body, "farmName"),
                optionalJsonString(body, "runtimeVersion", null),
                optionalJsonString(body, "hostname", null),
                optionalJsonString(body, "displayLocation", null),
                optionalJsonString(body, "description", null),
                objectJson(body, "metadata")));
        sendJson(exchange, 201, "{\"farm\":" + farmJson(farm, true) + "}");
    }

    private void handleHeartbeat(HttpExchange exchange, String farmId) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
            return;
        }
        String body = readBody(exchange);
        CentralFarm farm = farmService.heartbeat(new FarmHeartbeatRequest(
                farmId,
                requiredJsonString(body, "runtimeInstanceId"),
                requiredJsonString(body, "farmSecret"),
                optionalJsonString(body, "runtimeVersion", null),
                optionalJsonString(body, "status", null),
                optionalJsonInteger(body, "printerCount", 0),
                optionalJsonInteger(body, "cameraCount", 0),
                optionalJsonInteger(body, "activePrintCount", 0),
                optionalJsonInteger(body, "warningCount", 0),
                optionalJsonInteger(body, "errorCount", 0),
                optionalJsonInteger(body, "spaghettiAlertCount", 0),
                optionalJsonString(body, "message", null)));
        sendJson(exchange, 200, "{\"accepted\":true,\"farm\":" + farmJson(farm, false) + "}");
    }

    private void handleOverview(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
            return;
        }
        List<CentralFarmOverview> farms = farmService.overview();
        StringBuilder json = new StringBuilder("{\"farms\":[");
        for (int index = 0; index < farms.size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            CentralFarmOverview overview = farms.get(index);
            json.append("{\"status\":\"").append(escapeJson(overview.status())).append("\",\"farm\":")
                    .append(farmJson(overview.farm(), false)).append('}');
        }
        json.append("]}");
        sendJson(exchange, 200, json.toString());
    }

    private void handleDashboard(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
            return;
        }

        String path = exchange.getRequestURI().getPath();

        if ("/central-dashboard".equals(path) || "/central-dashboard/".equals(path)) {
            sendResource(exchange, "central-dashboard/index.html", "text/html; charset=utf-8");
            return;
        }
        if ("/central-dashboard/favicon.svg".equals(path)) {
            sendResource(exchange, "dashboard/favicon.svg", "image/svg+xml");
            return;
        }
        if ("/central-dashboard/central-dashboard.css".equals(path)) {
            sendResource(exchange, "central-dashboard/central-dashboard.css", "text/css; charset=utf-8");
            return;
        }
        if ("/central-dashboard/central-dashboard.js".equals(path)) {
            sendResource(exchange, "central-dashboard/central-dashboard.js", "application/javascript; charset=utf-8");
            return;
        }

        sendJson(exchange, 404, errorJson(OperationMessages.resourceNotFound(path)));
    }

    private void safeHandle(HttpExchange exchange, ExchangeHandler handler) throws IOException {
        addCorsHeaders(exchange);
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }
        try {
            handler.handle(exchange);
        } catch (CentralFarmService.FarmRejectedException exception) {
            sendJson(exchange, 403, errorJson(exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            sendJson(exchange, 400, errorJson(exception.getMessage()));
        } catch (Exception exception) {
            sendJson(exchange, 500, errorJson(OperationMessages.INTERNAL_SERVER_ERROR));
        } finally {
            exchange.close();
        }
    }

    private String farmJson(CentralFarm farm, boolean includeSecret) {
        return "{"
                + "\"farmId\":" + nullableString(farm.farmId()) + ","
                + "\"runtimeInstanceId\":" + nullableString(farm.runtimeInstanceId()) + ","
                + "\"farmName\":" + nullableString(farm.farmName()) + ","
                + "\"runtimeVersion\":" + nullableString(farm.runtimeVersion()) + ","
                + "\"hostname\":" + nullableString(farm.hostname()) + ","
                + "\"displayLocation\":" + nullableString(farm.displayLocation()) + ","
                + "\"enabled\":" + farm.enabled() + ","
                + "\"registeredAt\":" + nullableString(farm.registeredAt().toString()) + ","
                + "\"lastSeenAt\":" + nullableString(farm.lastSeenAt() == null ? null : farm.lastSeenAt().toString()) + ","
                + "\"printerCount\":" + farm.printerCount() + ","
                + "\"cameraCount\":" + farm.cameraCount() + ","
                + "\"activePrintCount\":" + farm.activePrintCount() + ","
                + "\"warningCount\":" + farm.warningCount() + ","
                + "\"errorCount\":" + farm.errorCount() + ","
                + "\"spaghettiAlertCount\":" + farm.spaghettiAlertCount()
                + (includeSecret ? ",\"farmSecret\":" + nullableString(farm.farmSecret()) : "")
                + "}";
    }

    private String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private String requiredJsonString(String body, String fieldName) {
        String value = optionalJsonString(body, fieldName, null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private String optionalJsonString(String body, String fieldName, String fallback) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")
                .matcher(body);
        return matcher.find() ? unescapeJsonString(matcher.group(1)) : fallback;
    }

    private int optionalJsonInteger(String body, String fieldName, int fallback) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*(-?[0-9]+)").matcher(body);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : fallback;
    }

    private String objectJson(String body, String fieldName) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*(\\{.*?\\})", Pattern.DOTALL)
                .matcher(body);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String unescapeJsonString(String value) {
        return value.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n").replace("\\r", "\r");
    }

    private void sendJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        send(exchange, statusCode, body, "application/json; charset=utf-8");
    }

    private void sendHtml(HttpExchange exchange, int statusCode, String body) throws IOException {
        send(exchange, statusCode, body, "text/html; charset=utf-8");
    }

    private void sendResource(HttpExchange exchange, String resourcePath, String contentType) throws IOException {
        try (InputStream inputStream = CentralApiServer.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                sendJson(exchange, 404, errorJson(OperationMessages.resourceNotFound(resourcePath)));
                return;
            }
            sendBytes(exchange, 200, inputStream.readAllBytes(), contentType);
        }
    }

    private void send(HttpExchange exchange, int statusCode, String body, String contentType) throws IOException {
        sendBytes(exchange, statusCode, body.getBytes(StandardCharsets.UTF_8), contentType);
    }

    private void sendBytes(HttpExchange exchange, int statusCode, byte[] bytes, String contentType) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    private void addCorsHeaders(HttpExchange exchange) {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type");
    }

    private String errorJson(String message) {
        return "{\"error\":" + nullableString(message) + "}";
    }

    private String nullableString(String value) {
        return value == null ? "null" : "\"" + escapeJson(value) + "\"";
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
