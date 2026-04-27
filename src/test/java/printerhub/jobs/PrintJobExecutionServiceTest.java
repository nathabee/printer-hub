package printerhub.jobs;

import org.junit.jupiter.api.Test;
import printerhub.farm.PrinterFarmStore;
import printerhub.farm.PrinterNode;
import printerhub.persistence.PrinterEventStore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PrintJobExecutionServiceTest {

    @Test
    void advanceJobs_movesAssignedJobToRunning() {
        PrintJobStore jobStore = new PrintJobStore();
        PrinterFarmStore printerFarmStore = new PrinterFarmStore("SIM_PORT", "sim");
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
        PrinterFarmStore printerFarmStore = new PrinterFarmStore("SIM_PORT", "sim");
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