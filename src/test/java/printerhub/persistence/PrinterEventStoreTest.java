package printerhub.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class PrinterEventStoreTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void clearDatabaseProperty() {
        System.clearProperty("printerhub.databaseFile");
    }

    @Test
    void saveStoresEvent() throws Exception {
        useDatabase("event-save.db");

        PrinterEventStore store = new PrinterEventStore();
        PrinterEvent event = new PrinterEvent(
                0L,
                "printer-1",
                "job-1",
                "PRINTER_ERROR",
                "Heater failure",
                Instant.parse("2026-04-29T10:00:00Z")
        );

        PrinterEvent saved = store.save(event);

        assertSame(event, saved);
        assertEquals(1, countRows());

        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        """
                        SELECT printer_id, job_id, event_type, message, created_at
                        FROM printer_events
                        ORDER BY id ASC
                        LIMIT 1
                        """
                );
                ResultSet resultSet = statement.executeQuery()
        ) {
            assertTrue(resultSet.next());
            assertEquals("printer-1", resultSet.getString("printer_id"));
            assertEquals("job-1", resultSet.getString("job_id"));
            assertEquals("PRINTER_ERROR", resultSet.getString("event_type"));
            assertEquals("Heater failure", resultSet.getString("message"));
            assertEquals("2026-04-29T10:00:00Z", resultSet.getString("created_at"));
        }
    }

    @Test
    void recordCreatesAndStoresEvent() throws Exception {
        useDatabase("event-record.db");

        PrinterEventStore store = new PrinterEventStore();

        PrinterEvent event = store.record(
                "printer-2",
                null,
                "PRINTER_TIMEOUT",
                "No response for command M105"
        );

        assertEquals("printer-2", event.printerId());
        assertNull(event.jobId());
        assertEquals("PRINTER_TIMEOUT", event.eventType());
        assertEquals("No response for command M105", event.message());
        assertEquals(1, countRows());
    }

    @Test
    void saveFailsForNullEvent() {
        PrinterEventStore store = new PrinterEventStore();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> store.save(null)
        );

        assertEquals("printer event must not be null", exception.getMessage());
    }

    @Test
    void saveWrapsDatabaseFailure() {
        System.setProperty("printerhub.databaseFile", tempDir.resolve("not-a-db-dir").toString());
        assertDoesNotThrow(() -> java.nio.file.Files.createDirectories(tempDir.resolve("not-a-db-dir")));

        PrinterEventStore store = new PrinterEventStore();
        PrinterEvent event = PrinterEvent.create(
                "printer-1",
                null,
                "PRINTER_ERROR",
                "Heater failure"
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> store.save(event)
        );

        assertEquals("Failed to save printer event", exception.getMessage());
    }

    private void useDatabase(String fileName) {
        Path dbFile = tempDir.resolve(fileName);
        System.setProperty("printerhub.databaseFile", dbFile.toString());
        new DatabaseInitializer().initialize();
    }

    private int countRows() throws Exception {
        try (
                Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM printer_events"
                );
                ResultSet resultSet = statement.executeQuery()
        ) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }
}