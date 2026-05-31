package spaghettichef.central.persistence;

import spaghettichef.central.service.CentralReplayFile;
import spaghettichef.central.service.CentralReplayPackage;
import spaghettichef.central.service.CentralReplayUploadRequest;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class CentralReplayPackageStore {
    public void insert(CentralReplayUploadRequest request) {
        try (Connection connection = CentralDatabase.getConnection()) {
            connection.setAutoCommit(false);
            try {
                insertPackage(connection, request.replayPackage());
                for (CentralReplayFile file : request.files()) {
                    insertFile(connection, file);
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to store central replay package", exception);
        }
    }

    public List<CentralReplayPackage> findByFarmId(String farmId) {
        return findPackages("SELECT * FROM central_replay_package WHERE farm_id = ? ORDER BY created_at DESC", farmId);
    }

    public CentralReplayPackage findByPackageId(String packageId) {
        List<CentralReplayPackage> packages = findPackages("SELECT * FROM central_replay_package WHERE package_id = ?",
                packageId);
        return packages.isEmpty() ? null : packages.get(0);
    }

    public List<CentralReplayFile> findFiles(String packageId) {
        try (Connection connection = CentralDatabase.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT * FROM central_replay_file WHERE package_id = ? ORDER BY relative_path")) {
            statement.setString(1, packageId);
            try (ResultSet resultSet = statement.executeQuery()) {
                ArrayList<CentralReplayFile> files = new ArrayList<>();
                while (resultSet.next()) {
                    files.add(mapFile(resultSet));
                }
                return files;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to list central replay files", exception);
        }
    }

    private void insertPackage(Connection connection, CentralReplayPackage replayPackage) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO central_replay_package (
                    package_id, farm_id, runtime_instance_id, camera_job_id, printer_id, camera_id,
                    label, started_at, finished_at, frame_count, delta_count, visibility,
                    manifest_json, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, replayPackage.packageId());
            statement.setString(2, replayPackage.farmId());
            statement.setString(3, replayPackage.runtimeInstanceId());
            statement.setString(4, replayPackage.cameraJobId());
            statement.setString(5, replayPackage.printerId());
            statement.setString(6, replayPackage.cameraId());
            statement.setString(7, replayPackage.label());
            statement.setString(8, replayPackage.startedAt());
            statement.setString(9, replayPackage.finishedAt());
            statement.setInt(10, replayPackage.frameCount());
            statement.setInt(11, replayPackage.deltaCount());
            statement.setString(12, replayPackage.visibility());
            statement.setString(13, replayPackage.manifestJson());
            statement.setString(14, replayPackage.createdAt().toString());
            statement.setString(15, replayPackage.updatedAt().toString());
            statement.executeUpdate();
        }
    }

    private void insertFile(Connection connection, CentralReplayFile file) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO central_replay_file (
                    package_id, file_type, relative_path, content_type, size_bytes, created_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, file.packageId());
            statement.setString(2, file.fileType());
            statement.setString(3, file.relativePath());
            statement.setString(4, file.contentType());
            statement.setLong(5, file.sizeBytes());
            statement.setString(6, file.createdAt().toString());
            statement.executeUpdate();
        }
    }

    private List<CentralReplayPackage> findPackages(String sql, String value) {
        try (Connection connection = CentralDatabase.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (ResultSet resultSet = statement.executeQuery()) {
                ArrayList<CentralReplayPackage> packages = new ArrayList<>();
                while (resultSet.next()) {
                    packages.add(mapPackage(resultSet));
                }
                return packages;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to list central replay packages", exception);
        }
    }

    private CentralReplayPackage mapPackage(ResultSet resultSet) throws SQLException {
        return new CentralReplayPackage(
                resultSet.getString("package_id"),
                resultSet.getString("farm_id"),
                resultSet.getString("runtime_instance_id"),
                resultSet.getString("camera_job_id"),
                resultSet.getString("printer_id"),
                resultSet.getString("camera_id"),
                resultSet.getString("label"),
                resultSet.getString("started_at"),
                resultSet.getString("finished_at"),
                resultSet.getInt("frame_count"),
                resultSet.getInt("delta_count"),
                resultSet.getString("visibility"),
                resultSet.getString("manifest_json"),
                java.time.Instant.parse(resultSet.getString("created_at")),
                java.time.Instant.parse(resultSet.getString("updated_at")));
    }

    private CentralReplayFile mapFile(ResultSet resultSet) throws SQLException {
        return new CentralReplayFile(
                resultSet.getString("package_id"),
                resultSet.getString("file_type"),
                resultSet.getString("relative_path"),
                resultSet.getString("content_type"),
                resultSet.getLong("size_bytes"),
                java.time.Instant.parse(resultSet.getString("created_at")));
    }
}
