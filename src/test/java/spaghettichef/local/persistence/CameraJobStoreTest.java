package spaghettichef.local.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CameraJobStoreTest {

    private static final Instant STARTED_AT = Instant.parse("2026-06-03T12:00:00Z");

    @TempDir
    Path tempDir;

    @AfterEach
    void clearDatabaseProperty() {
        System.clearProperty("spaghettichef.databaseFile");
    }

    @Test
    void saveAllocatesIdsAcrossPrinters() {
        useDatabase("camera-jobs-cross-printer-ids.db");

        CameraJobStore store = new CameraJobStore();

        CameraJob firstPrinterJob = store.save(runningJob("p1"));
        CameraJob secondPrinterJob = store.save(runningJob("pclinux"));

        assertEquals(1L, firstPrinterJob.requireId());
        assertEquals(2L, secondPrinterJob.requireId());
    }

    private static CameraJob runningJob(String printerId) {
        return CameraJob.running(
                printerId,
                null,
                null,
                STARTED_AT,
                5,
                25,
                "simulated",
                "simulated-camera:" + printerId,
                "/tmp/camera/" + printerId + "/snapshots/pending",
                "test camera job");
    }

    private void useDatabase(String fileName) {
        Path dbFile = tempDir.resolve(fileName);
        System.setProperty("spaghettichef.databaseFile", dbFile.toString());
        new DatabaseInitializer().initialize();
    }
}
