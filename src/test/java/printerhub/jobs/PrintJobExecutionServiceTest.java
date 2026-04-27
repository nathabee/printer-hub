package printerhub.jobs;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import printerhub.farm.PrinterFarmStore;
import printerhub.farm.PrinterNode;
import printerhub.persistence.DatabaseInitializer;
import printerhub.persistence.PrinterEventStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PrintJobExecutionServiceTest {

    private Path databaseFile;

    @BeforeEach
    void setUp() throws IOException {
        databaseFile = Files.createTempFile("printerhub-job-execution-test-", ".db");
        System.setProperty("printerhub.databaseFile", databaseFile.toString());

        DatabaseInitializer.initialize();
    }

    @AfterEach
    void tearDown() throws IOException {
        System.clearProperty("printerhub.databaseFile");
        Files.deleteIfExists(databaseFile);
    }

    @Test
    void advanceJobs_movesAssignedJobToRunning() {
        PrintJobStore jobStore = new PrintJobStore();

        PrinterFarmStore printerFarmStore = new PrinterFarmStore(List.of(
                new PrinterNode("printer-1", "Primary printer", "SIM_PORT", "sim")
        ));

        PrinterEventStore eventStore = new PrinterEventStore();

        PrintJob job = jobStore.createAssigned(
                "demo-job",
                PrintJobType.SIMULATED,
                "printer-1"
        );

        PrinterNode printerNode = printerFarmStore.findById("printer-1").orElseThrow();
        printerNode.assignJob(job.getId());

        PrintJobExecutionService service = new PrintJobExecutionService(
                jobStore,
                printerFarmStore,
                eventStore
        );

        service.advanceJobs();

        PrintJob updatedJob = jobStore.findById(job.getId()).orElseThrow();

        assertEquals(PrintJobState.RUNNING, updatedJob.getState());
        assertEquals(job.getId(), printerNode.getAssignedJobId());
    }

    @Test
    void advanceJobs_movesRunningJobToCompletedAndClearsPrinterAssignment() {
        PrintJobStore jobStore = new PrintJobStore();

        PrinterFarmStore printerFarmStore = new PrinterFarmStore(List.of(
                new PrinterNode("printer-1", "Primary printer", "SIM_PORT", "sim")
        ));

        PrinterEventStore eventStore = new PrinterEventStore();

        PrintJob assignedJob = jobStore.createAssigned(
                "demo-job",
                PrintJobType.SIMULATED,
                "printer-1"
        );

        PrintJob runningJob = assignedJob.withState(PrintJobState.RUNNING);
        jobStore.save(runningJob);

        PrinterNode printerNode = printerFarmStore.findById("printer-1").orElseThrow();
        printerNode.assignJob(runningJob.getId());

        PrintJobExecutionService service = new PrintJobExecutionService(
                jobStore,
                printerFarmStore,
                eventStore
        );

        service.advanceJobs();

        PrintJob updatedJob = jobStore.findById(runningJob.getId()).orElseThrow();

        assertEquals(PrintJobState.COMPLETED, updatedJob.getState());
        assertNull(printerNode.getAssignedJobId());
    }
}