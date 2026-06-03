package spaghettichef.local.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import spaghettichef.local.persistence.CameraCalculationResult;
import spaghettichef.local.persistence.CameraCalculationResultStore;
import spaghettichef.local.persistence.CameraCalculationRun;
import spaghettichef.local.persistence.CameraCalculationRunStore;
import spaghettichef.local.persistence.CameraDeltaFrame;
import spaghettichef.local.persistence.CameraDeltaFrameStore;
import spaghettichef.local.persistence.CameraDeltaSet;
import spaghettichef.local.persistence.CameraDeltaSetStore;
import spaghettichef.local.persistence.CameraEventStore;
import spaghettichef.local.persistence.CameraJob;
import spaghettichef.local.persistence.CameraJobStore;
import spaghettichef.local.persistence.CameraSettings;
import spaghettichef.local.persistence.CameraSettingsStore;
import spaghettichef.local.persistence.CameraSnapshotEntry;
import spaghettichef.local.persistence.CameraSnapshotEntryStore;
import spaghettichef.local.persistence.DatabaseInitializer;
import spaghettichef.local.persistence.PrinterConfigurationStore;
import spaghettichef.local.runtime.PrinterRuntimeNodeFactory;

class CameraJobDeletionServiceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-05-27T13:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    @TempDir
    Path tempDir;

    @AfterEach
    void clearDatabaseProperty() {
        System.clearProperty("spaghettichef.databaseFile");
    }

    @Test
    void deleteRemovesSelectedCameraJobData() throws Exception {
        useDatabase("camera-job-delete.db");
        Stores stores = stores();
        CameraJob job = saveCameraJob(stores, "printer-1");
        Path snapshotPath = saveSnapshot(stores.snapshotEntryStore(), "printer-1", job.requireId(), 1);
        CameraDeltaSet deltaSet = saveDeltaSet(stores, "printer-1", job.requireId());
        Path deltaPath = saveDeltaFrame(stores, "printer-1", job.requireId(), deltaSet.requireId(), 1L, 1L);
        CameraCalculationRun run = saveCalculationRun(stores, "printer-1", job.requireId(), deltaSet.requireId());
        stores.eventStore().record("printer-1", job.requireId(), "CAMERA_FRAME_CAPTURED", "job event");
        stores.eventStore().record("printer-1", 999L, "CAMERA_FRAME_CAPTURED", "other event");
        stores.calculationResultStore().save(new CameraCalculationResult(
                null,
                run.requireId(),
                1L,
                0.7,
                false,
                "OK",
                "test result",
                FIXED_INSTANT));
        Path preview = cameraDirectory("printer-1").resolve("latest.jpg");
        Files.createDirectories(preview.getParent());
        Files.write(preview, new byte[] {9});

        CameraJobDeletionReport report = service(stores).delete(
                "printer-1",
                job.requireId(),
                CameraJobDeletionRequest.safeDefault(CameraJobDeletionRequest.CONFIRMATION));

        assertEquals(1, report.deletedSnapshotFiles());
        assertEquals(1, report.deletedSnapshotRows());
        assertEquals(1, report.deletedDeltaFiles());
        assertEquals(1, report.deletedDeltaRows());
        assertEquals(1, report.deletedDeltaSetRows());
        assertEquals(1, report.deletedCalculationRunRows());
        assertEquals(1, report.deletedCalculationResultRows());
        assertEquals(1, report.deletedCameraEventRows());
        assertEquals(1, report.deletedCameraJobRows());
        assertTrue(report.failedFiles().isEmpty());
        assertFalse(Files.exists(snapshotPath));
        assertFalse(Files.exists(deltaPath));
        assertTrue(Files.exists(preview));
        assertTrue(stores.cameraJobStore().findById(job.requireId()).isEmpty());
        assertTrue(stores.deltaSetStore().findByCameraJobId(job.requireId()).isEmpty());
    }

    @Test
    void deleteRejectsWrongPrinter() {
        useDatabase("camera-job-delete-wrong-printer.db");
        Stores stores = stores();
        CameraJob job = saveCameraJob(stores, "printer-1");
        saveCameraSettings("printer-2");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service(stores).delete(
                        "printer-2",
                        job.requireId(),
                        CameraJobDeletionRequest.safeDefault(CameraJobDeletionRequest.CONFIRMATION)));

        assertEquals("camera job not found: 1", exception.getMessage());
        assertTrue(stores.cameraJobStore().findByPrinterIdAndId("printer-1", job.requireId()).isPresent());
    }

    @Test
    void deleteRequiresConfirmation() {
        useDatabase("camera-job-delete-confirmation.db");
        Stores stores = stores();
        CameraJob job = saveCameraJob(stores, "printer-1");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service(stores).delete(
                        "printer-1",
                        job.requireId(),
                        CameraJobDeletionRequest.safeDefault("")));

        assertEquals("requiredConfirmation must be DELETE_CAMERA_JOB", exception.getMessage());
    }

    private CameraJobDeletionService service(Stores stores) {
        return new CameraJobDeletionService(
                new CameraSettingsService(stores.settingsStore(), FIXED_CLOCK),
                stores.cameraJobStore(),
                stores.snapshotEntryStore(),
                stores.deltaSetStore(),
                stores.deltaFrameStore(),
                stores.calculationRunStore(),
                stores.calculationResultStore(),
                stores.eventStore());
    }

    private CameraJob saveCameraJob(Stores stores, String printerId) {
        saveCameraSettings(printerId);
        Path snapshotDirectory = cameraDirectory(printerId).resolve("snapshots").resolve("1");
        return stores.cameraJobStore().save(CameraJob.running(
                printerId,
                null,
                null,
                FIXED_INSTANT,
                10,
                20,
                CameraSourceType.SIMULATED.wireValue(),
                "simulated-camera:" + printerId,
                snapshotDirectory.toString(),
                "test job"));
    }

    private void saveCameraSettings(String printerId) {
        savePrinter(printerId);
        new CameraSettingsStore().save(new CameraSettings(
                printerId,
                true,
                CameraSourceType.SIMULATED,
                "default",
                10,
                20,
                false,
                false,
                false,
                0.85,
                3,
                "ffmpeg",
                "",
                "640x480",
                5000,
                3,
                FIXED_INSTANT));
    }

    private Path saveSnapshot(
            CameraSnapshotEntryStore store,
            String printerId,
            long cameraJobId,
            int sequence) throws Exception {
        Path directory = cameraDirectory(printerId).resolve("snapshots").resolve(Long.toString(cameraJobId));
        Files.createDirectories(directory);
        Path path = directory.resolve("%06d_snapshot.jpg".formatted(sequence));
        Files.write(path, new byte[] {(byte) sequence});
        store.save(CameraSnapshotEntry.captured(
                printerId,
                cameraJobId,
                null,
                path.toString(),
                "image/jpeg",
                Files.size(path),
                FIXED_INSTANT.plusSeconds(sequence),
                FIXED_INSTANT.plusSeconds(sequence),
                CameraSourceType.SIMULATED.wireValue(),
                "snapshot"));
        return path;
    }

    private CameraDeltaSet saveDeltaSet(Stores stores, String printerId, long cameraJobId) {
        return stores.deltaSetStore().save(new CameraDeltaSet(
                null,
                printerId,
                cameraJobId,
                "image-delta",
                1,
                2,
                1,
                FIXED_INSTANT,
                "delta set"));
    }

    private Path saveDeltaFrame(
            Stores stores,
            String printerId,
            long cameraJobId,
            long deltaSetId,
            long fromSnapshotId,
            long toSnapshotId) throws Exception {
        Path directory = cameraDirectory(printerId)
                .resolve("deltas")
                .resolve(Long.toString(cameraJobId))
                .resolve(Long.toString(deltaSetId));
        Files.createDirectories(directory);
        Path path = directory.resolve("000001_000002_delta.jpg");
        Files.write(path, new byte[] {2});
        stores.deltaFrameStore().save(new CameraDeltaFrame(
                null,
                deltaSetId,
                printerId,
                cameraJobId,
                fromSnapshotId,
                toSnapshotId,
                FIXED_INSTANT,
                FIXED_INSTANT.plusSeconds(1),
                path.toString(),
                0.2,
                0.1,
                0.1,
                FIXED_INSTANT));
        return path;
    }

    private CameraCalculationRun saveCalculationRun(
            Stores stores,
            String printerId,
            long cameraJobId,
            long deltaSetId) {
        return stores.calculationRunStore().save(new CameraCalculationRun(
                null,
                printerId,
                cameraJobId,
                deltaSetId,
                "spaghetti-heuristic",
                "{}",
                FIXED_INSTANT,
                1,
                "calculation"));
    }

    private Stores stores() {
        return new Stores(
                new CameraSettingsStore(),
                new CameraJobStore(),
                new CameraSnapshotEntryStore(),
                new CameraDeltaSetStore(),
                new CameraDeltaFrameStore(),
                new CameraCalculationRunStore(),
                new CameraCalculationResultStore(),
                new CameraEventStore());
    }

    private void useDatabase(String fileName) {
        Path dbFile = tempDir.resolve(fileName);
        System.setProperty("spaghettichef.databaseFile", dbFile.toString());
        new DatabaseInitializer().initialize();
    }

    private void savePrinter(String printerId) {
        new PrinterConfigurationStore().save(PrinterRuntimeNodeFactory.create(
                printerId,
                printerId,
                "SIM_PORT",
                "sim",
                printerStorageDirectory(printerId).toString(),
                true));
    }

    private Path printerStorageDirectory(String printerId) {
        return tempDir.resolve("camera-storage").resolve(printerId);
    }

    private Path cameraDirectory(String printerId) {
        return printerStorageDirectory(printerId).resolve("camera");
    }

    private record Stores(
            CameraSettingsStore settingsStore,
            CameraJobStore cameraJobStore,
            CameraSnapshotEntryStore snapshotEntryStore,
            CameraDeltaSetStore deltaSetStore,
            CameraDeltaFrameStore deltaFrameStore,
            CameraCalculationRunStore calculationRunStore,
            CameraCalculationResultStore calculationResultStore,
            CameraEventStore eventStore
    ) {
    }
}
