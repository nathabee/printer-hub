package printerhub.jobs;

import printerhub.farm.PrinterFarmStore;
import printerhub.farm.PrinterNode;
import printerhub.persistence.PrinterEventStore;

import java.util.List;

/**
 * Simulates job execution lifecycle transitions.
 */
public class PrintJobExecutionService {

    private final PrintJobStore jobStore;
    private final PrinterFarmStore printerFarmStore;
    private final PrinterEventStore eventStore;

    public PrintJobExecutionService(PrintJobStore jobStore,
                                    PrinterFarmStore printerFarmStore,
                                    PrinterEventStore eventStore) {
        if (jobStore == null) {
            throw new IllegalArgumentException("jobStore must not be null");
        }

        if (printerFarmStore == null) {
            throw new IllegalArgumentException("printerFarmStore must not be null");
        }

        if (eventStore == null) {
            throw new IllegalArgumentException("eventStore must not be null");
        }

        this.jobStore = jobStore;
        this.printerFarmStore = printerFarmStore;
        this.eventStore = eventStore;
    }

    public void advanceJobs() {
        List<PrintJob> jobs = jobStore.findAll();

        for (PrintJob job : jobs) {
            advanceJob(job);
        }
    }

    private void advanceJob(PrintJob job) {
        if (job.getAssignedPrinterId() == null) {
            return;
        }

        PrinterNode printerNode =
                printerFarmStore.findById(job.getAssignedPrinterId()).orElse(null);

        if (printerNode == null) {
            return;
        }

        if (job.getState() == PrintJobState.ASSIGNED) {
            moveToRunning(job, printerNode);
            return;
        }

        if (job.getState() == PrintJobState.RUNNING) {
            moveToCompleted(job, printerNode);
        }
    }

    private void moveToRunning(PrintJob job, PrinterNode printerNode) {
        if (!PrintJobValidator.canTransition(job.getState(), PrintJobState.RUNNING)) {
            return;
        }

        PrintJob runningJob = job.withState(PrintJobState.RUNNING);
        jobStore.save(runningJob);

        eventStore.record(
                printerNode.getId(),
                job.getId(),
                "JOB_RUNNING",
                "Print job moved to RUNNING"
        );
    }

    private void moveToCompleted(PrintJob job, PrinterNode printerNode) {
        if (!PrintJobValidator.canTransition(job.getState(), PrintJobState.COMPLETED)) {
            return;
        }

        PrintJob completedJob = job.withState(PrintJobState.COMPLETED);
        jobStore.save(completedJob);

        printerNode.clearAssignedJob();

        eventStore.record(
                printerNode.getId(),
                job.getId(),
                "JOB_COMPLETED",
                "Print job completed"
        );
    }
}