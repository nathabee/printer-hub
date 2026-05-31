package spaghettichef.local.monitoring;

import spaghettichef.local.command.SdCardUploadService;
import spaghettichef.local.job.PrintJob;
import spaghettichef.local.runtime.PrinterRuntimeNode;
import spaghettichef.local.SerialFailureType;

import java.time.Instant;
import java.util.List;

public record GlobalMonitoringSnapshot(
        Instant generatedAt,
        Summary summary,
        List<PrinterRuntime> printers,
        List<PrintJob> activeJobs,
        List<SdCardUploadService.UploadProgress> activeUploads) {

    public record Summary(
            int totalPrinters,
            int enabledPrinters,
            int disabledPrinters,
            int busyPrinters,
            int errorPrinters,
            int activeJobs,
            int activeUploads) {
    }

    public record PrinterRuntime(
            PrinterRuntimeNode printer,
            String state,
            boolean busy,
            String activeJobId,
            String errorMessage,
            SerialFailureType serialFailureType,
            Instant updatedAt) {
    }
}
