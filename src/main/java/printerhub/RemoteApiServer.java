package printerhub;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import printerhub.serial.SerialPortAdapterFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

public class RemoteApiServer {

    private static final String DEFAULT_POLL_COMMAND = "M105";
    private static final DateTimeFormatter JSON_TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final int apiPort;
    private final String printerPortName;
    private final String printerMode;
    private final int baudRate;
    private final long initDelayMs;
    private final long pollDelayMs;
    private final PrinterStateTracker stateTracker;

    private HttpServer server;

    public RemoteApiServer(int apiPort,
                           String printerPortName,
                           String printerMode,
                           int baudRate,
                           long initDelayMs,
                           long pollDelayMs) {
        this.apiPort = apiPort;
        this.printerPortName = printerPortName;
        this.printerMode = printerMode;
        this.baudRate = baudRate;
        this.initDelayMs = initDelayMs;
        this.pollDelayMs = pollDelayMs;
        this.stateTracker = new PrinterStateTracker();
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(apiPort), 0);
        server.createContext("/health", this::handleHealth);
        server.createContext("/printer/status", this::handlePrinterStatus);
        server.createContext("/printer/poll", this::handlePrinterPoll);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();

        System.out.println(OperationMessages.infoMessage(
                "Remote API started on http://localhost:" + apiPort
        ));
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        if (!isMethod(exchange, "GET")) {
            sendJson(exchange, 405, errorJson("Method not allowed"));
            return;
        }

        sendJson(exchange, 200, """
                {
                  "status": "UP",
                  "service": "PrinterHub"
                }
                """);
    }

    private void handlePrinterStatus(HttpExchange exchange) throws IOException {
        if (!isMethod(exchange, "GET")) {
            sendJson(exchange, 405, errorJson("Method not allowed"));
            return;
        }

        sendJson(exchange, 200, snapshotJson(stateTracker.getCurrentSnapshot()));
    }

    private void handlePrinterPoll(HttpExchange exchange) throws IOException {
        if (!isMethod(exchange, "POST")) {
            sendJson(exchange, 405, errorJson("Method not allowed"));
            return;
        }

        PrinterPort port = new SerialConnection(
                printerPortName,
                baudRate,
                SerialPortAdapterFactory.create(printerPortName, printerMode)
        );

        try {
            stateTracker.markConnecting();

            if (!port.connect()) {
                stateTracker.markDisconnected();
                sendJson(exchange, 500, errorJson(
                        OperationMessages.failedToConnectForPolling(printerPortName)
                ));
                return;
            }

            Thread.sleep(initDelayMs);

            port.sendCommand(DEFAULT_POLL_COMMAND);
            String response = port.readLine();

            if (!isValidTemperatureResponse(response)) {
                stateTracker.markError(response);
                sendJson(exchange, 502, errorJson("Invalid printer response for M105"));
                return;
            }

            PrinterSnapshot snapshot = stateTracker.updateFromResponse(response);
            sendJson(exchange, 200, snapshotJson(snapshot));

        } catch (TimeoutException e) {
            stateTracker.markError(e.getMessage());
            sendJson(exchange, 504, errorJson("Printer timeout: " + e.getMessage()));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            stateTracker.markError(e.getMessage());
            sendJson(exchange, 500, errorJson("Printer polling interrupted: " + e.getMessage()));

        } catch (Exception e) {
            stateTracker.markDisconnected();
            sendJson(exchange, 500, errorJson("Printer polling failed: " + e.getMessage()));

        } finally {
            port.disconnect();
        }
}

    private boolean isMethod(HttpExchange exchange, String expectedMethod) {
        return expectedMethod.equalsIgnoreCase(exchange.getRequestMethod());
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

    private String snapshotJson(PrinterSnapshot snapshot) {
        return "{\n"
                + "  \"state\": \"" + snapshot.getState() + "\",\n"
                + "  \"hotendTemperature\": " + nullableNumber(snapshot.getHotendTemperature()) + ",\n"
                + "  \"bedTemperature\": " + nullableNumber(snapshot.getBedTemperature()) + ",\n"
                + "  \"lastResponse\": " + nullableString(snapshot.getLastResponse()) + ",\n"
                + "  \"updatedAt\": \"" + JSON_TIME_FORMAT.format(snapshot.getUpdatedAt()) + "\"\n"
                + "}\n";
    }

    private String errorJson(String message) {
        return "{\n"
                + "  \"error\": " + nullableString(message) + "\n"
                + "}\n";
    }

    private String nullableNumber(Double value) {
        if (value == null) {
            return "null";
        }

        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private String nullableString(String value) {
        if (value == null) {
            return "null";
        }

        return "\"" + escapeJson(value) + "\"";
    }

    private String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder();

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);

            switch (c) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 0x20 || c > 0x7E) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
                }
            }
        }

        return escaped.toString();
    }

    private boolean isValidTemperatureResponse(String response) {
    if (response == null || response.isBlank()) {
        return false;
    }

    String normalized = response.toLowerCase();

    return normalized.contains("ok")
            && normalized.contains("t:")
            && normalized.contains("b:");
    }
}