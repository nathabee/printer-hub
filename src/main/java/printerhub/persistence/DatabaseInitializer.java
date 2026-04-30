package printerhub.persistence;

import printerhub.OperationMessages;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseInitializer {

    public void initialize() {
        try (
                Connection connection = Database.getConnection();
                Statement statement = connection.createStatement()
        ) {
            createPrintJobsTable(statement);
            createPrinterSnapshotsTable(statement);
            createPrinterEventsTable(statement);
            createConfiguredPrintersTable(statement);
            createMonitoringRulesTable(statement);
            migrateMonitoringRulesTable(statement);

            System.out.println(OperationMessages.databaseInitialized(DatabaseConfig.databaseFile()));
        } catch (SQLException exception) {
            throw new IllegalStateException(OperationMessages.FAILED_TO_INITIALIZE_DATABASE_SCHEMA, exception);
        }
    }

    private void createPrintJobsTable(Statement statement) throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS print_jobs (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    type TEXT NOT NULL,
                    state TEXT NOT NULL,
                    printer_id TEXT,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                );
                """;

        statement.execute(sql);
    }

    private void createPrinterSnapshotsTable(Statement statement) throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS printer_snapshots (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    printer_id TEXT NOT NULL,
                    state TEXT NOT NULL,
                    hotend_temperature REAL,
                    bed_temperature REAL,
                    last_response TEXT,
                    error_message TEXT,
                    created_at TEXT NOT NULL
                );
                """;

        statement.execute(sql);
    }

    private void createPrinterEventsTable(Statement statement) throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS printer_events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    printer_id TEXT,
                    job_id TEXT,
                    event_type TEXT NOT NULL,
                    message TEXT,
                    created_at TEXT NOT NULL
                );
                """;

        statement.execute(sql);
    }

    private void createConfiguredPrintersTable(Statement statement) throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS configured_printers (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    port_name TEXT NOT NULL,
                    mode TEXT NOT NULL,
                    enabled INTEGER NOT NULL DEFAULT 1,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                );
                """;

        statement.execute(sql);
    }

    private void createMonitoringRulesTable(Statement statement) throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS monitoring_rules (
                    id TEXT PRIMARY KEY,
                    snapshot_on_state_change INTEGER NOT NULL DEFAULT 1,
                    temperature_threshold REAL NOT NULL,
                    min_interval_seconds INTEGER NOT NULL,
                    poll_interval_seconds INTEGER NOT NULL DEFAULT 5,
                    event_dedup_window_seconds INTEGER NOT NULL DEFAULT 60,
                    error_persistence_behavior TEXT NOT NULL DEFAULT 'DEDUPLICATED',
                    updated_at TEXT NOT NULL
                );
                """;

        statement.execute(sql);
    }

    private void migrateMonitoringRulesTable(Statement statement) throws SQLException {
        addColumnIfMissing(
                statement,
                "ALTER TABLE monitoring_rules ADD COLUMN poll_interval_seconds INTEGER NOT NULL DEFAULT 5;"
        );
        addColumnIfMissing(
                statement,
                "ALTER TABLE monitoring_rules ADD COLUMN event_dedup_window_seconds INTEGER NOT NULL DEFAULT 60;"
        );
        addColumnIfMissing(
                statement,
                "ALTER TABLE monitoring_rules ADD COLUMN error_persistence_behavior TEXT NOT NULL DEFAULT 'DEDUPLICATED';"
        );
    }

    private void addColumnIfMissing(Statement statement, String sql) throws SQLException {
        try {
            statement.execute(sql);
        } catch (SQLException exception) {
            String message = exception.getMessage();

            if (message != null && message.toLowerCase().contains("duplicate column name")) {
                return;
            }

            throw exception;
        }
    }
}