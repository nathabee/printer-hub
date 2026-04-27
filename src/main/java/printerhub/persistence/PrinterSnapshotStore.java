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

    private final RuntimeConfigurationStore configurationStore;

    public PrinterSnapshotStore() {
        this(new RuntimeConfigurationStore());
    }

    public PrinterSnapshotStore(RuntimeConfigurationStore configurationStore) {
        if (configurationStore == null) {
            throw new IllegalArgumentException("configurationStore must not be null");
        }

        this.configurationStore = configurationStore;
    }

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
                    hotend_temperature,
                    bed_temperature,
                    last_response,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?, ?);
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, normalizedPrinterId);
            statement.setString(2, snapshot.getState().name());
            statement.setObject(3, snapshot.getHotendTemperature());
            statement.setObject(4, snapshot.getBedTemperature());
            statement.setString(5, snapshot.getLastResponse());
            statement.setString(6, snapshot.getUpdatedAt().toString());

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
                    hotend_temperature,
                    bed_temperature,
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
        MonitoringRules rules = configurationStore.loadMonitoringRules();

        String sql = """
                SELECT
                    state,
                    hotend_temperature,
                    bed_temperature,
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

                PrinterState lastState =
                        PrinterState.valueOf(resultSet.getString("state"));

                LocalDateTime lastCreatedAt =
                        LocalDateTime.parse(resultSet.getString("created_at"));

                if (rules.isSnapshotOnStateChange()
                        && lastState != snapshot.getState()) {
                    return true;
                }

                if (temperatureDifferenceExceeded(
                        resultSet,
                        snapshot,
                        rules.getTemperatureThreshold()
                )) {
                    return true;
                }

                long secondsSinceLastSnapshot =
                        Duration.between(
                                lastCreatedAt,
                                snapshot.getUpdatedAt()
                        ).toSeconds();

                return secondsSinceLastSnapshot >= rules.getMinIntervalSeconds();
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to check latest printer snapshot", e);
        }
    }

    private boolean temperatureDifferenceExceeded(
            ResultSet resultSet,
            PrinterSnapshot snapshot,
            double threshold
    ) throws SQLException {

        if (threshold <= 0) {
            return false;
        }

        Double lastHotend =
                readNullableDouble(resultSet, "hotend_temperature");

        Double lastBed =
                readNullableDouble(resultSet, "bed_temperature");

        return differenceExceeded(
                lastHotend,
                snapshot.getHotendTemperature(),
                threshold
        ) || differenceExceeded(
                lastBed,
                snapshot.getBedTemperature(),
                threshold
        );
    }

    private Double readNullableDouble(
            ResultSet resultSet,
            String columnName
    ) throws SQLException {

        double value = resultSet.getDouble(columnName);

        if (resultSet.wasNull()) {
            return null;
        }

        return value;
    }

    private boolean differenceExceeded(
            Double previous,
            Double current,
            double threshold
    ) {
        if (previous == null || current == null) {
            return false;
        }

        return Math.abs(current - previous) >= threshold;
    }

    private PrinterSnapshot toSnapshot(ResultSet resultSet) throws SQLException {
        return new PrinterSnapshot(
                PrinterState.valueOf(resultSet.getString("state")),
                readNullableDouble(resultSet, "hotend_temperature"),
                readNullableDouble(resultSet, "bed_temperature"),
                resultSet.getString("last_response"),
                LocalDateTime.parse(resultSet.getString("created_at"))
        );
    }
}