package spaghettichef.local.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class CameraDeltaSetStore {

    public CameraDeltaSet save(CameraDeltaSet deltaSet) {
        if (deltaSet == null) {
            throw new IllegalArgumentException("camera delta set must not be null");
        }

        String sql = """
                INSERT INTO camera_delta_sets (
                    id,
                    printer_id,
                    camera_job_id,
                    method_name,
                    delta_snapshot_step,
                    source_snapshot_count,
                    generated_delta_count,
                    created_at,
                    message
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            long id = nextId(connection, deltaSet.printerId());
            CameraDeltaSet saved = deltaSet.withId(id);
            statement.setLong(1, saved.requireId());
            statement.setString(2, saved.printerId());
            statement.setLong(3, saved.cameraJobId());
            statement.setString(4, saved.methodName());
            statement.setInt(5, saved.deltaSnapshotStep());
            statement.setInt(6, saved.sourceSnapshotCount());
            statement.setInt(7, saved.generatedDeltaCount());
            statement.setString(8, saved.createdAt().toString());
            statement.setString(9, saved.message());
            statement.executeUpdate();
            return saved;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save camera delta set", exception);
        }
    }

    public Optional<CameraDeltaSet> findById(long id) {
        String sql = selectColumns() + " FROM camera_delta_sets WHERE id = ?;";

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setLong(1, requirePositive(id, "id"));

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(mapRow(resultSet));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load camera delta set", exception);
        }
    }

    public Optional<CameraDeltaSet> findByPrinterIdAndId(String printerId, long id) {
        String sql = selectColumns() + " FROM camera_delta_sets WHERE printer_id = ? AND id = ?;";

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, requireText(printerId, "printerId"));
            statement.setLong(2, requirePositive(id, "id"));

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(mapRow(resultSet));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load camera delta set", exception);
        }
    }

    public List<CameraDeltaSet> findByCameraJobId(long cameraJobId) {
        String sql = selectColumns() + """
                FROM camera_delta_sets
                WHERE camera_job_id = ?
                ORDER BY created_at DESC, id DESC;
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setLong(1, requirePositive(cameraJobId, "cameraJobId"));

            try (ResultSet resultSet = statement.executeQuery()) {
                List<CameraDeltaSet> deltaSets = new ArrayList<>();
                while (resultSet.next()) {
                    deltaSets.add(mapRow(resultSet));
                }
                return deltaSets;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load camera delta sets", exception);
        }
    }

    public List<CameraDeltaSet> findByPrinterIdAndCameraJobId(String printerId, long cameraJobId) {
        String sql = selectColumns() + """
                FROM camera_delta_sets
                WHERE printer_id = ?
                    AND camera_job_id = ?
                ORDER BY created_at DESC, id DESC;
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, requireText(printerId, "printerId"));
            statement.setLong(2, requirePositive(cameraJobId, "cameraJobId"));

            try (ResultSet resultSet = statement.executeQuery()) {
                List<CameraDeltaSet> deltaSets = new ArrayList<>();
                while (resultSet.next()) {
                    deltaSets.add(mapRow(resultSet));
                }
                return deltaSets;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load camera delta sets", exception);
        }
    }

    public int deleteByCameraJobId(long cameraJobId) {
        String sql = "DELETE FROM camera_delta_sets WHERE camera_job_id = ?;";

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setLong(1, requirePositive(cameraJobId, "cameraJobId"));
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to delete camera delta sets", exception);
        }
    }

    public int deleteByPrinterIdAndCameraJobId(String printerId, long cameraJobId) {
        String sql = "DELETE FROM camera_delta_sets WHERE printer_id = ? AND camera_job_id = ?;";

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, requireText(printerId, "printerId"));
            statement.setLong(2, requirePositive(cameraJobId, "cameraJobId"));
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to delete camera delta sets", exception);
        }
    }

    public int deleteById(long id) {
        String sql = "DELETE FROM camera_delta_sets WHERE id = ?;";

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setLong(1, requirePositive(id, "id"));
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to delete camera delta set", exception);
        }
    }

    public int deleteByPrinterIdAndId(String printerId, long id) {
        String sql = "DELETE FROM camera_delta_sets WHERE printer_id = ? AND id = ?;";

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, requireText(printerId, "printerId"));
            statement.setLong(2, requirePositive(id, "id"));
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to delete camera delta set", exception);
        }
    }

    public CameraDeltaSet updateGeneratedDeltaCount(long id, int generatedDeltaCount) {
        if (generatedDeltaCount < 0) {
            throw new IllegalArgumentException("generatedDeltaCount must not be negative");
        }

        String sql = "UPDATE camera_delta_sets SET generated_delta_count = ? WHERE id = ?;";

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setInt(1, generatedDeltaCount);
            statement.setLong(2, requirePositive(id, "id"));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to update camera delta set", exception);
        }

        return findById(id)
                .orElseThrow(() -> new IllegalArgumentException("camera delta set not found: " + id));
    }

    public CameraDeltaSet updateGeneratedDeltaCount(String printerId, long id, int generatedDeltaCount) {
        if (generatedDeltaCount < 0) {
            throw new IllegalArgumentException("generatedDeltaCount must not be negative");
        }

        String sql = "UPDATE camera_delta_sets SET generated_delta_count = ? WHERE printer_id = ? AND id = ?;";

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setInt(1, generatedDeltaCount);
            statement.setString(2, requireText(printerId, "printerId"));
            statement.setLong(3, requirePositive(id, "id"));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to update camera delta set", exception);
        }

        return findByPrinterIdAndId(printerId, id)
                .orElseThrow(() -> new IllegalArgumentException("camera delta set not found: " + id));
    }

    public CameraDeltaSet updateCounts(long id, int sourceSnapshotCount, int generatedDeltaCount) {
        if (sourceSnapshotCount < 0) {
            throw new IllegalArgumentException("sourceSnapshotCount must not be negative");
        }
        if (generatedDeltaCount < 0) {
            throw new IllegalArgumentException("generatedDeltaCount must not be negative");
        }

        String sql = """
                UPDATE camera_delta_sets
                SET source_snapshot_count = ?,
                    generated_delta_count = ?
                WHERE id = ?;
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setInt(1, sourceSnapshotCount);
            statement.setInt(2, generatedDeltaCount);
            statement.setLong(3, requirePositive(id, "id"));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to update camera delta set counts", exception);
        }

        return findById(id)
                .orElseThrow(() -> new IllegalArgumentException("camera delta set not found: " + id));
    }

    public CameraDeltaSet updateCounts(String printerId, long id, int sourceSnapshotCount, int generatedDeltaCount) {
        if (sourceSnapshotCount < 0) {
            throw new IllegalArgumentException("sourceSnapshotCount must not be negative");
        }
        if (generatedDeltaCount < 0) {
            throw new IllegalArgumentException("generatedDeltaCount must not be negative");
        }

        String sql = """
                UPDATE camera_delta_sets
                SET source_snapshot_count = ?,
                    generated_delta_count = ?
                WHERE printer_id = ?
                    AND id = ?;
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setInt(1, sourceSnapshotCount);
            statement.setInt(2, generatedDeltaCount);
            statement.setString(3, requireText(printerId, "printerId"));
            statement.setLong(4, requirePositive(id, "id"));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to update camera delta set counts", exception);
        }

        return findByPrinterIdAndId(printerId, id)
                .orElseThrow(() -> new IllegalArgumentException("camera delta set not found: " + id));
    }

    private static String selectColumns() {
        return """
                SELECT
                    id,
                    printer_id,
                    camera_job_id,
                    method_name,
                    delta_snapshot_step,
                    source_snapshot_count,
                    generated_delta_count,
                    created_at,
                    message
                """;
    }

    private static CameraDeltaSet mapRow(ResultSet resultSet) throws SQLException {
        return new CameraDeltaSet(
                resultSet.getLong("id"),
                resultSet.getString("printer_id"),
                resultSet.getLong("camera_job_id"),
                resultSet.getString("method_name"),
                resultSet.getInt("delta_snapshot_step"),
                resultSet.getInt("source_snapshot_count"),
                resultSet.getInt("generated_delta_count"),
                Instant.parse(resultSet.getString("created_at")),
                resultSet.getString("message"));
    }

    private static long nextId(Connection connection, String printerId) throws SQLException {
        String sql = "SELECT COALESCE(MAX(id), 0) + 1 AS next_id FROM camera_delta_sets WHERE printer_id = ?;";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, requireText(printerId, "printerId"));

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getLong("next_id");
                }
            }
        }

        throw new IllegalStateException("Failed to allocate camera delta set id");
    }

    private static long requirePositive(long value, String fieldName) {
        if (value <= 0L) {
            throw new IllegalArgumentException(fieldName + " must be greater than zero");
        }

        return value;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }

        return value.trim();
    }
}
