package printerhub.jobs;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class PrintJobTest {

    @Test
    void create_setsDefaults() {
        PrintJob job = PrintJob.create("test-job.gcode", PrintJobType.GCODE);

        assertNotNull(job.getId());
        assertEquals("test-job.gcode", job.getName());
        assertEquals(PrintJobType.GCODE, job.getType());
        assertEquals(PrintJobState.CREATED, job.getState());
        assertNull(job.getAssignedPrinterId());
        assertNotNull(job.getCreatedAt());
        assertNotNull(job.getUpdatedAt());
    }

    @Test
    void constructor_normalizesBlankValues() {
        PrintJob job = new PrintJob(
                " ",
                " ",
                null,
                null,
                " ",
                null,
                null
        );

        assertNotNull(job.getId());
        assertEquals("unnamed-job", job.getName());
        assertEquals(PrintJobType.SIMULATED, job.getType());
        assertEquals(PrintJobState.CREATED, job.getState());
        assertNull(job.getAssignedPrinterId());
        assertNotNull(job.getCreatedAt());
        assertNotNull(job.getUpdatedAt());
    }

    @Test
    void withState_returnsNewJobWithSameIdentity() {
        LocalDateTime createdAt = LocalDateTime.now().minusMinutes(5);
        PrintJob job = new PrintJob(
                "job-1",
                "demo",
                PrintJobType.SIMULATED,
                PrintJobState.CREATED,
                null,
                createdAt,
                createdAt
        );

        PrintJob validated = job.withState(PrintJobState.VALIDATED);

        assertEquals("job-1", validated.getId());
        assertEquals("demo", validated.getName());
        assertEquals(PrintJobState.VALIDATED, validated.getState());
        assertEquals(createdAt, validated.getCreatedAt());
        assertTrue(validated.getUpdatedAt().isAfter(createdAt)
                || validated.getUpdatedAt().isEqual(createdAt));
    }

    @Test
    void assignedTo_setsPrinterAndAssignedState() {
        PrintJob job = PrintJob.create("demo", PrintJobType.SIMULATED);

        PrintJob assigned = job.assignedTo("printer-1");

        assertEquals(PrintJobState.ASSIGNED, assigned.getState());
        assertEquals("printer-1", assigned.getAssignedPrinterId());
    }

    @Test
    void equality_isBasedOnId() {
        LocalDateTime now = LocalDateTime.now();

        PrintJob first = new PrintJob(
                "same-id",
                "first",
                PrintJobType.GCODE,
                PrintJobState.CREATED,
                null,
                now,
                now
        );

        PrintJob second = new PrintJob(
                "same-id",
                "second",
                PrintJobType.SIMULATED,
                PrintJobState.RUNNING,
                "printer-1",
                now,
                now
        );

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }
}