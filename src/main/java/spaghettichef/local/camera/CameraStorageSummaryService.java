package spaghettichef.local.camera;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import spaghettichef.local.persistence.CameraCalculationResultStore;
import spaghettichef.local.persistence.CameraCalculationRun;
import spaghettichef.local.persistence.CameraCalculationRunStore;
import spaghettichef.local.persistence.CameraDeltaFrame;
import spaghettichef.local.persistence.CameraDeltaFrameStore;
import spaghettichef.local.persistence.CameraDeltaSet;
import spaghettichef.local.persistence.CameraDeltaSetStore;
import spaghettichef.local.persistence.CameraJob;
import spaghettichef.local.persistence.CameraJobStore;
import spaghettichef.local.persistence.CameraSnapshotEntry;
import spaghettichef.local.persistence.CameraSnapshotEntryStore;

public final class CameraStorageSummaryService {

    private final CameraJobStore cameraJobStore;
    private final CameraSnapshotEntryStore snapshotEntryStore;
    private final CameraDeltaSetStore deltaSetStore;
    private final CameraDeltaFrameStore deltaFrameStore;
    private final CameraCalculationRunStore calculationRunStore;
    private final CameraCalculationResultStore calculationResultStore;

    public CameraStorageSummaryService() {
        this(
                new CameraJobStore(),
                new CameraSnapshotEntryStore(),
                new CameraDeltaSetStore(),
                new CameraDeltaFrameStore(),
                new CameraCalculationRunStore(),
                new CameraCalculationResultStore());
    }

    public CameraStorageSummaryService(
            CameraJobStore cameraJobStore,
            CameraSnapshotEntryStore snapshotEntryStore,
            CameraDeltaSetStore deltaSetStore,
            CameraDeltaFrameStore deltaFrameStore,
            CameraCalculationRunStore calculationRunStore,
            CameraCalculationResultStore calculationResultStore) {
        if (cameraJobStore == null
                || snapshotEntryStore == null
                || deltaSetStore == null
                || deltaFrameStore == null
                || calculationRunStore == null
                || calculationResultStore == null) {
            throw new IllegalArgumentException("camera storage summary stores must not be null");
        }
        this.cameraJobStore = cameraJobStore;
        this.snapshotEntryStore = snapshotEntryStore;
        this.deltaSetStore = deltaSetStore;
        this.deltaFrameStore = deltaFrameStore;
        this.calculationRunStore = calculationRunStore;
        this.calculationResultStore = calculationResultStore;
    }

    public CameraStorageSummary summarize(String printerId) {
        String normalizedPrinterId = requirePrinterId(printerId);
        Path storageRoot = CameraStoragePaths.cameraDirectory(normalizedPrinterId).toAbsolutePath().normalize();
        List<CameraJob> jobs = cameraJobStore.findByPrinterId(normalizedPrinterId);

        int snapshotCount = 0;
        int retainedSnapshotCount = 0;
        int deltaSetCount = 0;
        int deltaFrameCount = 0;
        int calculationRunCount = 0;
        int calculationResultCount = 0;
        long totalSnapshotBytes = 0L;
        long totalDeltaBytes = 0L;
        int missingFileCount = 0;

        for (CameraJob job : jobs) {
            long cameraJobId = job.requireId();
            List<CameraSnapshotEntry> entries = snapshotEntryStore.findByPrinterIdAndJobId(
                    normalizedPrinterId,
                    Long.toString(cameraJobId));
            snapshotCount += entries.size();
            for (CameraSnapshotEntry entry : entries) {
                if (!entry.fileDeleted()) {
                    retainedSnapshotCount++;
                    totalSnapshotBytes += entry.sizeBytes();
                    if (!Files.isRegularFile(Path.of(entry.snapshotPath()))) {
                        missingFileCount++;
                    }
                }
            }

            List<CameraDeltaSet> deltaSets = deltaSetStore.findByPrinterIdAndCameraJobId(
                    normalizedPrinterId,
                    cameraJobId);
            deltaSetCount += deltaSets.size();
            for (CameraDeltaSet deltaSet : deltaSets) {
                List<CameraDeltaFrame> frames = deltaFrameStore.findByPrinterIdAndDeltaSetId(
                        normalizedPrinterId,
                        deltaSet.requireId());
                deltaFrameCount += frames.size();
                for (CameraDeltaFrame frame : frames) {
                    Path deltaPath = Path.of(frame.deltaPath());
                    if (Files.isRegularFile(deltaPath)) {
                        totalDeltaBytes += size(deltaPath);
                    } else {
                        missingFileCount++;
                    }
                }
            }

            List<CameraCalculationRun> runs = calculationRunStore.findByPrinterIdAndCameraJobId(
                    normalizedPrinterId,
                    cameraJobId);
            calculationRunCount += runs.size();
            for (CameraCalculationRun run : runs) {
                calculationResultCount += calculationResultStore.findByCalculationRunId(run.requireId()).size();
            }
        }

        return new CameraStorageSummary(
                normalizedPrinterId,
                storageRoot.toString(),
                jobs.size(),
                snapshotCount,
                retainedSnapshotCount,
                deltaSetCount,
                deltaFrameCount,
                calculationRunCount,
                calculationResultCount,
                totalSnapshotBytes,
                totalDeltaBytes,
                missingFileCount,
                Files.isRegularFile(storageRoot.resolve("latest.jpg")),
                Files.isRegularFile(storageRoot.resolve("previous.jpg")),
                Files.isRegularFile(storageRoot.resolve("delta.jpg")),
                message(jobs.size(), snapshotCount, missingFileCount));
    }

    private static long size(Path path) {
        try {
            return Files.size(path);
        } catch (IOException exception) {
            return 0L;
        }
    }

    private static String message(int cameraJobCount, int snapshotCount, int missingFileCount) {
        if (cameraJobCount == 0 && snapshotCount == 0) {
            return "No camera storage rows found for printer";
        }
        if (missingFileCount > 0) {
            return "Camera storage summary includes missing files";
        }
        return "Camera storage summary available";
    }

    private static String requirePrinterId(String printerId) {
        if (printerId == null || printerId.isBlank()) {
            throw new IllegalArgumentException("printerId must not be blank");
        }
        return printerId.trim();
    }
}
