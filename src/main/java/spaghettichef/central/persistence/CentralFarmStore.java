package spaghettichef.central.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import spaghettichef.central.service.CentralFarm;

public final class CentralFarmStore {
    public void insert(CentralFarm farm) {
        String sql = """
                INSERT INTO central_farm (
                    farm_id, runtime_instance_id, farm_name, runtime_version, hostname, display_location,
                    description, farm_secret, enabled, registered_at, last_seen_at, printer_count, camera_count,
                    active_print_count, warning_count, error_count, spaghetti_alert_count, last_status_message,
                    last_summary_json, structure_json, structure_updated_at, created_at, updated_at, metadata_json
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        update(sql, farm);
    }

    public void updateRegistration(CentralFarm farm) {
        try (Connection connection = CentralDatabase.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        UPDATE central_farm
                        SET farm_name = ?, runtime_version = ?, hostname = ?, display_location = ?,
                            description = ?, updated_at = ?, metadata_json = ?
                        WHERE farm_id = ?
                        """)) {
            statement.setString(1, farm.farmName());
            statement.setString(2, farm.runtimeVersion());
            statement.setString(3, farm.hostname());
            statement.setString(4, farm.displayLocation());
            statement.setString(5, farm.description());
            statement.setString(6, farm.updatedAt().toString());
            statement.setString(7, farm.metadataJson());
            statement.setString(8, farm.farmId());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to update central farm registration", exception);
        }
    }

    public void updateHeartbeat(CentralFarm farm) {
        try (Connection connection = CentralDatabase.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        UPDATE central_farm
                        SET runtime_version = ?, last_seen_at = ?, printer_count = ?, camera_count = ?,
                            active_print_count = ?, warning_count = ?, error_count = ?, spaghetti_alert_count = ?,
                            last_status_message = ?, last_summary_json = ?, updated_at = ?
                        WHERE farm_id = ?
                        """)) {
            statement.setString(1, farm.runtimeVersion());
            statement.setString(2, farm.lastSeenAt().toString());
            statement.setInt(3, farm.printerCount());
            statement.setInt(4, farm.cameraCount());
            statement.setInt(5, farm.activePrintCount());
            statement.setInt(6, farm.warningCount());
            statement.setInt(7, farm.errorCount());
            statement.setInt(8, farm.spaghettiAlertCount());
            statement.setString(9, farm.lastStatusMessage());
            statement.setString(10, farm.lastSummaryJson());
            statement.setString(11, farm.updatedAt().toString());
            statement.setString(12, farm.farmId());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to update central farm heartbeat", exception);
        }
    }

    public void updateStructure(CentralFarm farm) {
        try (Connection connection = CentralDatabase.getConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        UPDATE central_farm
                        SET structure_json = ?, structure_updated_at = ?, updated_at = ?
                        WHERE farm_id = ?
                        """)) {
            statement.setString(1, farm.structureJson());
            statement.setString(2, farm.structureUpdatedAt().toString());
            statement.setString(3, farm.updatedAt().toString());
            statement.setString(4, farm.farmId());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to update central farm structure", exception);
        }
    }

    public CentralFarm findByRuntimeInstanceId(String runtimeInstanceId) {
        return findOne("SELECT * FROM central_farm WHERE runtime_instance_id = ?", runtimeInstanceId);
    }

    public CentralFarm findByFarmId(String farmId) {
        return findOne("SELECT * FROM central_farm WHERE farm_id = ?", farmId);
    }

    public List<CentralFarm> findAll() {
        try (Connection connection = CentralDatabase.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT * FROM central_farm ORDER BY farm_name COLLATE NOCASE, farm_id");
                ResultSet resultSet = statement.executeQuery()) {
            List<CentralFarm> farms = new ArrayList<>();
            while (resultSet.next()) {
                farms.add(map(resultSet));
            }
            return farms;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to list central farms", exception);
        }
    }

    private void update(String sql, CentralFarm farm) {
        try (Connection connection = CentralDatabase.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, farm.farmId());
            statement.setString(2, farm.runtimeInstanceId());
            statement.setString(3, farm.farmName());
            statement.setString(4, farm.runtimeVersion());
            statement.setString(5, farm.hostname());
            statement.setString(6, farm.displayLocation());
            statement.setString(7, farm.description());
            statement.setString(8, farm.farmSecret());
            statement.setInt(9, farm.enabled() ? 1 : 0);
            statement.setString(10, farm.registeredAt().toString());
            statement.setString(11, farm.lastSeenAt() == null ? null : farm.lastSeenAt().toString());
            statement.setInt(12, farm.printerCount());
            statement.setInt(13, farm.cameraCount());
            statement.setInt(14, farm.activePrintCount());
            statement.setInt(15, farm.warningCount());
            statement.setInt(16, farm.errorCount());
            statement.setInt(17, farm.spaghettiAlertCount());
            statement.setString(18, farm.lastStatusMessage());
            statement.setString(19, farm.lastSummaryJson());
            statement.setString(20, farm.structureJson());
            statement.setString(21, farm.structureUpdatedAt() == null ? null : farm.structureUpdatedAt().toString());
            statement.setString(22, farm.createdAt().toString());
            statement.setString(23, farm.updatedAt().toString());
            statement.setString(24, farm.metadataJson());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to store central farm", exception);
        }
    }

    private CentralFarm findOne(String sql, String value) {
        try (Connection connection = CentralDatabase.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return map(resultSet);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to load central farm", exception);
        }
    }

    private CentralFarm map(ResultSet resultSet) throws SQLException {
        return new CentralFarm(
                resultSet.getString("farm_id"),
                resultSet.getString("runtime_instance_id"),
                resultSet.getString("farm_name"),
                resultSet.getString("runtime_version"),
                resultSet.getString("hostname"),
                resultSet.getString("display_location"),
                resultSet.getString("description"),
                resultSet.getString("farm_secret"),
                resultSet.getInt("enabled") == 1,
                java.time.Instant.parse(resultSet.getString("registered_at")),
                parseInstant(resultSet.getString("last_seen_at")),
                resultSet.getInt("printer_count"),
                resultSet.getInt("camera_count"),
                resultSet.getInt("active_print_count"),
                resultSet.getInt("warning_count"),
                resultSet.getInt("error_count"),
                resultSet.getInt("spaghetti_alert_count"),
                resultSet.getString("last_status_message"),
                resultSet.getString("last_summary_json"),
                resultSet.getString("structure_json"),
                parseInstant(resultSet.getString("structure_updated_at")),
                java.time.Instant.parse(resultSet.getString("created_at")),
                java.time.Instant.parse(resultSet.getString("updated_at")),
                resultSet.getString("metadata_json"));
    }

    private java.time.Instant parseInstant(String value) {
        return value == null ? null : java.time.Instant.parse(value);
    }
}
