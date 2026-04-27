package printerhub.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * SQLite-backed event store for audit and traceability data.
 */
public class PrinterEventStore {

    public PrinterEvent save(PrinterEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("printer event must not be null");
        }

        String sql = """
                INSERT INTO printer_events (
                    printer_id,
                    job_id,
                    event_type,
                    message,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?);
                """;

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {

            statement.setString(1, event.getPrinterId());
            statement.setString(2, event.getJobId());
            statement.setString(3, event.getEventType());
            statement.setString(4, event.getMessage());
            statement.setString(5, event.getCreatedAt().toString());

            statement.executeUpdate();

            return event;

        } catch (SQLException e) {
            throw new RuntimeException("Failed to save printer event", e);
        }
    }

    public PrinterEvent record(String printerId,
                               String jobId,
                               String eventType,
                               String message) {
        return save(PrinterEvent.create(
                printerId,
                jobId,
                eventType,
                message
        ));
    }
}