package spaghettichef.central.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class CentralDatabaseInitializer {
    public void initialize() {
        try (Connection connection = CentralDatabase.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS central_farm (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        farm_id TEXT NOT NULL UNIQUE,
                        runtime_instance_id TEXT NOT NULL UNIQUE,
                        farm_name TEXT NOT NULL,
                        runtime_version TEXT,
                        hostname TEXT,
                        display_location TEXT,
                        description TEXT,
                        farm_secret TEXT NOT NULL,
                        enabled INTEGER NOT NULL DEFAULT 1,
                        registered_at TEXT NOT NULL,
                        last_seen_at TEXT,
                        printer_count INTEGER NOT NULL DEFAULT 0,
                        camera_count INTEGER NOT NULL DEFAULT 0,
                        active_print_count INTEGER NOT NULL DEFAULT 0,
                        warning_count INTEGER NOT NULL DEFAULT 0,
                        error_count INTEGER NOT NULL DEFAULT 0,
                        spaghetti_alert_count INTEGER NOT NULL DEFAULT 0,
                        last_status_message TEXT,
                        last_summary_json TEXT,
                        structure_json TEXT,
                        structure_updated_at TEXT,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL,
                        metadata_json TEXT
                    );
                    """);
            addColumnIfMissing(statement, "structure_json TEXT");
            addColumnIfMissing(statement, "structure_updated_at TEXT");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS central_replay_package (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        package_id TEXT NOT NULL UNIQUE,
                        farm_id TEXT NOT NULL,
                        runtime_instance_id TEXT NOT NULL,
                        camera_job_id TEXT,
                        printer_id TEXT,
                        camera_id TEXT,
                        label TEXT,
                        started_at TEXT,
                        finished_at TEXT,
                        frame_count INTEGER NOT NULL DEFAULT 0,
                        delta_count INTEGER NOT NULL DEFAULT 0,
                        visibility TEXT,
                        manifest_json TEXT NOT NULL,
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    );
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS central_replay_file (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        package_id TEXT NOT NULL,
                        file_type TEXT NOT NULL,
                        relative_path TEXT NOT NULL,
                        content_type TEXT,
                        size_bytes INTEGER NOT NULL DEFAULT 0,
                        created_at TEXT NOT NULL
                    );
                    """);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to initialize central database schema", exception);
        }
    }

    private void addColumnIfMissing(Statement statement, String columnDefinition) throws SQLException {
        try {
            statement.execute("ALTER TABLE central_farm ADD COLUMN " + columnDefinition);
        } catch (SQLException exception) {
            if (!exception.getMessage().toLowerCase(java.util.Locale.ROOT).contains("duplicate column name")) {
                throw exception;
            }
        }
    }
}
