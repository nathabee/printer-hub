package printerhub.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrinterEventStoreTest {

    private Path databaseFile;
    private PrinterEventStore store;

    @BeforeEach
    void setUp() throws IOException {
        databaseFile = Files.createTempFile("printerhub-event-store-test-", ".db");
        System.setProperty("printerhub.databaseFile", databaseFile.toString());

        DatabaseInitializer.initialize();

        store = new PrinterEventStore();
    }

    @AfterEach
    void tearDown() throws IOException {
        System.clearProperty("printerhub.databaseFile");
        Files.deleteIfExists(databaseFile);
    }

    @Test
    void recordPersistsPrinterEvent() throws Exception {
        store.record(
                "printer-1",
                "job-1",
                "JOB_ASSIGNED",
                "Print job assigned to printer"
        );

        try (
                Connection connection = Database.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT printer_id, job_id, event_type, message FROM printer_events"
                )
        ) {
            assertTrue(resultSet.next());
            assertEquals("printer-1", resultSet.getString("printer_id"));
            assertEquals("job-1", resultSet.getString("job_id"));
            assertEquals("JOB_ASSIGNED", resultSet.getString("event_type"));
            assertEquals("Print job assigned to printer", resultSet.getString("message"));
        }
    }
}