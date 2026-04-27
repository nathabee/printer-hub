package printerhub.persistence;

import printerhub.jobs.PrintJob;
import printerhub.jobs.PrintJobState;
import printerhub.jobs.PrintJobStore;
import printerhub.jobs.PrintJobType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SQLite-backed print job store.
 */
public class PersistentPrintJobStore extends PrintJobStore {

    @Override
    public PrintJob create(String name, PrintJobType type) {
        PrintJob job = PrintJob.create(name, type);
        return save(job);
    }

    @Override
    public PrintJob createAssigned(String name, PrintJobType type, String printerId) {
        PrintJob job = PrintJob.create(name, type).assignedTo(printerId);
        return save(job);
    }

    @Override
    public PrintJob save(PrintJob job) {
        if (job == null) {
            throw new IllegalArgumentException("print job must not be null");
        }

        String sql = """
                INSERT INTO print_jobs (
                    id,
                    name,
                    type,
                    state,
                    printer_id,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    name = excluded.name,
                    type = excluded.type,
                    state = excluded.state,
                    printer_id = excluded.printer_id,
                    created_at = excluded.created_at,
                    updated_at = excluded.updated_at;
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {

            statement.setString(1, job.getId());
            statement.setString(2, job.getName());
            statement.setString(3, job.getType().name());
            statement.setString(4, job.getState().name());
            statement.setString(5, job.getAssignedPrinterId());
            statement.setString(6, job.getCreatedAt().toString());
            statement.setString(7, job.getUpdatedAt().toString());

            statement.executeUpdate();

            return job;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to save print job", e);
        }
    }

    @Override
    public List<PrintJob> findAll() {
        String sql = """
                SELECT
                    id,
                    name,
                    type,
                    state,
                    printer_id,
                    created_at,
                    updated_at
                FROM print_jobs
                ORDER BY created_at ASC;
                """;

        List<PrintJob> jobs = new ArrayList<>();

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()
        ) {

            while (resultSet.next()) {
                jobs.add(toPrintJob(resultSet));
            }

            return jobs;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load print jobs", e);
        }
    }

    @Override
    public Optional<PrintJob> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }

        String sql = """
                SELECT
                    id,
                    name,
                    type,
                    state,
                    printer_id,
                    created_at,
                    updated_at
                FROM print_jobs
                WHERE id = ?;
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {

            statement.setString(1, id.trim());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(toPrintJob(resultSet));
                }

                return Optional.empty();
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load print job by id", e);
        }
    }

    @Override
    public int size() {
        String sql = "SELECT COUNT(*) FROM print_jobs;";

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()
        ) {

            if (resultSet.next()) {
                return resultSet.getInt(1);
            }

            return 0;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to count print jobs", e);
        }
    }

    private PrintJob toPrintJob(ResultSet resultSet) throws SQLException {
        return new PrintJob(
                resultSet.getString("id"),
                resultSet.getString("name"),
                PrintJobType.valueOf(resultSet.getString("type")),
                PrintJobState.valueOf(resultSet.getString("state")),
                resultSet.getString("printer_id"),
                LocalDateTime.parse(resultSet.getString("created_at")),
                LocalDateTime.parse(resultSet.getString("updated_at"))
        );
    }
}