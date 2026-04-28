package printerhub.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import printerhub.PrinterSnapshot;
import printerhub.runtime.PrinterRegistry;
import printerhub.runtime.PrinterRuntimeNode;
import printerhub.runtime.PrinterRuntimeStateCache;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public final class RemoteApiServer {

    private final int port;
    private final PrinterRegistry printerRegistry;
    private final PrinterRuntimeStateCache stateCache;
    private HttpServer server;

    public RemoteApiServer(
            int port,
            PrinterRegistry printerRegistry,
            PrinterRuntimeStateCache stateCache
    ) {
        this.port = port;
        this.printerRegistry = printerRegistry;
        this.stateCache = stateCache;
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.setExecutor(Executors.newFixedThreadPool(8));

            server.createContext("/health", this::handleHealth);
            server.createContext("/printers", this::handlePrinters);

            server.start();

            System.out.println("[PrinterHub] API server started on port " + port);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to start API server on port " + port, ex);
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            System.out.println("[PrinterHub] API server stopped");
        }
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            send(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            return;
        }

        send(exchange, 200, "{\"status\":\"ok\"}");
    }

    private void handlePrinters(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            send(exchange, 405, "{\"error\":\"method_not_allowed\"}");
            return;
        }

        StringBuilder json = new StringBuilder();
        json.append("[");

        boolean first = true;

        for (PrinterRuntimeNode node : printerRegistry.all()) {
            if (!first) {
                json.append(",");
            }

            PrinterSnapshot snapshot = stateCache.findByPrinterId(node.id()).orElse(null);

            json.append("{")
                    .append("\"id\":\"").append(escapeJson(node.id())).append("\",")
                    .append("\"displayName\":\"").append(escapeJson(node.displayName())).append("\",")
                    .append("\"portName\":\"").append(escapeJson(node.portName())).append("\",")
                    .append("\"mode\":\"").append(escapeJson(node.mode())).append("\",")
                    .append("\"enabled\":").append(node.enabled()).append(",");

            if (snapshot == null) {
                json.append("\"state\":\"UNKNOWN\",")
                        .append("\"lastResponse\":null,")
                        .append("\"errorMessage\":null,")
                        .append("\"updatedAt\":null");
            } else {
                json.append("\"state\":\"").append(snapshot.state()).append("\",")
                        .append("\"lastResponse\":").append(toJsonNullable(snapshot.lastResponse())).append(",")
                        .append("\"errorMessage\":").append(toJsonNullable(snapshot.errorMessage())).append(",")
                        .append("\"updatedAt\":\"").append(snapshot.updatedAt()).append("\"");
            }

            json.append("}");

            first = false;
        }

        json.append("]");

        send(exchange, 200, json.toString());
    }

    private void send(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);

        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    private String toJsonNullable(String value) {
        if (value == null) {
            return "null";
        }

        return "\"" + escapeJson(value) + "\"";
    }

    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}