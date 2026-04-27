package printerhub.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates required database tables if they do not exist.
 */
public final class DatabaseInitializer {

    private DatabaseInitializer() {
        // utility class
    }

    /**
     * Initializes database schema.
     */
    public static void initialize() {

        try (
                Connection connection =
                        Database.getConnection();

                Statement statement =
                        connection.createStatement()
        ) {

            createPrintJobsTable(statement);
            createPrinterSnapshotsTable(statement);
            createPrinterEventsTable(statement);

        } catch (SQLException e) {

            throw new RuntimeException(
                    "Failed to initialize database schema",
                    e
            );

        }

    }

    private static void createPrintJobsTable(
            Statement statement
    ) throws SQLException {

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

    private static void createPrinterSnapshotsTable(
            Statement statement
    ) throws SQLException {

        String sql = """
                CREATE TABLE IF NOT EXISTS printer_snapshots (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    printer_id TEXT NOT NULL,
                    state TEXT NOT NULL,
                    last_response TEXT,
                    created_at TEXT NOT NULL
                );
                """;

        statement.execute(sql);
    }

    private static void createPrinterEventsTable(
            Statement statement
    ) throws SQLException {

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

}