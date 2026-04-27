package printerhub.persistence;

import printerhub.PrinterSnapshot;
import printerhub.PrinterState;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite-backed store for printer state snapshots.
 */
public class PrinterSnapshotStore {

    public void save(String printerId, PrinterSnapshot snapshot) {
        if (printerId == null || printerId.isBlank()) {
            throw new IllegalArgumentException("printerId must not be blank");
        }

        if (snapshot == null) {
            throw new IllegalArgumentException("printer snapshot must not be null");
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

            statement.setString(1, printerId.trim());
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