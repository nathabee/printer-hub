package printerhub.persistence;

import printerhub.PrinterSnapshot;
import printerhub.PrinterState;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite-backed store for printer state snapshots.
 */
public class PrinterSnapshotStore {

    private static final long DEFAULT_MIN_INTERVAL_SECONDS = 30;

    public void save(String printerId, PrinterSnapshot snapshot) {
        if (printerId == null || printerId.isBlank()) {
            throw new IllegalArgumentException("printerId must not be blank");
        }

        if (snapshot == null) {
            throw new IllegalArgumentException("printer snapshot must not be null");
        }

        String normalizedPrinterId = printerId.trim();

        if (!shouldStoreSnapshot(normalizedPrinterId, snapshot)) {
            return;
        }

        String sql = """
                INSERT INTO printer_snapshots (
                    printer_id,
                    state,
                    last_response,
                    created_at
                )
                VALUES (?, ?, ?, ?);
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {

            statement.setString(1, normalizedPrinterId);
            statement.setString(2, snapshot.getState().name());
            statement.setString(3, snapshot.getLastResponse());
            statement.setString(4, snapshot.getUpdatedAt().toString());

            statement.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to save printer snapshot", e);
        }
    }

    public List<PrinterSnapshot> findRecentByPrinterId(String printerId, int limit) {
        if (printerId == null || printerId.isBlank()) {
            throw new IllegalArgumentException("printerId must not be blank");
        }

        int safeLimit = limit <= 0 ? 20 : limit;

        String sql = """
                SELECT
                    state,
                    last_response,
                    created_at
                FROM printer_snapshots
                WHERE printer_id = ?
                ORDER BY id DESC
                LIMIT ?;
                """;

        List<PrinterSnapshot> snapshots = new ArrayList<>();

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {

            statement.setString(1, printerId.trim());
            statement.setInt(2, safeLimit);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    snapshots.add(toSnapshot(resultSet));
                }
            }

            return snapshots;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load printer snapshots", e);
        }
    }

    private boolean shouldStoreSnapshot(String printerId, PrinterSnapshot snapshot) {
        String sql = """
                SELECT
                    state,
                    created_at
                FROM printer_snapshots
                WHERE printer_id = ?
                ORDER BY id DESC
                LIMIT 1;
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {

            statement.setString(1, printerId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return true;
                }

                PrinterState lastState = PrinterState.valueOf(resultSet.getString("state"));
                LocalDateTime lastCreatedAt = LocalDateTime.parse(resultSet.getString("created_at"));

                if (lastState != snapshot.getState()) {
                    return true;
                }

                long secondsSinceLastSnapshot =
                        Duration.between(lastCreatedAt, snapshot.getUpdatedAt()).toSeconds();

                return secondsSinceLastSnapshot >= minimumSnapshotIntervalSeconds();
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to check latest printer snapshot", e);
        }
    }

    private long minimumSnapshotIntervalSeconds() {
        String configuredValue = System.getProperty("printerhub.snapshot.minIntervalSeconds");

        if (configuredValue == null || configuredValue.isBlank()) {
            return DEFAULT_MIN_INTERVAL_SECONDS;
        }

        try {
            return Math.max(0, Long.parseLong(configuredValue.trim()));
        } catch (NumberFormatException e) {
            return DEFAULT_MIN_INTERVAL_SECONDS;
        }
    }

    private PrinterSnapshot toSnapshot(ResultSet resultSet) throws SQLException {
        return new PrinterSnapshot(
                PrinterState.valueOf(resultSet.getString("state")),
                null,
                null,
                resultSet.getString("last_response"),
                LocalDateTime.parse(resultSet.getString("created_at"))
        );
    }
}