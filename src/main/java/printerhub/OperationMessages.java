package printerhub;

public final class OperationMessages {

    private OperationMessages() {
    }

    public static final String ERROR_PREFIX = "[ERROR] ";
    public static final String INFO_PREFIX = "[INFO] ";

    public static final String MONITORING_SCHEDULER_MUST_NOT_BE_NULL = "monitoringScheduler must not be null";
    public static final String MONITORING_RULES_STORE_MUST_NOT_BE_NULL = "monitoringRulesStore must not be null";

    public static final String FAILED_TO_SAVE_PRINTER_CONFIGURATION = "Failed to save printer configuration";
    public static final String FAILED_TO_LOAD_PRINTER_CONFIGURATION = "Failed to load printer configuration";
    public static final String FAILED_TO_CHECK_PRINTER_CONFIGURATION = "Failed to check printer configuration";
    public static final String FAILED_TO_DELETE_PRINTER_CONFIGURATION = "Failed to delete printer configuration";
    public static final String FAILED_TO_UPDATE_PRINTER_ENABLED_FLAG = "Failed to update printer enabled flag";

    public static final String DATABASE_INITIALIZER_MUST_NOT_BE_NULL = "databaseInitializer must not be null";
    public static final String API_SERVER_MUST_NOT_BE_NULL = "apiServer must not be null";

    public static final String NODE_MUST_NOT_BE_NULL = "node must not be null";
    public static final String STATE_CACHE_MUST_NOT_BE_NULL = "stateCache must not be null";
    public static final String SNAPSHOT_STORE_MUST_NOT_BE_NULL = "snapshotStore must not be null";
    public static final String EVENT_STORE_MUST_NOT_BE_NULL = "eventStore must not be null";
    public static final String CLOCK_MUST_NOT_BE_NULL = "clock must not be null";
    public static final String STATUS_COMMAND_MUST_NOT_BE_BLANK = "statusCommand must not be blank";
    public static final String PRINTER_REGISTRY_MUST_NOT_BE_NULL = "printerRegistry must not be null";
    public static final String PRINTER_CONFIGURATION_STORE_MUST_NOT_BE_NULL = "printerConfigurationStore must not be null";
    public static final String PRINTER_ID_MUST_NOT_BE_BLANK = "printerId must not be blank";
    public static final String SNAPSHOT_MUST_NOT_BE_NULL = "snapshot must not be null";
    public static final String PORT_MUST_BE_IN_VALID_RANGE = "port must be between 1 and 65535";
    public static final String PORT_NAME_MUST_NOT_BE_BLANK = "portName must not be blank";
    public static final String PORT_ADAPTER_MUST_NOT_BE_NULL = "portAdapter must not be null";
    public static final String COMMAND_MUST_NOT_BE_BLANK = "command must not be blank";
    public static final String PRINTER_EVENT_MUST_NOT_BE_NULL = "printer event must not be null";
    public static final String MONITORING_RULES_MUST_NOT_BE_NULL = "monitoringRules must not be null";
    public static final String INTERVAL_SECONDS_MUST_BE_GREATER_THAN_ZERO = "intervalSeconds must be greater than zero";

    public static final String POLL_INTERVAL_SECONDS_MUST_BE_GREATER_THAN_ZERO =
            "pollIntervalSeconds must be greater than zero";
    public static final String SNAPSHOT_MINIMUM_INTERVAL_SECONDS_MUST_NOT_BE_NEGATIVE =
            "snapshotMinimumIntervalSeconds must not be negative";
    public static final String TEMPERATURE_DELTA_THRESHOLD_MUST_NOT_BE_NEGATIVE =
            "temperatureDeltaThreshold must not be negative";
    public static final String EVENT_DEDUPLICATION_WINDOW_SECONDS_MUST_NOT_BE_NEGATIVE =
            "eventDeduplicationWindowSeconds must not be negative";
    public static final String ERROR_PERSISTENCE_BEHAVIOR_MUST_NOT_BE_NULL =
            "errorPersistenceBehavior must not be null";

    public static final String EVENT_PRINTER_POLLED = "PRINTER_POLLED";
    public static final String EVENT_PRINTER_DISABLED = "PRINTER_DISABLED";
    public static final String EVENT_PRINTER_TIMEOUT = "PRINTER_TIMEOUT";
    public static final String EVENT_PRINTER_DISCONNECTED = "PRINTER_DISCONNECTED";
    public static final String EVENT_PRINTER_ERROR = "PRINTER_ERROR";

    public static final String PRINTER_NODE_DISABLED = "Printer node is disabled.";
    public static final String PRINTER_POLL_COMPLETED_SUCCESSFULLY = "Printer poll completed successfully.";
    public static final String PRINTER_RETURNED_ERROR_RESPONSE = "Printer returned error response.";
    public static final String UNKNOWN_PRINTER_MONITORING_ERROR = "Unknown printer monitoring error.";
    public static final String UNKNOWN_API_ERROR = "Unexpected API error.";
    public static final String INTERNAL_SERVER_ERROR = "internal_server_error";

    public static final String METHOD_NOT_ALLOWED = "method_not_allowed";
    public static final String PRINTER_NOT_FOUND = "printer_not_found";
    public static final String PRINTER_ENDPOINT_NOT_FOUND = "printer_endpoint_not_found";
    public static final String DASHBOARD_RESOURCE_NOT_FOUND = "dashboard_resource_not_found";

    public static final String API_SERVER_STOPPED = "API server stopped";
    public static final String API_OPERATION_FAILED = "API operation failed";
    public static final String UNEXPECTED_API_ERROR = "Unexpected API error";

    public static final String FAILED_TO_SAVE_PRINTER_SNAPSHOT = "Failed to save printer snapshot";
    public static final String FAILED_TO_LOAD_PRINTER_SNAPSHOTS = "Failed to load printer snapshots";
    public static final String FAILED_TO_CHECK_LATEST_PRINTER_SNAPSHOT = "Failed to check latest printer snapshot";
    public static final String INVALID_STORED_PRINTER_SNAPSHOT_STATE = "Invalid stored printer snapshot state";
    public static final String INVALID_STORED_PRINTER_SNAPSHOT_TIMESTAMP = "Invalid stored printer snapshot timestamp";
    public static final String FAILED_TO_SAVE_PRINTER_EVENT = "Failed to save printer event";

    public static final String FAILED_TO_LOAD_MONITORING_RULES = "Failed to load monitoring rules";
    public static final String FAILED_TO_SAVE_MONITORING_RULES = "Failed to save monitoring rules";

    public static final String SERIAL_CONNECTION_IS_NOT_OPEN = "Serial port is not open.";
    public static final String INTERRUPTED_WHILE_READING_RESPONSE = "Interrupted while reading response from serial port.";

    public static final String PRINTER_PORT_MUST_NOT_BE_NULL = "printerPort must not be null";
    public static final String UNKNOWN_RUNTIME_CLOSE_ERROR = "Unknown runtime close error";

    public static final String UNKNOWN_STARTUP_ERROR = "Unknown startup error";
    public static final String UPDATED_AT_MUST_NOT_BE_NULL = "updatedAt must not be null";

    public static final String DATABASE_FILE_MUST_NOT_BE_BLANK = "database file must not be blank";
    public static final String FAILED_TO_INITIALIZE_DATABASE_SCHEMA = "Failed to initialize database schema";

    public static final String TEMPERATURE_THRESHOLD_MUST_NOT_BE_NEGATIVE = "temperatureThreshold must not be negative";
    public static final String MIN_INTERVAL_SECONDS_MUST_NOT_BE_NEGATIVE = "minIntervalSeconds must not be negative";

    public static final String INVALID_PRINTER_MODE = "mode must be one of: real, sim, simulated, sim-disconnected, sim-timeout, sim-error";

    public static final String SIMULATED_PRINTER_FAILURE_RESPONSE = "Error: Simulated printer failure";

    public static final String EVENT_TYPE_MUST_NOT_BE_BLANK = "eventType must not be blank";
    public static final String CREATED_AT_MUST_NOT_BE_NULL = "createdAt must not be null";
    public static final String SHUTDOWN_SIGNAL_MUST_NOT_BE_NULL = "shutdownSignal must not be null";

    public static final String DEDUP_WINDOW_MUST_NOT_BE_NULL = "dedupWindow must not be null";
    public static final String DEDUP_WINDOW_MUST_NOT_BE_NEGATIVE = "dedupWindow must not be negative";
    public static final String EVENT_MESSAGE_MUST_NOT_BE_BLANK = "eventMessage must not be blank";
    public static final String MONITORING_EVENT_POLICY_MUST_NOT_BE_NULL = "monitoringEventPolicy must not be null";

    public static String simulatedPrinterDisconnected(String portName) {
        return "Simulated printer is disconnected: " + portName;
    }

    public static String simulatedPrinterNotConnected(String portName) {
        return "Simulated printer is not connected: " + portName;
    }

    public static String databaseInitialized(String databaseFile) {
        return "[PrinterHub] Database initialized: " + databaseFile;
    }

    public static String failedToOpenDatabaseConnection(String databaseFile) {
        return "Failed to open database connection for file '" + databaseFile + "'";
    }

    public static String runtimeStartupFailed(String detail) {
        return "[PrinterHub] Runtime startup failed: " + detail;
    }

    public static String localRuntimeStarted() {
        return "[PrinterHub] Local runtime started";
    }

    public static String healthEndpoint(int apiPort) {
        return "[PrinterHub] Health:   http://localhost:" + apiPort + "/health";
    }

    public static String printersEndpoint(int apiPort) {
        return "[PrinterHub] Printers: http://localhost:" + apiPort + "/printers";
    }

    public static String monitoringSettingsEndpoint(int apiPort) {
        return "[PrinterHub] Settings: http://localhost:" + apiPort + "/settings/monitoring";
    }

    public static String invalidIntegerSystemProperty(String key, String value) {
        return "Invalid integer system property '" + key + "': " + value;
    }

    public static String invalidLongSystemProperty(String key, String value) {
        return "Invalid long system property '" + key + "': " + value;
    }

    public static String failedToDisconnectPrinterNode(String printerId, String detail) {
        return "[PrinterHub] Failed to disconnect printer node '" + printerId + "': " + detail;
    }

    public static String failedToCloseSerialPort(String portName, String detail) {
        return "[PrinterHub] Failed to close serial port '" + portName + "': " + detail;
    }

    public static String failedToCloseResource(String resourceName, String detail) {
        return "[PrinterHub] Failed to close " + resourceName + ": " + detail;
    }

    public static String noResponseForCommand(String command) {
        return "No response for command " + command;
    }

    public static String failedToPersistSnapshot(String printerId, String detail) {
        return "[PrinterHub] Failed to persist snapshot for " + printerId + ": " + detail;
    }

    public static String failedToPersistEvent(String printerId, String detail) {
        return "[PrinterHub] Failed to persist event for " + printerId + ": " + detail;
    }

    public static String failedToStartApiServer(int port) {
        return "Failed to start API server on port " + port;
    }

    public static String apiServerStarted(int port) {
        return "[PrinterHub] API server started on port " + port;
    }

    public static String apiServerStopped() {
        return "[PrinterHub] " + API_SERVER_STOPPED;
    }

    public static String apiOperationFailed(String detail) {
        return "[PrinterHub] " + API_OPERATION_FAILED + ": " + detail;
    }

    public static String unexpectedApiError(String detail) {
        return "[PrinterHub] " + UNEXPECTED_API_ERROR + ": " + detail;
    }

    public static String resourceNotFound(String resourcePath) {
        return "resource_not_found: " + resourcePath;
    }

    public static String fieldMustNotBeBlank(String fieldName) {
        return fieldName + " must not be blank";
    }

    public static String invalidEnumField(String fieldName, String value) {
        return "Invalid value for " + fieldName + ": " + value;
    }

    public static String failedToOpenSerialPort(String portName) {
        return "Failed to open serial port '" + portName + "'. "
                + "Possible causes: device path is wrong, permission is missing, "
                + "or the port is already in use.";
    }

    public static String failedToInitializeSerialStreams(String portName) {
        return "Failed to initialize serial streams for port: " + portName;
    }

    public static String failedToSendCommand(String command, String portName) {
        return "Failed to send command '" + command + "' to " + portName;
    }

    public static String noResponseForCommandOnPort(String command, String portName) {
        return "No response for command '" + command + "' on " + portName;
    }

    public static String interruptedWhileReadingResponse(String portName) {
        return "Interrupted while reading response from " + portName;
    }

    public static String noResponseWithinTimeout(int timeoutMs) {
        return "No response received from printer within " + timeoutMs + " ms.";
    }

    public static String invalidStoredPrinterSnapshotState(String storedState) {
        return INVALID_STORED_PRINTER_SNAPSHOT_STATE + ": " + storedState;
    }

    public static String invalidStoredPrinterSnapshotTimestamp(String storedTimestamp) {
        return INVALID_STORED_PRINTER_SNAPSHOT_TIMESTAMP + ": " + storedTimestamp;
    }

    public static String printerUpdateRestoreFailed(String printerId) {
        return "Printer update failed and previous runtime state could not be restored for " + printerId + ".";
    }

    public static String failedToRollbackPrinterRegistration(String printerId, String detail) {
        return "[PrinterHub] Failed to roll back printer registration for "
                + printerId + ": " + detail;
    }

    public static String failedToRestorePrinterAfterPut(String printerId, String detail) {
        return "[PrinterHub] Failed to restore printer after PUT failure for "
                + printerId + ": " + detail;
    }

    public static String failedToRestorePrinterAfterDelete(String printerId, String detail) {
        return "[PrinterHub] Failed to restore printer after DELETE failure for "
                + printerId + ": " + detail;
    }

    public static String invalidErrorPersistenceBehavior(String value) {
        return "Invalid errorPersistenceBehavior: " + value;
    }

    public static String safeDetail(String detail, String fallback) {
        if (detail == null || detail.isBlank()) {
            return fallback;
        }

        return detail;
    }
}