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
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutorService;

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
    private final AtomicBoolean pollingInProgress = new AtomicBoolean(false);

    private HttpServer server;
    private ExecutorService httpExecutor;
    private ScheduledExecutorService pollingExecutor;
    //private java.util.concurrent.ExecutorService httpExecutor;
    //private ScheduledExecutorService pollingExecutor;

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
        httpExecutor = Executors.newFixedThreadPool(4);
        pollingExecutor = Executors.newSingleThreadScheduledExecutor();

        server = HttpServer.create(new InetSocketAddress(apiPort), 0);
        server.createContext("/health", this::handleHealth);
        server.createContext("/printer/status", this::handlePrinterStatus);
        server.createContext("/printer/poll", this::handlePrinterPoll);
        server.setExecutor(httpExecutor);
        server.start();

        pollingExecutor.scheduleWithFixedDelay(
                this::runBackgroundPollSafely,
                0,
                pollDelayMs,
                TimeUnit.MILLISECONDS
        );

        System.out.println(OperationMessages.infoMessage(
                "Remote API started on http://localhost:" + apiPort
        ));
        System.out.println(OperationMessages.infoMessage(
                "Background printer monitoring started for port '" + printerPortName + "' in mode '" + printerMode + "'."
        ));
    }

    public void stop() {
        System.out.println(OperationMessages.infoMessage("Stopping Remote API server..."));

        if (pollingExecutor != null) {
            pollingExecutor.shutdownNow();
        }

        if (server != null) {
            server.stop(0);
        }

        if (httpExecutor != null) {
            httpExecutor.shutdownNow();
        }

        stateTracker.markDisconnected();
        System.out.println(OperationMessages.infoMessage("Remote API server stopped."));
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

        PrinterSnapshot snapshot = pollPrinterOnce();

        if (snapshot.getState() == PrinterState.ERROR || snapshot.getState() == PrinterState.DISCONNECTED) {
            sendJson(exchange, 502, snapshotJson(snapshot));
            return;
        }

        sendJson(exchange, 200, snapshotJson(snapshot));
    }

    private void runBackgroundPollSafely() {
        try {
            pollPrinterOnce();
        } catch (Exception e) {
            stateTracker.markError("Background polling failed: " + e.getMessage());
            System.err.println(OperationMessages.errorMessage(
                    "Background polling failed: " + e.getMessage()
            ));
        }
    }

    private PrinterSnapshot pollPrinterOnce() {
        if (!pollingInProgress.compareAndSet(false, true)) {
            return stateTracker.getCurrentSnapshot();
        }

        PrinterPort port = new SerialConnection(
                printerPortName,
                baudRate,
                SerialPortAdapterFactory.create(printerPortName, printerMode)
        );

        try {
            stateTracker.markConnecting();

            if (!port.connect()) {
                return stateTracker.markDisconnected();
            }

            sleepInitDelay();

            port.sendCommand(DEFAULT_POLL_COMMAND);
            String response = port.readLine();

            if (!isValidTemperatureResponse(response)) {
                return stateTracker.markError(response);
            }

            return stateTracker.updateFromResponse(response);

        } catch (TimeoutException e) {
            return stateTracker.markError("Printer timeout: " + e.getMessage());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return stateTracker.markError("Printer polling interrupted: " + e.getMessage());

        } catch (Exception e) {
            return stateTracker.markDisconnected();

        } finally {
            try {
                port.disconnect();
            } finally {
                pollingInProgress.set(false);
            }
        }
    }

    private void sleepInitDelay() throws InterruptedException {
        if (initDelayMs > 0) {
            Thread.sleep(initDelayMs);
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

        return String.format(Locale.ROOT, "%.2f", value);
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

        String normalized = response.toLowerCase(Locale.ROOT);

        return normalized.contains("ok")
                && normalized.contains("t:")
                && normalized.contains("b:");
    }
}