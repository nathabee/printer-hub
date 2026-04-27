package printerhub;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import printerhub.farm.PrinterFarmStore;
import printerhub.farm.PrinterNode;
import printerhub.jobs.PrintJob;
import printerhub.jobs.PrintJobExecutionService;
import printerhub.jobs.PrintJobStore;
import printerhub.jobs.PrintJobType;
import printerhub.persistence.MonitoringRules;
import printerhub.persistence.PersistentPrintJobStore;
import printerhub.persistence.PrinterEventStore;
import printerhub.persistence.PrinterSnapshotStore;
import printerhub.persistence.RuntimeConfigurationStore;
import printerhub.serial.SerialPortAdapterFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class RemoteApiServer {

    private static final String DEFAULT_POLL_COMMAND = "M105";
    private static final DateTimeFormatter JSON_TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final int apiPort;
    private final String printerPortName;
    private final String printerMode;
    private final int baudRate;
    private final long initDelayMs;
    private final long pollDelayMs;
    private final AtomicBoolean pollingInProgress = new AtomicBoolean(false);

    private final RuntimeConfigurationStore configurationStore;
    private final PrintJobStore jobStore;
    private final PrinterFarmStore printerFarmStore;
    private final PrinterEventStore eventStore;
    private final PrinterSnapshotStore snapshotStore;
    private final PrintJobExecutionService jobExecutionService;

    private HttpServer server;
    private ExecutorService httpExecutor;
    private ScheduledExecutorService pollingExecutor;

    public RemoteApiServer(
            int apiPort,
            String printerPortName,
            String printerMode,
            int baudRate,
            long initDelayMs,
            long pollDelayMs
    ) {
        this.apiPort = apiPort;
        this.printerPortName = printerPortName;
        this.printerMode = printerMode;
        this.baudRate = baudRate;
        this.initDelayMs = initDelayMs;
        this.pollDelayMs = pollDelayMs;

        this.configurationStore = new RuntimeConfigurationStore();
        this.configurationStore.ensureDefaultPrinter(printerPortName, printerMode);
        this.configurationStore.ensureDefaultMonitoringRules();

        this.printerFarmStore = new PrinterFarmStore(
                this.configurationStore.findEnabledPrinters()
        );

        this.jobStore = new PersistentPrintJobStore();
        this.eventStore = new PrinterEventStore();
        this.snapshotStore = new PrinterSnapshotStore(this.configurationStore);
        this.jobExecutionService = new PrintJobExecutionService(
                this.jobStore,
                this.printerFarmStore,
                this.eventStore
        );
    }

    public void start() throws IOException {
        httpExecutor = Executors.newFixedThreadPool(4);
        pollingExecutor = Executors.newSingleThreadScheduledExecutor();

        server = HttpServer.create(new InetSocketAddress(apiPort), 0);
        server.createContext("/health", this::handleHealth);
        server.createContext("/printer/status", this::handlePrinterStatus);
        server.createContext("/printer/poll", this::handlePrinterPoll);
        server.createContext("/jobs", this::handleJobs);
        server.createContext("/printers", this::handlePrinters);
        server.createContext("/config", this::handleConfig);
        server.createContext("/dashboard", this::handleDashboard);
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
                "Background printer monitoring started for configured local runtime."
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

        for (PrinterNode printerNode : printerFarmStore.findAll()) {
            printerNode.getStateTracker().markDisconnected();
        }

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

        sendJson(exchange, 200, snapshotJson(printerFarmStore.getDefaultPrinter().getSnapshot()));
    }

    private void handlePrinterPoll(HttpExchange exchange) throws IOException {
        if (!isMethod(exchange, "POST")) {
            sendJson(exchange, 405, errorJson("Method not allowed"));
            return;
        }

        PrinterSnapshot snapshot = pollPrinterOnce();

        if (snapshot.getState() == PrinterState.ERROR
                || snapshot.getState() == PrinterState.DISCONNECTED) {
            sendJson(exchange, 502, snapshotJson(snapshot));
            return;
        }

        sendJson(exchange, 200, snapshotJson(snapshot));
    }

    private void runBackgroundPollSafely() {
        try {
            for (PrinterNode printerNode : printerFarmStore.findAll()) {
                pollPrinterNode(printerNode);
            }

            jobExecutionService.advanceJobs();

        } catch (Exception e) {
            printerFarmStore.getDefaultPrinter()
                    .getStateTracker()
                    .markError("Background polling failed: " + e.getMessage());

            System.err.println(OperationMessages.errorMessage(
                    "Background polling failed: " + e.getMessage()
            ));
        }
    }

    private PrinterSnapshot pollPrinterOnce() {
        return pollPrinterNode(printerFarmStore.getDefaultPrinter());
    }

    private PrinterSnapshot pollPrinterNode(PrinterNode printerNode) {
        if (!pollingInProgress.compareAndSet(false, true)) {
            return printerNode.getSnapshot();
        }

        PrinterPort port = new SerialConnection(
                printerNode.getPortName(),
                baudRate,
                SerialPortAdapterFactory.create(
                        printerNode.getPortName(),
                        printerNode.getMode()
                )
        );

        PrinterStateTracker tracker = printerNode.getStateTracker();

        try {
            tracker.markConnecting();

            if (!port.connect()) {
                PrinterSnapshot snapshot = tracker.markDisconnected();

                snapshotStore.save(printerNode.getId(), snapshot);

                eventStore.record(
                        printerNode.getId(),
                        printerNode.getAssignedJobId(),
                        "PRINTER_DISCONNECTED",
                        "Printer connection failed"
                );

                return snapshot;
            }

            sleepInitDelay();

            port.sendCommand(DEFAULT_POLL_COMMAND);
            String response = port.readLine();

            if (!isValidTemperatureResponse(response)) {
                PrinterSnapshot snapshot = tracker.markError(response);

                snapshotStore.save(printerNode.getId(), snapshot);

                eventStore.record(
                        printerNode.getId(),
                        printerNode.getAssignedJobId(),
                        "PRINTER_ERROR",
                        "Invalid printer response: " + response
                );

                return snapshot;
            }

            PrinterSnapshot snapshot = tracker.updateFromResponse(response);

            snapshotStore.save(printerNode.getId(), snapshot);

            eventStore.record(
                    printerNode.getId(),
                    printerNode.getAssignedJobId(),
                    "PRINTER_POLLED",
                    "Printer poll completed successfully"
            );

            return snapshot;

        } catch (TimeoutException e) {
            PrinterSnapshot snapshot = tracker.markError("Printer timeout: " + e.getMessage());

            snapshotStore.save(printerNode.getId(), snapshot);

            eventStore.record(
                    printerNode.getId(),
                    printerNode.getAssignedJobId(),
                    "PRINTER_ERROR",
                    "Printer timeout: " + e.getMessage()
            );

            return snapshot;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return tracker.markError("Printer polling interrupted: " + e.getMessage());

        } catch (Exception e) {
            PrinterSnapshot snapshot = tracker.markDisconnected();

            snapshotStore.save(printerNode.getId(), snapshot);

            eventStore.record(
                    printerNode.getId(),
                    printerNode.getAssignedJobId(),
                    "PRINTER_DISCONNECTED",
                    "Printer disconnected during polling"
            );

            return snapshot;

        } finally {
            try {
                port.disconnect();
            } finally {
                pollingInProgress.set(false);
            }
        }
    }

    private void handleConfig(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();

        if ("/config/printers".equals(path)) {
            handleConfigPrintersRoot(exchange);
            return;
        }

        if (path.startsWith("/config/printers/")) {
            handleConfigPrinterById(exchange, path);
            return;
        }

        if ("/config/monitoring-rules".equals(path)) {
            handleConfigMonitoringRules(exchange);
            return;
        }

        sendJson(exchange, 404, errorJson("Configuration endpoint not found"));
    }

    private void handleConfigPrintersRoot(HttpExchange exchange) throws IOException {
        if (isMethod(exchange, "GET")) {
            sendJson(exchange, 200, configuredPrintersJson());
            return;
        }

        if (isMethod(exchange, "POST")) {
            String body = readBody(exchange);

            PrinterNode printerNode = new PrinterNode(
                    requiredJsonString(body, "id"),
                    requiredJsonString(body, "name"),
                    requiredJsonString(body, "portName"),
                    requiredJsonString(body, "mode")
            );

            configurationStore.savePrinter(printerNode);
            reloadPrinterFarm();

            sendJson(exchange, 201, printerJson(printerNode));
            return;
        }

        sendJson(exchange, 405, errorJson("Method not allowed"));
    }

    private void handleConfigPrinterById(HttpExchange exchange, String path) throws IOException {
        String remaining = path.substring("/config/printers/".length());

        if (remaining.isBlank()) {
            sendJson(exchange, 404, errorJson("Configured printer endpoint not found"));
            return;
        }

        String[] parts = remaining.split("/");
        String printerId = parts[0];

        if (parts.length == 1 && isMethod(exchange, "PUT")) {
            String body = readBody(exchange);

            PrinterNode printerNode = new PrinterNode(
                    printerId,
                    requiredJsonString(body, "name"),
                    requiredJsonString(body, "portName"),
                    requiredJsonString(body, "mode")
            );

            configurationStore.savePrinter(printerNode);
            reloadPrinterFarm();

            sendJson(exchange, 200, printerJson(printerNode));
            return;
        }

        if (parts.length == 2 && isMethod(exchange, "POST")) {
            if ("enable".equals(parts[1])) {
                configurationStore.enablePrinter(printerId);
                reloadPrinterFarm();
                sendJson(exchange, 200, configuredPrintersJson());
                return;
            }

            if ("disable".equals(parts[1])) {
                configurationStore.disablePrinter(printerId);
                reloadPrinterFarmAllowingEmpty();
                sendJson(exchange, 200, configuredPrintersJson());
                return;
            }
        }

        sendJson(exchange, 404, errorJson("Configured printer endpoint not found"));
    }

    private void handleConfigMonitoringRules(HttpExchange exchange) throws IOException {
        if (isMethod(exchange, "GET")) {
            sendJson(exchange, 200, monitoringRulesJson(configurationStore.loadMonitoringRules()));
            return;
        }

        if (isMethod(exchange, "PUT")) {
            String body = readBody(exchange);

            MonitoringRules rules = new MonitoringRules(
                    optionalJsonBoolean(body, "snapshotOnStateChange", true),
                    optionalJsonDouble(body, "temperatureThreshold", 1.0),
                    optionalJsonLong(body, "minIntervalSeconds", 30)
            );

            configurationStore.saveMonitoringRules(rules);

            sendJson(exchange, 200, monitoringRulesJson(rules));
            return;
        }

        sendJson(exchange, 405, errorJson("Method not allowed"));
    }

    private void reloadPrinterFarm() {
        printerFarmStore.reload(configurationStore.findEnabledPrinters());
    }

    private void reloadPrinterFarmAllowingEmpty() {
        List<PrinterNode> enabledPrinters = configurationStore.findEnabledPrinters();

        if (!enabledPrinters.isEmpty()) {
            printerFarmStore.reload(enabledPrinters);
        }
    }

    private String configuredPrintersJson() {
        StringBuilder json = new StringBuilder();

        json.append("{\n");
        json.append("  \"printers\": [");

        boolean first = true;
        for (PrinterNode printerNode : configurationStore.findAllPrinters()) {
            if (!first) {
                json.append(",");
            }

            json.append("\n").append(indent(printerJson(printerNode), 4));
            first = false;
        }

        if (!first) {
            json.append("\n  ");
        }

        json.append("]\n");
        json.append("}\n");

        return json.toString();
    }

    private String monitoringRulesJson(MonitoringRules rules) {
        return "{\n"
                + "  \"snapshotOnStateChange\": " + rules.isSnapshotOnStateChange() + ",\n"
                + "  \"temperatureThreshold\": " + nullableNumber(rules.getTemperatureThreshold()) + ",\n"
                + "  \"minIntervalSeconds\": " + rules.getMinIntervalSeconds() + "\n"
                + "}\n";
    }

    private void handleJobs(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();

        if ("/jobs".equals(path)) {
            handleJobsRoot(exchange);
            return;
        }

        if (path.startsWith("/jobs/")) {
            handleJobById(exchange, path);
            return;
        }

        sendJson(exchange, 404, errorJson("Job endpoint not found"));
    }

    private void handleJobsRoot(HttpExchange exchange) throws IOException {
        if (isMethod(exchange, "GET")) {
            sendJson(exchange, 200, jobsJson());
            return;
        }

        if (isMethod(exchange, "POST")) {
            PrintJob job = jobStore.create("simulated-job", PrintJobType.SIMULATED);

            eventStore.record(
                    null,
                    job.getId(),
                    "JOB_CREATED",
                    "Print job created through API"
            );

            sendJson(exchange, 201, jobJson(job));
            return;
        }

        sendJson(exchange, 405, errorJson("Method not allowed"));
    }

    private void handleJobById(HttpExchange exchange, String path) throws IOException {
        if (!isMethod(exchange, "GET")) {
            sendJson(exchange, 405, errorJson("Method not allowed"));
            return;
        }

        String jobId = path.substring("/jobs/".length());

        jobStore.findById(jobId)
                .ifPresentOrElse(
                        job -> sendJsonUnchecked(exchange, 200, jobJson(job)),
                        () -> sendJsonUnchecked(exchange, 404, errorJson("Job not found"))
                );
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

        sendJson(exchange, 404, errorJson("Printer endpoint not found"));
    }

    private void handlePrintersRoot(HttpExchange exchange) throws IOException {
        if (!isMethod(exchange, "GET")) {
            sendJson(exchange, 405, errorJson("Method not allowed"));
            return;
        }

        sendJson(exchange, 200, printersJson());
    }

    private void handlePrinterById(HttpExchange exchange, String path) throws IOException {
        String remaining = path.substring("/printers/".length());
        String[] parts = remaining.split("/");

        if (parts.length < 2) {
            sendJson(exchange, 404, errorJson("Printer endpoint not found"));
            return;
        }

        String printerId = parts[0];
        String action = parts[1];

        PrinterNode printerNode = printerFarmStore.findById(printerId).orElse(null);

        if (printerNode == null) {
            sendJson(exchange, 404, errorJson("Printer not found"));
            return;
        }

        if ("status".equals(action)) {
            handlePrinterStatusById(exchange, printerNode);
            return;
        }

        if ("poll".equals(action)) {
            handlePrinterPollById(exchange, printerNode);
            return;
        }

        if ("jobs".equals(action)) {
            handlePrinterJobAssignment(exchange, printerNode);
            return;
        }

        if ("history".equals(action)) {
            handlePrinterHistory(exchange, printerNode);
            return;
        }

        sendJson(exchange, 404, errorJson("Printer endpoint not found"));
    }

    private void handlePrinterHistory(HttpExchange exchange, PrinterNode printerNode) throws IOException {
        if (!isMethod(exchange, "GET")) {
            sendJson(exchange, 405, errorJson("Method not allowed"));
            return;
        }

        List<PrinterSnapshot> snapshots =
                snapshotStore.findRecentByPrinterId(printerNode.getId(), 20);

        sendJson(exchange, 200, printerHistoryJson(printerNode.getId(), snapshots));
    }

    private void handlePrinterStatusById(HttpExchange exchange, PrinterNode printerNode) throws IOException {
        if (!isMethod(exchange, "GET")) {
            sendJson(exchange, 405, errorJson("Method not allowed"));
            return;
        }

        sendJson(exchange, 200, printerJson(printerNode));
    }

    private void handlePrinterPollById(HttpExchange exchange, PrinterNode printerNode) throws IOException {
        if (!isMethod(exchange, "POST")) {
            sendJson(exchange, 405, errorJson("Method not allowed"));
            return;
        }

        PrinterSnapshot snapshot = pollPrinterNode(printerNode);

        if (snapshot.getState() == PrinterState.ERROR
                || snapshot.getState() == PrinterState.DISCONNECTED) {
            sendJson(exchange, 502, printerJson(printerNode));
            return;
        }

        sendJson(exchange, 200, printerJson(printerNode));
    }

    private void handlePrinterJobAssignment(HttpExchange exchange, PrinterNode printerNode) throws IOException {
        if (!isMethod(exchange, "POST")) {
            sendJson(exchange, 405, errorJson("Method not allowed"));
            return;
        }

        PrintJob job = jobStore.createAssigned(
                "simulated-job-for-" + printerNode.getId(),
                PrintJobType.SIMULATED,
                printerNode.getId()
        );

        printerNode.assignJob(job.getId());

        eventStore.record(
                printerNode.getId(),
                job.getId(),
                "JOB_ASSIGNED",
                "Print job assigned to printer"
        );

        sendJson(exchange, 201, jobJson(job));
    }

    private void sleepInitDelay() throws InterruptedException {
        if (initDelayMs > 0) {
            Thread.sleep(initDelayMs);
        }
    }

    private boolean isMethod(HttpExchange exchange, String expectedMethod) {
        return expectedMethod.equalsIgnoreCase(exchange.getRequestMethod());
    }

    private String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
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

    private void sendJsonUnchecked(HttpExchange exchange, int statusCode, String body) {
        try {
            sendJson(exchange, statusCode, body);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String printersJson() {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"printers\": [");

        boolean first = true;
        for (PrinterNode printerNode : printerFarmStore.findAll()) {
            if (!first) {
                json.append(",");
            }

            json.append("\n").append(indent(printerJson(printerNode), 4));
            first = false;
        }

        if (!first) {
            json.append("\n  ");
        }

        json.append("]\n");
        json.append("}\n");

        return json.toString();
    }

    private String printerJson(PrinterNode printerNode) {
        PrinterSnapshot snapshot = printerNode.getSnapshot();

        return "{\n"
                + "  \"id\": " + nullableString(printerNode.getId()) + ",\n"
                + "  \"name\": " + nullableString(printerNode.getName()) + ",\n"
                + "  \"portName\": " + nullableString(printerNode.getPortName()) + ",\n"
                + "  \"mode\": " + nullableString(printerNode.getMode()) + ",\n"
                + "  \"assignedJobId\": " + nullableString(printerNode.getAssignedJobId()) + ",\n"
                + "  \"state\": \"" + snapshot.getState() + "\",\n"
                + "  \"hotendTemperature\": " + nullableNumber(snapshot.getHotendTemperature()) + ",\n"
                + "  \"bedTemperature\": " + nullableNumber(snapshot.getBedTemperature()) + ",\n"
                + "  \"lastResponse\": " + nullableString(snapshot.getLastResponse()) + ",\n"
                + "  \"updatedAt\": \"" + JSON_TIME_FORMAT.format(snapshot.getUpdatedAt()) + "\"\n"
                + "}\n";
    }

    private String printerHistoryJson(String printerId, List<PrinterSnapshot> snapshots) {
        StringBuilder json = new StringBuilder();

        json.append("{\n");
        json.append("  \"printerId\": ").append(nullableString(printerId)).append(",\n");
        json.append("  \"history\": [");

        boolean first = true;
        for (PrinterSnapshot snapshot : snapshots) {
            if (!first) {
                json.append(",");
            }

            json.append("\n").append(indent(snapshotHistoryJson(snapshot), 4));
            first = false;
        }

        if (!first) {
            json.append("\n  ");
        }

        json.append("]\n");
        json.append("}\n");

        return json.toString();
    }

    private String snapshotHistoryJson(PrinterSnapshot snapshot) {
        return "{\n"
                + "  \"state\": \"" + snapshot.getState() + "\",\n"
                + "  \"lastResponse\": " + nullableString(snapshot.getLastResponse()) + ",\n"
                + "  \"createdAt\": \"" + JSON_TIME_FORMAT.format(snapshot.getUpdatedAt()) + "\"\n"
                + "}\n";
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

    private String jobsJson() {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"jobs\": [");

        boolean first = true;
        for (PrintJob job : jobStore.findAll()) {
            if (!first) {
                json.append(",");
            }

            json.append("\n").append(indent(jobJson(job), 4));
            first = false;
        }

        if (!first) {
            json.append("\n  ");
        }

        json.append("]\n");
        json.append("}\n");
        return json.toString();
    }

    private String jobJson(PrintJob job) {
        return "{\n"
                + "  \"id\": " + nullableString(job.getId()) + ",\n"
                + "  \"name\": " + nullableString(job.getName()) + ",\n"
                + "  \"type\": \"" + job.getType() + "\",\n"
                + "  \"state\": \"" + job.getState() + "\",\n"
                + "  \"assignedPrinterId\": " + nullableString(job.getAssignedPrinterId()) + ",\n"
                + "  \"createdAt\": \"" + JSON_TIME_FORMAT.format(job.getCreatedAt()) + "\",\n"
                + "  \"updatedAt\": \"" + JSON_TIME_FORMAT.format(job.getUpdatedAt()) + "\"\n"
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

    private String requiredJsonString(String body, String fieldName) {
        String value = optionalJsonString(body, fieldName, null);

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }

        return value.trim();
    }

    private String optionalJsonString(String body, String fieldName, String fallback) {
        String pattern = "\"" + fieldName + "\"\\s*:\\s*\"";
        int start = body.indexOf(pattern);

        if (start < 0) {
            return fallback;
        }

        int valueStart = start + pattern.length();
        int valueEnd = body.indexOf('"', valueStart);

        if (valueEnd < 0) {
            return fallback;
        }

        return body.substring(valueStart, valueEnd);
    }

    private boolean optionalJsonBoolean(String body, String fieldName, boolean fallback) {
        String pattern = "\"" + fieldName + "\"\\s*:\\s*";
        int start = body.indexOf(pattern);

        if (start < 0) {
            return fallback;
        }

        int valueStart = start + pattern.length();
        String tail = body.substring(valueStart).trim();

        if (tail.startsWith("true")) {
            return true;
        }

        if (tail.startsWith("false")) {
            return false;
        }

        return fallback;
    }

    private double optionalJsonDouble(String body, String fieldName, double fallback) {
        String rawValue = optionalJsonRawNumber(body, fieldName);

        if (rawValue == null) {
            return fallback;
        }

        try {
            return Double.parseDouble(rawValue);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private long optionalJsonLong(String body, String fieldName, long fallback) {
        String rawValue = optionalJsonRawNumber(body, fieldName);

        if (rawValue == null) {
            return fallback;
        }

        try {
            return Long.parseLong(rawValue);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String optionalJsonRawNumber(String body, String fieldName) {
        String pattern = "\"" + fieldName + "\"\\s*:\\s*";
        int start = body.indexOf(pattern);

        if (start < 0) {
            return null;
        }

        int valueStart = start + pattern.length();
        int valueEnd = valueStart;

        while (valueEnd < body.length()) {
            char c = body.charAt(valueEnd);

            if ((c >= '0' && c <= '9') || c == '.' || c == '-') {
                valueEnd++;
            } else {
                break;
            }
        }

        if (valueEnd == valueStart) {
            return null;
        }

        return body.substring(valueStart, valueEnd);
    }

    private String indent(String value, int spaces) {
        String prefix = " ".repeat(spaces);
        return prefix + value.replace("\n", "\n" + prefix).stripTrailing();
    }

    private void handleDashboard(HttpExchange exchange) throws IOException {
        if (!isMethod(exchange, "GET")) {
            sendJson(exchange, 405, errorJson("Method not allowed"));
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

        sendJson(exchange, 404, errorJson("Dashboard resource not found"));
    }

    private void sendResource(
            HttpExchange exchange,
            String resourcePath,
            String contentType
    ) throws IOException {
        try (InputStream inputStream = RemoteApiServer.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                sendJson(exchange, 404, errorJson("Resource not found: " + resourcePath));
                return;
            }

            byte[] bytes = inputStream.readAllBytes();

            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", contentType);

            exchange.sendResponseHeaders(200, bytes.length);

            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(bytes);
            }
        }
    }
}