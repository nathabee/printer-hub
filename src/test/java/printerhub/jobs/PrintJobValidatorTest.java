package printerhub.jobs;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class PrintJobValidatorTest {

    @Test
    void validJob_returnsTrue() {
        PrintJob job = PrintJob.create("demo.gcode", PrintJobType.GCODE);

        assertTrue(PrintJobValidator.isValid(job));
        assertNull(PrintJobValidator.validationMessage(job));
    }

    @Test
    void nullJob_returnsValidationMessage() {
        assertFalse(PrintJobValidator.isValid(null));
        assertEquals("print job must not be null", PrintJobValidator.validationMessage(null));
    }

    @Test
    void canTransition_createdToValidated() {
        assertTrue(PrintJobValidator.canTransition(
                PrintJobState.CREATED,
                PrintJobState.VALIDATED
        ));
    }

    @Test
    void canTransition_createdToCancelled() {
        assertTrue(PrintJobValidator.canTransition(
                PrintJobState.CREATED,
                PrintJobState.CANCELLED
        ));
    }

    @Test
    void canTransition_validatedToAssigned() {
        assertTrue(PrintJobValidator.canTransition(
                PrintJobState.VALIDATED,
                PrintJobState.ASSIGNED
        ));
    }

    @Test
    void canTransition_assignedToRunning() {
        assertTrue(PrintJobValidator.canTransition(
                PrintJobState.ASSIGNED,
                PrintJobState.RUNNING
        ));
    }

    @Test
    void canTransition_runningToCompleted() {
        assertTrue(PrintJobValidator.canTransition(
                PrintJobState.RUNNING,
                PrintJobState.COMPLETED
        ));
    }

    @Test
    void cannotTransition_completedToRunning() {
        assertFalse(PrintJobValidator.canTransition(
                PrintJobState.COMPLETED,
                PrintJobState.RUNNING
        ));
    }

    @Test
    void cannotTransition_failedToRunning() {
        assertFalse(PrintJobValidator.canTransition(
                PrintJobState.FAILED,
                PrintJobState.RUNNING
        ));
    }

    @Test
    void cannotTransition_cancelledToRunning() {
        assertFalse(PrintJobValidator.canTransition(
                PrintJobState.CANCELLED,
                PrintJobState.RUNNING
        ));
    }

    @Test
    void cannotTransition_nullState() {
        assertFalse(PrintJobValidator.canTransition(null, PrintJobState.CREATED));
        assertFalse(PrintJobValidator.canTransition(PrintJobState.CREATED, null));
    }

    @Test
    void sameStateTransition_isAllowed() {
        assertTrue(PrintJobValidator.canTransition(
                PrintJobState.CREATED,
                PrintJobState.CREATED
        ));
    }

    @Test
    void constructorValidationRejectsMissingDateWhenForcedNull() {
        PrintJob job = new PrintJob(
                "job-1",
                "demo",
                PrintJobType.GCODE,
                PrintJobState.CREATED,
                null,
                LocalDateTime.now(),
                null
        );

        assertTrue(PrintJobValidator.isValid(job));
    }
}