package printerhub.jobs;

import java.util.EnumSet;
import java.util.Set;

public final class PrintJobValidator {

    private static final Set<PrintJobState> TERMINAL_STATES = EnumSet.of(
            PrintJobState.COMPLETED,
            PrintJobState.FAILED,
            PrintJobState.CANCELLED
    );

    private PrintJobValidator() {
    }

    public static boolean isValid(PrintJob job) {
        return validationMessage(job) == null;
    }

    public static String validationMessage(PrintJob job) {
        if (job == null) {
            return "print job must not be null";
        }

        if (job.getId() == null || job.getId().isBlank()) {
            return "print job id must not be blank";
        }

        if (job.getName() == null || job.getName().isBlank()) {
            return "print job name must not be blank";
        }

        if (job.getType() == null) {
            return "print job type must not be null";
        }

        if (job.getState() == null) {
            return "print job state must not be null";
        }

        if (job.getCreatedAt() == null) {
            return "print job createdAt must not be null";
        }

        if (job.getUpdatedAt() == null) {
            return "print job updatedAt must not be null";
        }

        return null;
    }

    public static boolean canTransition(PrintJobState current, PrintJobState next) {
        if (current == null || next == null) {
            return false;
        }

        if (current == next) {
            return true;
        }

        if (TERMINAL_STATES.contains(current)) {
            return false;
        }

        return switch (current) {
            case CREATED -> next == PrintJobState.VALIDATED
                    || next == PrintJobState.CANCELLED
                    || next == PrintJobState.FAILED;
            case VALIDATED -> next == PrintJobState.ASSIGNED
                    || next == PrintJobState.CANCELLED
                    || next == PrintJobState.FAILED;
            case ASSIGNED -> next == PrintJobState.RUNNING
                    || next == PrintJobState.CANCELLED
                    || next == PrintJobState.FAILED;
            case RUNNING -> next == PrintJobState.COMPLETED
                    || next == PrintJobState.FAILED
                    || next == PrintJobState.CANCELLED;
            case COMPLETED, FAILED, CANCELLED -> false;
        };
    }
}