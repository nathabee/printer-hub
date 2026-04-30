package printerhub.api;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import printerhub.OperationMessages;
import printerhub.PrinterSnapshot;
import printerhub.PrinterState;
import printerhub.config.RuntimeDefaults;
import printerhub.monitoring.PrinterMonitoringScheduler;
import printerhub.persistence.MonitoringRules;
import printerhub.persistence.MonitoringRulesStore;
import printerhub.persistence.PrinterConfigurationStore;
import printerhub.runtime.PrinterRegistry;
import printerhub.runtime.PrinterRuntimeNode;
import printerhub.runtime.PrinterRuntimeNodeFactory;
import printerhub.runtime.PrinterRuntimeStateCache;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RemoteApiServer {

    private final int port;
    private final PrinterRegistry printerRegistry;
    private final PrinterRuntimeStateCache stateCache;
    private final PrinterMonitoringScheduler monitoringScheduler;
    private final PrinterConfigurationStore printerConfigurationStore;
    private final MonitoringRulesStore monitoringRulesStore;

    private HttpServer server;

    public RemoteApiServer(
            int port,
            PrinterRegistry printerRegistry,
            PrinterRuntimeStateCache stateCache,
            PrinterMonitoringScheduler monitoringScheduler,
            PrinterConfigurationStore printerConfigurationStore,
            MonitoringRulesStore monitoringRulesStore
    ) {
        if (port < RuntimeDefaults.MIN_PORT || port > RuntimeDefaults.MAX_PORT) {
            throw new IllegalArgumentException(OperationMessages.PORT_MUST_BE_IN_VALID_RANGE);
        }
        if (printerRegistry == null) {
            throw new IllegalArgumentException(OperationMessages.PRINTER_REGISTRY_MUST_NOT_BE_NULL);
        }
        if (stateCache == null) {
            throw new IllegalArgumentException(OperationMessages.STATE_CACHE_MUST_NOT_BE_NULL);
        }
        if (monitoringScheduler == null) {
            throw new IllegalArgumentException(OperationMessages.MONITORING_SCHEDULER_MUST_NOT_BE_NULL);
        }
        if (printerConfigurationStore == null) {
            throw new IllegalArgumentException(OperationMessages.PRINTER_CONFIGURATION_STORE_MUST_NOT_BE_NULL);
        }
        if (monitoringRulesStore == null) {
            throw new IllegalArgumentException(OperationMessages.MONITORING_RULES_STORE_MUST_NOT_BE_NULL);
        }

        this.port = port;
        this.printerRegistry = printerRegistry;
        this.stateCache = stateCache;
        this.monitoringScheduler = monitoringScheduler;
        this.printerConfigurationStore = printerConfigurationStore;
        this.monitoringRulesStore = monitoringRulesStore;
    }

    public void start() {
        if (server != null) {
            return;
        }

        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.setExecutor(Executors.newFixedThreadPool(RuntimeDefaults.DEFAULT_API_THREAD_POOL_SIZE));

            server.createContext("/health", exchange -> safeHandle(exchange, this::handleHealth));
            server.createContext("/printers", exchange -> safeHandle(exchange, this::handlePrinters));
            server.createContext("/settings/monitoring", exchange -> safeHandle(exchange, this::handleMonitoringSettings));
            server.createContext("/dashboard", exchange -> safeHandle(exchange, this::handleDashboard));

            server.start();

            System.out.println(OperationMessages.apiServerStarted(port));
        } catch (IOException exception) {
            throw new IllegalStateException(OperationMessages.failedToStartApiServer(port), exception);
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
            System.out.println(OperationMessages.apiServerStopped());
        }
    }

    private void safeHandle(HttpExchange exchange, ExchangeHandler handler) throws IOException {
        try {
            handler.handle(exchange);
        } catch (IllegalArgumentException exception) {
            sendJson(exchange, 400, errorJson(safeMessage(exception)));
        } catch (IllegalStateException exception) {
            System.err.println(OperationMessages.apiOperationFailed(safeMessage(exception)));
            sendJson(exchange, 500, errorJson(safeMessage(exception)));
        } catch (Exception exception) {
            System.err.println(OperationMessages.unexpectedApiError(safeMessage(exception)));
            sendJson(exchange, 500, errorJson(OperationMessages.INTERNAL_SERVER_ERROR));
        }
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
            return;
        }

        sendJson(exchange, 200, "{\"status\":\"ok\"}");
    }

    private void handleMonitoringSettings(HttpExchange exchange) throws IOException {
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 200, monitoringRulesJson(monitoringRulesStore.load()));
            return;
        }

        if ("PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
            String body = readBody(exchange);
            MonitoringRules currentRules = monitoringRulesStore.load();

            MonitoringRules updatedRules = new MonitoringRules(
                    optionalJsonLong(body, "pollIntervalSeconds", currentRules.pollIntervalSeconds()),
                    optionalJsonLong(
                            body,
                            "snapshotMinimumIntervalSeconds",
                            currentRules.snapshotMinimumIntervalSeconds()
                    ),
                    optionalJsonDouble(
                            body,
                            "temperatureDeltaThreshold",
                            currentRules.temperatureDeltaThreshold()
                    ),
                    optionalJsonLong(
                            body,
                            "eventDeduplicationWindowSeconds",
                            currentRules.eventDeduplicationWindowSeconds()
                    ),
                    optionalJsonErrorPersistenceBehavior(
                            body,
                            "errorPersistenceBehavior",
                            currentRules.errorPersistenceBehavior()
                    )
            );

            monitoringRulesStore.save(updatedRules);
            monitoringScheduler.updateMonitoringRules(updatedRules);

            sendJson(exchange, 200, monitoringRulesJson(updatedRules));
            return;
        }

        sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
    }

    private void handlePrinters(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();

        if ("/printers".equals(path)) {
            handlePrintersRoot(exchange);
            return;
        }

        if (path.startsWith("/printers/")) {
            handlePrinterById(exchange, path);
            return;
        }

        sendJson(exchange, 404, errorJson(OperationMessages.PRINTER_ENDPOINT_NOT_FOUND));
    }

    private void handlePrintersRoot(HttpExchange exchange) throws IOException {
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 200, printersJson());
            return;
        }

        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            String body = readBody(exchange);

            PrinterRuntimeNode node = PrinterRuntimeNodeFactory.create(
                    requiredJsonString(body, "id"),
                    requiredJsonString(body, "displayName"),
                    requiredJsonString(body, "portName"),
                    requiredJsonString(body, "mode"),
                    optionalJsonBoolean(body, "enabled", true)
            );

            boolean registered = false;

            try {
                printerRegistry.register(node);
                registered = true;

                printerConfigurationStore.save(node);
                monitoringScheduler.startMonitoring(node);

                sendJson(exchange, 201, printerJson(node));
            } catch (Exception exception) {
                if (registered) {
                    rollbackRegister(node.id());
                }
                throw exception;
            }

            return;
        }

        sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
    }

    private void handlePrinterById(HttpExchange exchange, String path) throws IOException {
        String remaining = path.substring("/printers/".length());

        if (remaining.isBlank()) {
            sendJson(exchange, 404, errorJson(OperationMessages.PRINTER_NOT_FOUND));
            return;
        }

        String[] parts = remaining.split("/");
        String printerId = parts[0];

        if (parts.length == 1) {
            handlePrinterResource(exchange, printerId);
            return;
        }

        if (parts.length == 2 && "status".equals(parts[1])) {
            handlePrinterStatus(exchange, printerId);
            return;
        }

        if (parts.length == 2 && "enable".equals(parts[1])) {
            handlePrinterEnable(exchange, printerId);
            return;
        }

        if (parts.length == 2 && "disable".equals(parts[1])) {
            handlePrinterDisable(exchange, printerId);
            return;
        }

        sendJson(exchange, 404, errorJson(OperationMessages.PRINTER_ENDPOINT_NOT_FOUND));
    }

    private void handlePrinterResource(HttpExchange exchange, String printerId) throws IOException {
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            Optional<PrinterRuntimeNode> node = printerRegistry.findById(printerId);

            if (node.isEmpty()) {
                sendJson(exchange, 404, errorJson(OperationMessages.PRINTER_NOT_FOUND));
                return;
            }

            sendJson(exchange, 200, printerJson(node.get()));
            return;
        }

        if ("PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
            String body = readBody(exchange);

            PrinterRuntimeNode oldNode = printerRegistry.findById(printerId).orElse(null);

            if (oldNode == null) {
                sendJson(exchange, 404, errorJson(OperationMessages.PRINTER_NOT_FOUND));
                return;
            }

            boolean enabled = optionalJsonBoolean(body, "enabled", oldNode.enabled());

            PrinterRuntimeNode newNode = PrinterRuntimeNodeFactory.create(
                    printerId,
                    requiredJsonString(body, "displayName"),
                    requiredJsonString(body, "portName"),
                    requiredJsonString(body, "mode"),
                    enabled
            );

            monitoringScheduler.stopMonitoring(printerId);
            printerRegistry.remove(printerId);

            boolean restored = false;

            try {
                printerConfigurationStore.save(newNode);
                printerRegistry.register(newNode);
                monitoringScheduler.startMonitoring(newNode);

                sendJson(exchange, 200, printerJson(newNode));
            } catch (Exception exception) {
                try {
                    printerRegistry.register(oldNode);
                    monitoringScheduler.startMonitoring(oldNode);
                    restored = true;
                } catch (Exception rollbackException) {
                    System.err.println(OperationMessages.failedToRestorePrinterAfterPut(
                            printerId,
                            safeMessage(rollbackException)
                    ));
                }

                if (!restored) {
                    stateCache.update(
                            printerId,
                            PrinterSnapshot.error(
                                    PrinterState.ERROR,
                                    null,
                                    null,
                                    null,
                                    OperationMessages.printerUpdateRestoreFailed(printerId),
                                    Instant.now()
                            )
                    );
                }

                throw exception;
            }

            return;
        }

        if ("DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
            PrinterRuntimeNode existingNode = printerRegistry.findById(printerId).orElse(null);

            if (existingNode == null) {
                sendJson(exchange, 404, errorJson(OperationMessages.PRINTER_NOT_FOUND));
                return;
            }

            monitoringScheduler.stopMonitoring(printerId);
            printerRegistry.remove(printerId);
            stateCache.remove(printerId);

            try {
                printerConfigurationStore.delete(printerId);
                existingNode.close();

                sendJson(exchange, 200, "{\"deleted\":\"" + escapeJson(printerId) + "\"}");
            } catch (Exception exception) {
                try {
                    printerRegistry.register(existingNode);
                    monitoringScheduler.startMonitoring(existingNode);
                } catch (Exception rollbackException) {
                    System.err.println(OperationMessages.failedToRestorePrinterAfterDelete(
                            printerId,
                            safeMessage(rollbackException)
                    ));
                }
                throw exception;
            }

            return;
        }

        sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
    }

    private void handlePrinterStatus(HttpExchange exchange, String printerId) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
            return;
        }

        PrinterRuntimeNode node = printerRegistry.findById(printerId).orElse(null);

        if (node == null) {
            sendJson(exchange, 404, errorJson(OperationMessages.PRINTER_NOT_FOUND));
            return;
        }

        PrinterSnapshot snapshot = stateCache.findByPrinterId(printerId).orElse(null);
        sendJson(exchange, 200, snapshotJson(snapshot));
    }

    private void handlePrinterEnable(HttpExchange exchange, String printerId) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
            return;
        }

        PrinterRuntimeNode node = printerRegistry.findById(printerId).orElse(null);

        if (node == null) {
            sendJson(exchange, 404, errorJson(OperationMessages.PRINTER_NOT_FOUND));
            return;
        }

        boolean previousEnabled = node.enabled();

        try {
            printerConfigurationStore.enable(printerId);
            node.enable();
            monitoringScheduler.startMonitoring(node);

            sendJson(exchange, 200, printerJson(node));
        } catch (Exception exception) {
            if (!previousEnabled) {
                node.disable();
                monitoringScheduler.startMonitoring(node);
            }
            throw exception;
        }
    }

    private void handlePrinterDisable(HttpExchange exchange, String printerId) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
            return;
        }

        PrinterRuntimeNode node = printerRegistry.findById(printerId).orElse(null);

        if (node == null) {
            sendJson(exchange, 404, errorJson(OperationMessages.PRINTER_NOT_FOUND));
            return;
        }

        boolean previousEnabled = node.enabled();

        try {
            printerConfigurationStore.disable(printerId);
            node.disable();
            monitoringScheduler.stopMonitoring(printerId);
            node.close();
            monitoringScheduler.startMonitoring(node);

            sendJson(exchange, 200, printerJson(node));
        } catch (Exception exception) {
            if (previousEnabled) {
                node.enable();
                monitoringScheduler.startMonitoring(node);
            }
            throw exception;
        }
    }

    private String monitoringRulesJson(MonitoringRules monitoringRules) {
        return "{"
                + "\"pollIntervalSeconds\":" + monitoringRules.pollIntervalSeconds() + ","
                + "\"snapshotMinimumIntervalSeconds\":" + monitoringRules.snapshotMinimumIntervalSeconds() + ","
                + "\"temperatureDeltaThreshold\":" + formatDouble(monitoringRules.temperatureDeltaThreshold()) + ","
                + "\"eventDeduplicationWindowSeconds\":" + monitoringRules.eventDeduplicationWindowSeconds() + ","
                + "\"errorPersistenceBehavior\":\"" + escapeJson(monitoringRules.errorPersistenceBehavior().name()) + "\""
                + "}";
    }

    private String printersJson() {
        StringBuilder json = new StringBuilder();
        json.append("{\"printers\":[");

        boolean first = true;

        for (PrinterRuntimeNode node : printerRegistry.all()) {
            if (!first) {
                json.append(",");
            }

            json.append(printerJson(node));
            first = false;
        }

        json.append("]}");

        return json.toString();
    }

    private String printerJson(PrinterRuntimeNode node) {
        PrinterSnapshot snapshot = stateCache.findByPrinterId(node.id()).orElse(null);

        return "{"
                + "\"id\":\"" + escapeJson(node.id()) + "\","
                + "\"displayName\":\"" + escapeJson(node.displayName()) + "\","
                + "\"name\":\"" + escapeJson(node.displayName()) + "\","
                + "\"portName\":\"" + escapeJson(node.portName()) + "\","
                + "\"mode\":\"" + escapeJson(node.mode()) + "\","
                + "\"enabled\":" + node.enabled() + ","
                + "\"state\":\"" + (snapshot == null ? "UNKNOWN" : snapshot.state()) + "\","
                + "\"hotendTemperature\":" + nullableNumber(snapshot == null ? null : snapshot.hotendTemperature()) + ","
                + "\"bedTemperature\":" + nullableNumber(snapshot == null ? null : snapshot.bedTemperature()) + ","
                + "\"lastResponse\":" + nullableString(snapshot == null ? null : snapshot.lastResponse()) + ","
                + "\"errorMessage\":" + nullableString(snapshot == null ? null : snapshot.errorMessage()) + ","
                + "\"updatedAt\":" + nullableString(snapshot == null ? null : String.valueOf(snapshot.updatedAt()))
                + "}";
    }

    private String snapshotJson(PrinterSnapshot snapshot) {
        if (snapshot == null) {
            return "{"
                    + "\"state\":\"UNKNOWN\","
                    + "\"hotendTemperature\":null,"
                    + "\"bedTemperature\":null,"
                    + "\"lastResponse\":null,"
                    + "\"errorMessage\":null,"
                    + "\"updatedAt\":null"
                    + "}";
        }

        return "{"
                + "\"state\":\"" + snapshot.state() + "\","
                + "\"hotendTemperature\":" + nullableNumber(snapshot.hotendTemperature()) + ","
                + "\"bedTemperature\":" + nullableNumber(snapshot.bedTemperature()) + ","
                + "\"lastResponse\":" + nullableString(snapshot.lastResponse()) + ","
                + "\"errorMessage\":" + nullableString(snapshot.errorMessage()) + ","
                + "\"updatedAt\":" + nullableString(String.valueOf(snapshot.updatedAt()))
                + "}";
    }

    private void sendJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");

        exchange.sendResponseHeaders(statusCode, bytes.length);

        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    private String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private String errorJson(String message) {
        return "{\"error\":" + nullableString(message) + "}";
    }

    private String nullableNumber(Double value) {
        if (value == null) {
            return "null";
        }

        return formatDouble(value);
    }

    private String formatDouble(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private String nullableString(String value) {
        if (value == null) {
            return "null";
        }

        return "\"" + escapeJson(value) + "\"";
    }

    private String requiredJsonString(String body, String fieldName) {
        String value = optionalJsonString(body, fieldName, null);

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(OperationMessages.fieldMustNotBeBlank(fieldName));
        }

        return value.trim();
    }

    private String optionalJsonString(String body, String fieldName, String fallback) {
        Pattern pattern = Pattern.compile(
                "\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\"([^\"]*)\""
        );

        Matcher matcher = pattern.matcher(body);

        if (!matcher.find()) {
            return fallback;
        }

        return matcher.group(1);
    }

    private boolean optionalJsonBoolean(String body, String fieldName, boolean fallback) {
        Pattern pattern = Pattern.compile(
                "\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*(true|false)"
        );

        Matcher matcher = pattern.matcher(body);

        if (!matcher.find()) {
            return fallback;
        }

        return Boolean.parseBoolean(matcher.group(1));
    }

    private long optionalJsonLong(String body, String fieldName, long fallback) {
        Pattern pattern = Pattern.compile(
                "\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*(-?[0-9]+)"
        );

        Matcher matcher = pattern.matcher(body);

        if (!matcher.find()) {
            return fallback;
        }

        return Long.parseLong(matcher.group(1));
    }

    private double optionalJsonDouble(String body, String fieldName, double fallback) {
        Pattern pattern = Pattern.compile(
                "\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)"
        );

        Matcher matcher = pattern.matcher(body);

        if (!matcher.find()) {
            return fallback;
        }

        return Double.parseDouble(matcher.group(1));
    }

    private MonitoringRules.ErrorPersistenceBehavior optionalJsonErrorPersistenceBehavior(
            String body,
            String fieldName,
            MonitoringRules.ErrorPersistenceBehavior fallback
    ) {
        String value = optionalJsonString(body, fieldName, null);

        if (value == null || value.isBlank()) {
            return fallback;
        }

        return MonitoringRules.parseErrorPersistenceBehavior(value);
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private void handleDashboard(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
            return;
        }

        String path = exchange.getRequestURI().getPath();

        if ("/dashboard".equals(path) || "/dashboard/".equals(path)) {
            sendResource(exchange, "/dashboard/index.html", "text/html; charset=utf-8");
            return;
        }

        if ("/dashboard/dashboard.css".equals(path)) {
            sendResource(exchange, "/dashboard/dashboard.css", "text/css; charset=utf-8");
            return;
        }

        if ("/dashboard/dashboard.js".equals(path)) {
            sendResource(exchange, "/dashboard/dashboard.js", "application/javascript; charset=utf-8");
            return;
        }

        sendJson(exchange, 404, errorJson(OperationMessages.DASHBOARD_RESOURCE_NOT_FOUND));
    }

    private void sendResource(
            HttpExchange exchange,
            String resourcePath,
            String contentType
    ) throws IOException {
        try (InputStream inputStream = RemoteApiServer.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                sendJson(exchange, 404, errorJson(OperationMessages.resourceNotFound(resourcePath)));
                return;
            }

            byte[] bytes = inputStream.readAllBytes();

            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);

            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(bytes);
            }
        }
    }

    private void rollbackRegister(String printerId) {
        try {
            monitoringScheduler.stopMonitoring(printerId);
            printerRegistry.remove(printerId);
            stateCache.remove(printerId);
        } catch (Exception exception) {
            System.err.println(OperationMessages.failedToRollbackPrinterRegistration(
                    printerId,
                    safeMessage(exception)
            ));
        }
    }

    private String safeMessage(Exception exception) {
        if (exception == null) {
            return OperationMessages.UNKNOWN_API_ERROR;
        }

        return OperationMessages.safeDetail(
                exception.getMessage(),
                OperationMessages.UNKNOWN_API_ERROR
        );
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}