package printerhub.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public final class PrinterEventStore {

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
            statement.setString(1, event.printerId());
            statement.setString(2, event.jobId());
            statement.setString(3, event.eventType());
            statement.setString(4, event.message());
            statement.setString(5, event.createdAt().toString());

            statement.executeUpdate();

            return event;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save printer event", exception);
        }
    }

    public PrinterEvent record(
            String printerId,
            String jobId,
            String eventType,
            String message
    ) {
        return save(PrinterEvent.create(
                printerId,
                jobId,
                eventType,
                message
        ));
    }
}