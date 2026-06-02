package spaghettichef.local.camera;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import spaghettichef.local.persistence.CameraCalculationResultStore;
import spaghettichef.local.persistence.CameraCalculationRun;
import spaghettichef.local.persistence.CameraCalculationRunStore;
import spaghettichef.local.persistence.CameraDeltaFrame;
import spaghettichef.local.persistence.CameraDeltaFrameStore;
import spaghettichef.local.persistence.CameraDeltaSetStore;
import spaghettichef.local.persistence.CameraEventStore;
import spaghettichef.local.persistence.CameraJob;
import spaghettichef.local.persistence.CameraJobStore;
import spaghettichef.local.persistence.CameraSettings;
import spaghettichef.local.persistence.CameraSnapshotEntry;
import spaghettichef.local.persistence.CameraSnapshotEntryStore;

public final class CameraJobDeletionService {

    private static final Set<String> PREVIEW_FILENAMES = Set.of("latest.jpg", "previous.jpg", "delta.jpg");

    private final CameraSettingsService settingsService;
    private final CameraJobStore cameraJobStore;
    private final CameraSnapshotEntryStore snapshotEntryStore;
    private final CameraDeltaSetStore deltaSetStore;
    private final CameraDeltaFrameStore deltaFrameStore;
    private final CameraCalculationRunStore calculationRunStore;
    private final CameraCalculationResultStore calculationResultStore;
    private final CameraEventStore eventStore;

    public CameraJobDeletionService(CameraSettingsService settingsService) {
        this(
                settingsService,
                new CameraJobStore(),
                new CameraSnapshotEntryStore(),
                new CameraDeltaSetStore(),
                new CameraDeltaFrameStore(),
                new CameraCalculationRunStore(),
                new CameraCalculationResultStore(),
                new CameraEventStore());
    }

    public CameraJobDeletionService(
            CameraSettingsService settingsService,
            CameraJobStore cameraJobStore,
            CameraSnapshotEntryStore snapshotEntryStore,
            CameraDeltaSetStore deltaSetStore,
            CameraDeltaFrameStore deltaFrameStore,
            CameraCalculationRunStore calculationRunStore,
            CameraCalculationResultStore calculationResultStore,
            CameraEventStore eventStore) {
        this.settingsService = Objects.requireNonNull(settingsService, "settingsService");
        this.cameraJobStore = Objects.requireNonNull(cameraJobStore, "cameraJobStore");
        this.snapshotEntryStore = Objects.requireNonNull(snapshotEntryStore, "snapshotEntryStore");
        this.deltaSetStore = Objects.requireNonNull(deltaSetStore, "deltaSetStore");
        this.deltaFrameStore = Objects.requireNonNull(deltaFrameStore, "deltaFrameStore");
        this.calculationRunStore = Objects.requireNonNull(calculationRunStore, "calculationRunStore");
        this.calculationResultStore = Objects.requireNonNull(calculationResultStore, "calculationResultStore");
        this.eventStore = Objects.requireNonNull(eventStore, "eventStore");
    }

    public CameraJobDeletionReport delete(String printerId, long cameraJobId, CameraJobDeletionRequest request) {
        String normalizedPrinterId = requirePrinterId(printerId);
        if (cameraJobId <= 0L) {
            throw new IllegalArgumentException("cameraJobId must be greater than zero");
        }
        CameraJobDeletionRequest effectiveRequest = request == null
                ? CameraJobDeletionRequest.safeDefault(CameraJobDeletionRequest.CONFIRMATION)
                : request;
        effectiveRequest.requireConfirmed();

        CameraJob cameraJob = cameraJobStore.findByPrinterIdAndId(normalizedPrinterId, cameraJobId)
                .orElseThrow(() -> new IllegalArgumentException("camera job not found: " + cameraJobId));

        CameraSettings settings = settingsService.load(normalizedPrinterId);
        Path printerCameraDirectory = CameraStoragePaths
                .resolveBaseDirectory(settings.storageDirectory())
                .resolve(safePathSegment(normalizedPrinterId))
                .toAbsolutePath()
                .normalize();

        List<String> failedFiles = new ArrayList<>();
        DeleteFilesResult snapshotFiles = new DeleteFilesResult(0, 0L);
        DeleteFilesResult deltaFiles = new DeleteFilesResult(0, 0L);
        int deletedSnapshotRows = 0;
        int deletedDeltaRows = 0;
        int deletedDeltaSetRows = 0;
        int deletedCalculationRunRows = 0;
        int deletedCalculationResultRows = 0;
        int deletedCameraEventRows = 0;
        int deletedCameraJobRows = 0;

        if (effectiveRequest.deleteSnapshotFiles()) {
            snapshotFiles = deleteSnapshotFiles(
                    snapshotEntryStore.findByPrinterIdAndJobId(normalizedPrinterId, Long.toString(cameraJobId)),
                    printerCameraDirectory,
                    failedFiles);
        }

        if (effectiveRequest.deleteDeltaFiles()) {
            deltaFiles = deleteDeltaFiles(
                    deltaFrameStore.findByPrinterIdAndCameraJobId(normalizedPrinterId, cameraJobId),
                    printerCameraDirectory,
                    failedFiles);
        }

        if (effectiveRequest.deleteCalculationRuns()) {
            for (CameraCalculationRun run : calculationRunStore.findByPrinterIdAndCameraJobId(
                    normalizedPrinterId,
                    cameraJobId)) {
                deletedCalculationResultRows += calculationResultStore.deleteByCalculationRunId(run.requireId());
            }
            deletedCalculationRunRows = calculationRunStore.deleteByPrinterIdAndCameraJobId(
                    normalizedPrinterId,
                    cameraJobId);
        }

        if (effectiveRequest.deleteDeltaRows()) {
            deletedDeltaRows = deltaFrameStore.deleteByPrinterIdAndCameraJobId(normalizedPrinterId, cameraJobId);
            deletedDeltaSetRows = deltaSetStore.deleteByPrinterIdAndCameraJobId(normalizedPrinterId, cameraJobId);
        }

        if (effectiveRequest.deleteCameraEvents()) {
            deletedCameraEventRows = eventStore.deleteByPrinterIdAndCameraJobId(normalizedPrinterId, cameraJobId);
        }

        if (effectiveRequest.deleteSnapshotRows()) {
            deletedSnapshotRows = snapshotEntryStore.deleteByPrinterIdAndJobId(
                    normalizedPrinterId,
                    Long.toString(cameraJobId));
        }

        if (effectiveRequest.deleteCameraJob()) {
            deletedCameraJobRows = cameraJobStore.deleteByPrinterIdAndId(normalizedPrinterId, cameraJobId);
        }

        String message = failedFiles.isEmpty()
                ? "camera_job_deleted"
                : "camera_job_deleted_with_file_errors";

        return new CameraJobDeletionReport(
                normalizedPrinterId,
                cameraJobId,
                snapshotFiles.deletedFiles(),
                snapshotFiles.deletedBytes(),
                deletedSnapshotRows,
                deltaFiles.deletedFiles(),
                deltaFiles.deletedBytes(),
                deletedDeltaRows,
                deletedDeltaSetRows,
                deletedCalculationRunRows,
                deletedCalculationResultRows,
                deletedCameraEventRows,
                deletedCameraJobRows,
                failedFiles,
                message);
    }

    private static DeleteFilesResult deleteSnapshotFiles(
            List<CameraSnapshotEntry> entries,
            Path printerCameraDirectory,
            List<String> failedFiles) {
        int deletedFiles = 0;
        long deletedBytes = 0L;

        for (CameraSnapshotEntry entry : entries) {
            FileDeleteResult result = deleteFile(entry.snapshotPath(), printerCameraDirectory, failedFiles);
            deletedFiles += result.deletedFiles();
            deletedBytes += result.deletedBytes();
        }

        return new DeleteFilesResult(deletedFiles, deletedBytes);
    }

    private static DeleteFilesResult deleteDeltaFiles(
            List<CameraDeltaFrame> frames,
            Path printerCameraDirectory,
            List<String> failedFiles) {
        int deletedFiles = 0;
        long deletedBytes = 0L;

        for (CameraDeltaFrame frame : frames) {
            FileDeleteResult result = deleteFile(frame.deltaPath(), printerCameraDirectory, failedFiles);
            deletedFiles += result.deletedFiles();
            deletedBytes += result.deletedBytes();
        }

        return new DeleteFilesResult(deletedFiles, deletedBytes);
    }

    private static FileDeleteResult deleteFile(
            String storedPath,
            Path printerCameraDirectory,
            List<String> failedFiles) {
        Path path = Path.of(storedPath).toAbsolutePath().normalize();
        Path fileName = path.getFileName();
        if (fileName == null
                || PREVIEW_FILENAMES.contains(fileName.toString().toLowerCase(Locale.ROOT))
                || !path.startsWith(printerCameraDirectory)) {
            failedFiles.add(storedPath);
            return new FileDeleteResult(0, 0L);
        }

        try {
            long size = Files.isRegularFile(path) ? Files.size(path) : 0L;
            if (Files.deleteIfExists(path)) {
                return new FileDeleteResult(1, size);
            }
            return new FileDeleteResult(0, 0L);
        } catch (IOException exception) {
            failedFiles.add(storedPath);
            return new FileDeleteResult(0, 0L);
        }
    }

    private static String requirePrinterId(String printerId) {
        if (printerId == null || printerId.isBlank()) {
            throw new IllegalArgumentException("printerId must not be blank");
        }
        return printerId.trim();
    }

    private static String safePathSegment(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        return value.trim().replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private record DeleteFilesResult(int deletedFiles, long deletedBytes) {
    }

    private record FileDeleteResult(int deletedFiles, long deletedBytes) {
    }
}
