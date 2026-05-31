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
import spaghettichef.central.service.CentralReplayFile;
import spaghettichef.central.service.CentralReplayPackage;
import spaghettichef.central.service.CentralReplayPackageService;
import spaghettichef.central.service.CentralReplayUploadRequest;
import spaghettichef.central.service.FarmHeartbeatRequest;
import spaghettichef.central.service.FarmRegistrationRequest;
import spaghettichef.central.service.FarmStructureSnapshotRequest;
import spaghettichef.shared.config.RuntimeDefaults;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class CentralApiServer {
    public static final String REGISTRATION_TOKEN_PROPERTY = "spaghettichef.central.registrationToken";
    public static final String REGISTRATION_TOKEN_ENV = "CENTRAL_REGISTRATION_TOKEN";
    public static final String REGISTRATION_TOKEN_HEADER = "X-SpaghettiChef-Registration-Token";

    private final int port;
    private final CentralFarmService farmService;
    private final CentralReplayPackageService replayService;
    private final Path replayStorageDir;
    private final String registrationToken;
    private HttpServer server;

    public CentralApiServer(int port, CentralFarmService farmService) {
        this(port, farmService, null, Path.of("central-replay-storage"), null);
    }

    public CentralApiServer(int port, CentralFarmService farmService, String registrationToken) {
        this(port, farmService, null, Path.of("central-replay-storage"), registrationToken);
    }

    public CentralApiServer(
            int port,
            CentralFarmService farmService,
            CentralReplayPackageService replayService,
            Path replayStorageDir,
            String registrationToken) {
        if (port < RuntimeDefaults.MIN_PORT || port > RuntimeDefaults.MAX_PORT) {
            throw new IllegalArgumentException(OperationMessages.PORT_MUST_BE_IN_VALID_RANGE);
        }
        this.port = port;
        this.farmService = farmService;
        this.replayService = replayService;
        this.replayStorageDir = replayStorageDir;
        this.registrationToken = blankToNull(registrationToken);
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
            server.createContext("/api/central/camera-replay-packages",
                    exchange -> safeHandle(exchange, this::handleReplayPackage));
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

        Matcher structure = Pattern.compile("^/api/central/farms/([^/]+)/structure$").matcher(path);
        if (structure.matches()) {
            handleStructure(exchange, structure.group(1));
            return;
        }

        Matcher farmReplayPackages = Pattern.compile("^/api/central/farms/([^/]+)/camera-replay-packages$")
                .matcher(path);
        if (farmReplayPackages.matches()) {
            handleFarmReplayPackages(exchange, farmReplayPackages.group(1));
            return;
        }

        Matcher farm = Pattern.compile("^/api/central/farms/([^/]+)$").matcher(path);
        if (farm.matches()) {
            handleGetFarm(exchange, farm.group(1));
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
        validateRegistrationToken(exchange);
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

    private void handleStructure(HttpExchange exchange, String farmId) throws IOException {
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            handleStructurePush(exchange, farmId);
            return;
        }
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            handleGetStructure(exchange, farmId);
            return;
        }
        sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
    }

    private void handleStructurePush(HttpExchange exchange, String farmId) throws IOException {
        String body = readBody(exchange);
        Map<String, Object> request = CentralJson.parseObject(body);
        CentralFarm farm = farmService.updateStructure(new FarmStructureSnapshotRequest(
                farmId,
                requiredJsonString(request, "runtimeInstanceId"),
                requiredJsonString(request, "farmSecret"),
                publicStructureJson(request)));
        sendJson(exchange, 200, "{\"accepted\":true,\"structureUpdatedAt\":"
                + nullableString(farm.structureUpdatedAt().toString()) + "}");
    }

    private void handleFarmReplayPackages(HttpExchange exchange, String farmId) throws IOException {
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            handleReplayUpload(exchange, farmId);
            return;
        }
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            handleReplayList(exchange, farmId);
            return;
        }
        sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
    }

    private void handleReplayUpload(HttpExchange exchange, String farmId) throws IOException {
        ensureReplayService();
        byte[] zipBytes = exchange.getRequestBody().readAllBytes();
        ExtractedReplayZip extracted = extractReplayZip(zipBytes);
        Map<String, Object> manifest = CentralJson.parseObject(extracted.manifestJson());
        String manifestFarmId = requiredJsonString(manifest, "farmId");
        if (!farmId.equals(manifestFarmId)) {
            throw new CentralFarmService.FarmRejectedException("farm_id_mismatch");
        }
        CentralReplayUploadRequest upload = replayService.createUpload(
                farmId,
                requiredJsonString(manifest, "runtimeInstanceId"),
                requiredReplaySecret(exchange, manifest),
                CentralJson.stringField(manifest, "cameraJobId"),
                CentralJson.stringField(manifest, "printerId"),
                CentralJson.stringField(manifest, "cameraId"),
                CentralJson.stringField(manifest, "label"),
                CentralJson.stringField(manifest, "startedAt"),
                CentralJson.stringField(manifest, "finishedAt"),
                optionalJsonInteger(manifest, "frameCount", 0),
                optionalJsonInteger(manifest, "deltaCount", 0),
                CentralJson.stringField(manifest, "visibility"),
                CentralJson.stringify(publicReplayManifest(manifest)),
                extracted.files());
        Path packageDir = replayStorageDir.resolve(upload.replayPackage().packageId()).normalize();
        if (!packageDir.startsWith(replayStorageDir.normalize())) {
            throw new IllegalArgumentException("invalid replay package path");
        }
        Files.createDirectories(packageDir);
        for (ExtractedReplayFile file : extracted.extractedFiles()) {
            Path destination = packageDir.resolve(file.relativePath()).normalize();
            if (!destination.startsWith(packageDir)) {
                throw new IllegalArgumentException("invalid replay file path");
            }
            Files.createDirectories(destination.getParent());
            Files.write(destination, file.bytes());
        }
        replayService.store(upload);
        sendJson(exchange, 201, "{\"accepted\":true,\"package\":" + replayPackageJson(upload.replayPackage(), false)
                + "}");
    }

    private void handleReplayList(HttpExchange exchange, String farmId) throws IOException {
        ensureReplayService();
        List<CentralReplayPackage> packages = replayService.listForFarm(farmId);
        StringBuilder json = new StringBuilder("{\"packages\":[");
        for (int index = 0; index < packages.size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            json.append(replayPackageJson(packages.get(index), false));
        }
        json.append("]}");
        sendJson(exchange, 200, json.toString());
    }

    private void handleReplayPackage(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        Matcher file = Pattern.compile("^/api/central/camera-replay-packages/([^/]+)/files/(.+)$").matcher(path);
        if (file.matches()) {
            handleReplayFile(exchange, file.group(1), file.group(2));
            return;
        }
        Matcher detail = Pattern.compile("^/api/central/camera-replay-packages/([^/]+)$").matcher(path);
        if (detail.matches()) {
            handleReplayDetail(exchange, detail.group(1));
            return;
        }
        sendJson(exchange, 404, errorJson(OperationMessages.resourceNotFound(path)));
    }

    private void handleReplayDetail(HttpExchange exchange, String packageId) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
            return;
        }
        ensureReplayService();
        CentralReplayPackage replayPackage = replayService.getPackage(packageId);
        List<CentralReplayFile> files = replayService.listFiles(packageId);
        StringBuilder json = new StringBuilder("{\"package\":");
        json.append(replayPackageJson(replayPackage, true)).append(",\"files\":[");
        for (int index = 0; index < files.size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            json.append(replayFileJson(files.get(index), true));
        }
        json.append("]}");
        sendJson(exchange, 200, json.toString());
    }

    private void handleReplayFile(HttpExchange exchange, String packageId, String relativePath) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
            return;
        }
        ensureReplayService();
        replayService.getPackage(packageId);
        String safeRelativePath = safeReplayRelativePath(relativePath);
        Path file = replayStorageDir.resolve(packageId).resolve(safeRelativePath).normalize();
        if (!file.startsWith(replayStorageDir.resolve(packageId).normalize()) || !Files.isRegularFile(file)) {
            sendJson(exchange, 404, errorJson(OperationMessages.resourceNotFound(relativePath)));
            return;
        }
        sendBytes(exchange, 200, Files.readAllBytes(file), contentType(safeRelativePath));
    }

    private void handleGetStructure(HttpExchange exchange, String farmId) throws IOException {
        CentralFarmOverview overview = farmService.getFarm(farmId);
        CentralFarm farm = overview.farm();
        sendJson(exchange, 200, "{"
                + "\"farmId\":" + nullableString(farm.farmId()) + ","
                + "\"runtimeInstanceId\":" + nullableString(farm.runtimeInstanceId()) + ","
                + "\"structureUpdatedAt\":" + nullableString(
                        farm.structureUpdatedAt() == null ? null : farm.structureUpdatedAt().toString()) + ","
                + "\"structure\":" + (farm.structureJson() == null ? "null" : farm.structureJson())
                + "}");
    }

    private void handleGetFarm(HttpExchange exchange, String farmId) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, errorJson(OperationMessages.METHOD_NOT_ALLOWED));
            return;
        }
        CentralFarmOverview overview = farmService.getFarm(farmId);
        sendJson(exchange, 200, "{\"status\":\"" + escapeJson(overview.status()) + "\",\"farm\":"
                + farmJson(overview.farm(), false) + "}");
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

    private String publicStructureJson(Map<String, Object> request) {
        LinkedHashMap<String, Object> structure = new LinkedHashMap<>();
        structure.put("generatedAt", request.get("generatedAt"));
        structure.put("printers", arrayField(request, "printers"));
        structure.put("cameras", arrayField(request, "cameras"));
        return CentralJson.stringify(structure);
    }

    private ExtractedReplayZip extractReplayZip(byte[] zipBytes) throws IOException {
        ArrayList<ExtractedReplayFile> extractedFiles = new ArrayList<>();
        ArrayList<CentralReplayPackageService.UploadedReplayFile> metadataFiles = new ArrayList<>();
        String manifestJson = null;
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String relativePath = safeReplayRelativePath(entry.getName());
                byte[] bytes = zipInputStream.readAllBytes();
                if ("manifest.json".equals(relativePath)) {
                    manifestJson = new String(bytes, StandardCharsets.UTF_8);
                }
                extractedFiles.add(new ExtractedReplayFile(relativePath, bytes));
                metadataFiles.add(new CentralReplayPackageService.UploadedReplayFile(relativePath, bytes.length));
            }
        }
        if (manifestJson == null || manifestJson.isBlank()) {
            throw new IllegalArgumentException("replay package must contain manifest.json");
        }
        return new ExtractedReplayZip(manifestJson, extractedFiles, metadataFiles);
    }

    private String safeReplayRelativePath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("replay file path must not be blank");
        }
        String normalized = path.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        Path normalizedPath = Path.of(normalized).normalize();
        String result = normalizedPath.toString().replace('\\', '/');
        if (result.isBlank() || result.startsWith("../") || result.equals("..") || result.contains("/../")) {
            throw new IllegalArgumentException("invalid replay file path");
        }
        return result;
    }

    private String requiredReplaySecret(HttpExchange exchange, Map<String, Object> manifest) {
        String headerSecret = blankToNull(exchange.getRequestHeaders().getFirst("X-SpaghettiChef-Farm-Secret"));
        if (headerSecret != null) {
            return headerSecret;
        }
        return requiredJsonString(manifest, "farmSecret");
    }

    private Map<String, Object> publicReplayManifest(Map<String, Object> manifest) {
        LinkedHashMap<String, Object> publicManifest = new LinkedHashMap<>(manifest);
        publicManifest.remove("farmSecret");
        return publicManifest;
    }

    private String replayPackageJson(CentralReplayPackage replayPackage, boolean includeManifest) {
        return "{"
                + "\"packageId\":" + nullableString(replayPackage.packageId()) + ","
                + "\"farmId\":" + nullableString(replayPackage.farmId()) + ","
                + "\"runtimeInstanceId\":" + nullableString(replayPackage.runtimeInstanceId()) + ","
                + "\"cameraJobId\":" + nullableString(replayPackage.cameraJobId()) + ","
                + "\"printerId\":" + nullableString(replayPackage.printerId()) + ","
                + "\"cameraId\":" + nullableString(replayPackage.cameraId()) + ","
                + "\"label\":" + nullableString(replayPackage.label()) + ","
                + "\"startedAt\":" + nullableString(replayPackage.startedAt()) + ","
                + "\"finishedAt\":" + nullableString(replayPackage.finishedAt()) + ","
                + "\"frameCount\":" + replayPackage.frameCount() + ","
                + "\"deltaCount\":" + replayPackage.deltaCount() + ","
                + "\"visibility\":" + nullableString(replayPackage.visibility()) + ","
                + "\"createdAt\":" + nullableString(replayPackage.createdAt().toString())
                + (includeManifest ? ",\"manifest\":" + replayPackage.manifestJson() : "")
                + "}";
    }

    private String replayFileJson(CentralReplayFile file, boolean includeUrl) {
        return "{"
                + "\"fileType\":" + nullableString(file.fileType()) + ","
                + "\"relativePath\":" + nullableString(file.relativePath()) + ","
                + "\"contentType\":" + nullableString(file.contentType()) + ","
                + "\"sizeBytes\":" + file.sizeBytes()
                + (includeUrl ? ",\"url\":" + nullableString("/api/central/camera-replay-packages/"
                        + file.packageId() + "/files/" + file.relativePath()) : "")
                + "}";
    }

    private int optionalJsonInteger(Map<String, Object> object, String fieldName, int fallback) {
        Object value = object.get(fieldName);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw new IllegalArgumentException(fieldName + " must be a number");
    }

    private String contentType(String relativePath) {
        String lower = relativePath.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".json")) {
            return "application/json; charset=utf-8";
        }
        return "application/octet-stream";
    }

    private void ensureReplayService() {
        if (replayService == null) {
            throw new IllegalStateException("central replay service is not configured");
        }
    }

    private List<?> arrayField(Map<String, Object> object, String fieldName) {
        Object value = object.get(fieldName);
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException(fieldName + " must be an array");
        }
        return list;
    }

    private void validateRegistrationToken(HttpExchange exchange) {
        if (registrationToken == null) {
            return;
        }
        String provided = blankToNull(exchange.getRequestHeaders().getFirst(REGISTRATION_TOKEN_HEADER));
        if (provided == null) {
            String authorization = blankToNull(exchange.getRequestHeaders().getFirst("Authorization"));
            if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
                provided = blankToNull(authorization.substring(7));
            }
        }
        if (!registrationToken.equals(provided)) {
            throw new CentralFarmService.FarmRejectedException("registration_token_mismatch");
        }
    }

    private String requiredJsonString(String body, String fieldName) {
        String value = optionalJsonString(body, fieldName, null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private String requiredJsonString(Map<String, Object> object, String fieldName) {
        String value = CentralJson.stringField(object, fieldName);
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

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record ExtractedReplayZip(
            String manifestJson,
            List<ExtractedReplayFile> extractedFiles,
            List<CentralReplayPackageService.UploadedReplayFile> files) {
    }

    private record ExtractedReplayFile(String relativePath, byte[] bytes) {
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
