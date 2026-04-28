package printerhub.persistence;

import printerhub.PrinterSnapshot;
import printerhub.PrinterState;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class PrinterSnapshotStore {

    private final MonitoringRules monitoringRules;

    public PrinterSnapshotStore() {
        this(MonitoringRules.defaults());
    }

    public PrinterSnapshotStore(MonitoringRules monitoringRules) {
        if (monitoringRules == null) {
            throw new IllegalArgumentException("monitoringRules must not be null");
        }

        this.monitoringRules = monitoringRules;
    }

    public void save(String printerId, PrinterSnapshot snapshot) {
        if (printerId == null || printerId.isBlank()) {
            throw new IllegalArgumentException("printerId must not be blank");
        }

        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
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
                    error_message,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?);
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, normalizedPrinterId);
            statement.setString(2, snapshot.state().name());
            statement.setObject(3, snapshot.hotendTemperature());
            statement.setObject(4, snapshot.bedTemperature());
            statement.setString(5, snapshot.lastResponse());
            statement.setString(6, snapshot.errorMessage());
            statement.setString(7, snapshot.updatedAt().toString());

            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save printer snapshot", exception);
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
                    error_message,
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
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load printer snapshots", exception);
        }
    }

    private boolean shouldStoreSnapshot(String printerId, PrinterSnapshot snapshot) {
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

                PrinterState lastState = PrinterState.valueOf(resultSet.getString("state"));
                Instant lastCreatedAt = Instant.parse(resultSet.getString("created_at"));

                if (monitoringRules.snapshotOnStateChange()
                        && lastState != snapshot.state()) {
                    return true;
                }

                if (temperatureDifferenceExceeded(
                        resultSet,
                        snapshot,
                        monitoringRules.temperatureThreshold()
                )) {
                    return true;
                }

                long secondsSinceLastSnapshot = Duration.between(
                        lastCreatedAt,
                        snapshot.updatedAt()
                ).toSeconds();

                return secondsSinceLastSnapshot >= monitoringRules.minIntervalSeconds();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to check latest printer snapshot", exception);
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

        Double lastHotend = readNullableDouble(resultSet, "hotend_temperature");
        Double lastBed = readNullableDouble(resultSet, "bed_temperature");

        return differenceExceeded(lastHotend, snapshot.hotendTemperature(), threshold)
                || differenceExceeded(lastBed, snapshot.bedTemperature(), threshold);
    }

    private Double readNullableDouble(ResultSet resultSet, String columnName) throws SQLException {
        double value = resultSet.getDouble(columnName);

        if (resultSet.wasNull()) {
            return null;
        }

        return value;
    }

    private boolean differenceExceeded(Double previous, Double current, double threshold) {
        if (previous == null || current == null) {
            return false;
        }

        return Math.abs(current - previous) >= threshold;
    }

    private PrinterSnapshot toSnapshot(ResultSet resultSet) throws SQLException {
        PrinterState state = PrinterState.valueOf(resultSet.getString("state"));
        Double hotendTemperature = readNullableDouble(resultSet, "hotend_temperature");
        Double bedTemperature = readNullableDouble(resultSet, "bed_temperature");
        String lastResponse = resultSet.getString("last_response");
        String errorMessage = resultSet.getString("error_message");
        Instant createdAt = Instant.parse(resultSet.getString("created_at"));

        if (state == PrinterState.ERROR || errorMessage != null) {
            return PrinterSnapshot.error(
                    state,
                    hotendTemperature,
                    bedTemperature,
                    lastResponse,
                    errorMessage,
                    createdAt
            );
        }

        return PrinterSnapshot.fromResponse(
                state,
                hotendTemperature,
                bedTemperature,
                lastResponse,
                createdAt
        );
    }
}