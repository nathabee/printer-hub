package printerhub.jobs;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PrintJobStoreTest {

    @Test
    void create_storesJob() {
        PrintJobStore store = new PrintJobStore();

        PrintJob job = store.create("demo", PrintJobType.SIMULATED);

        assertEquals(1, store.size());
        assertEquals("demo", job.getName());
        assertEquals(PrintJobType.SIMULATED, job.getType());
        assertEquals(PrintJobState.CREATED, job.getState());
    }

    @Test
    void findAll_returnsStoredJobs() {
        PrintJobStore store = new PrintJobStore();

        store.create("first", PrintJobType.SIMULATED);
        store.create("second", PrintJobType.GCODE);

        List<PrintJob> jobs = store.findAll();

        assertEquals(2, jobs.size());
    }

    @Test
    void findById_returnsStoredJob() {
        PrintJobStore store = new PrintJobStore();

        PrintJob job = store.create("demo", PrintJobType.SIMULATED);

        Optional<PrintJob> found = store.findById(job.getId());

        assertTrue(found.isPresent());
        assertEquals(job, found.get());
    }

    @Test
    void findById_unknown_returnsEmpty() {
        PrintJobStore store = new PrintJobStore();

        Optional<PrintJob> found = store.findById("missing");

        assertTrue(found.isEmpty());
    }

    @Test
    void findById_blank_returnsEmpty() {
        PrintJobStore store = new PrintJobStore();

        assertTrue(store.findById(null).isEmpty());
        assertTrue(store.findById(" ").isEmpty());
    }
}