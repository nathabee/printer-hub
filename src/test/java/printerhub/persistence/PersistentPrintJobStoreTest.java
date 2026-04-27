package printerhub.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import printerhub.jobs.PrintJob;
import printerhub.jobs.PrintJobState;
import printerhub.jobs.PrintJobType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PersistentPrintJobStoreTest {

    private Path databaseFile;
    private PersistentPrintJobStore store;

    @BeforeEach
    void setUp() throws IOException {
        databaseFile = Files.createTempFile("printerhub-job-execution-test-", ".db");
        System.setProperty("printerhub.databaseFile", databaseFile.toString());

        DatabaseInitializer.initialize();

        store = new PersistentPrintJobStore();
    }

    @AfterEach
    void tearDown() throws IOException {
        System.clearProperty("printerhub.databaseFile");
        Files.deleteIfExists(databaseFile);
    }

    @Test
    void createPersistsJob() {
        PrintJob job = store.create("test-job", PrintJobType.SIMULATED);

        Optional<PrintJob> loadedJob = store.findById(job.getId());

        assertTrue(loadedJob.isPresent());
        assertEquals("test-job", loadedJob.get().getName());
        assertEquals(PrintJobType.SIMULATED, loadedJob.get().getType());
        assertEquals(PrintJobState.CREATED, loadedJob.get().getState());
    }

    @Test
    void createAssignedPersistsPrinterAssignment() {
        PrintJob job = store.createAssigned(
                "assigned-job",
                PrintJobType.SIMULATED,
                "printer-2"
        );

        Optional<PrintJob> loadedJob = store.findById(job.getId());

        assertTrue(loadedJob.isPresent());
        assertEquals(PrintJobState.ASSIGNED, loadedJob.get().getState());
        assertEquals("printer-2", loadedJob.get().getAssignedPrinterId());
    }

    @Test
    void saveUpdatesExistingJob() {
        PrintJob job = store.create("update-job", PrintJobType.SIMULATED);
        PrintJob updatedJob = job.assignedTo("printer-1");

        store.save(updatedJob);

        Optional<PrintJob> loadedJob = store.findById(job.getId());

        assertTrue(loadedJob.isPresent());
        assertEquals(PrintJobState.ASSIGNED, loadedJob.get().getState());
        assertEquals("printer-1", loadedJob.get().getAssignedPrinterId());
        assertEquals(1, store.size());
    }

    @Test
    void findByIdReturnsEmptyForUnknownId() {
        assertTrue(store.findById("missing-id").isEmpty());
    }
}