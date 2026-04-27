package printerhub.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import printerhub.PrinterSnapshot;
import printerhub.PrinterState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PrinterSnapshotStoreTest {

        private Path databaseFile;
        private PrinterSnapshotStore store;

        @BeforeEach
        void setUp() throws IOException {
                databaseFile = Files.createTempFile("printerhub-snapshot-store-test-", ".db");
                System.setProperty("printerhub.databaseFile", databaseFile.toString());

                DatabaseInitializer.initialize();

                store = new PrinterSnapshotStore();
        }

        @AfterEach
        void tearDown() throws IOException {
                System.clearProperty("printerhub.databaseFile");
                Files.deleteIfExists(databaseFile);
                System.clearProperty("printerhub.snapshot.minIntervalSeconds");
        }

        @Test
        void savePersistsSnapshotAndCanLoadRecentHistory() {
                PrinterSnapshot firstSnapshot = new PrinterSnapshot(
                                PrinterState.CONNECTING,
                                null,
                                null,
                                null,
                                LocalDateTime.parse("2026-04-27T10:00:00"));

                PrinterSnapshot secondSnapshot = new PrinterSnapshot(
                                PrinterState.IDLE,
                                21.8,
                                21.5,
                                "ok T:21.80 /0.00 B:21.52 /0.00 @:0 B@:0",
                                LocalDateTime.parse("2026-04-27T10:01:00"));

                store.save("printer-1", firstSnapshot);
                store.save("printer-1", secondSnapshot);

                List<PrinterSnapshot> history = store.findRecentByPrinterId("printer-1", 10);

                assertEquals(2, history.size());
                assertEquals(PrinterState.IDLE, history.get(0).getState());
                assertEquals("ok T:21.80 /0.00 B:21.52 /0.00 @:0 B@:0", history.get(0).getLastResponse());
                assertEquals(PrinterState.CONNECTING, history.get(1).getState());
        }

        @Test
        void findRecentByPrinterIdUsesLimit() {
                store.save("printer-1", new PrinterSnapshot(
                                PrinterState.CONNECTING,
                                null,
                                null,
                                null,
                                LocalDateTime.parse("2026-04-27T10:00:00")));

                store.save("printer-1", new PrinterSnapshot(
                                PrinterState.IDLE,
                                null,
                                null,
                                "ok",
                                LocalDateTime.parse("2026-04-27T10:01:00")));

                List<PrinterSnapshot> history = store.findRecentByPrinterId("printer-1", 1);

                assertEquals(1, history.size());
                assertEquals(PrinterState.IDLE, history.get(0).getState());
        }

        @Test
        void saveSkipsSameStateUntilMinimumIntervalPasses() {
                System.setProperty("printerhub.snapshot.minIntervalSeconds", "30");

                store.save("printer-1", new PrinterSnapshot(
                                PrinterState.IDLE,
                                21.8,
                                21.5,
                                "ok T:21.80",
                                LocalDateTime.parse("2026-04-27T10:00:00")));

                store.save("printer-1", new PrinterSnapshot(
                                PrinterState.IDLE,
                                22.1,
                                21.7,
                                "ok T:22.10",
                                LocalDateTime.parse("2026-04-27T10:00:10")));

                List<PrinterSnapshot> history = store.findRecentByPrinterId("printer-1", 10);

                assertEquals(1, history.size());
        }

        @Test
        void saveStoresSameStateAfterMinimumIntervalPasses() {
                System.setProperty("printerhub.snapshot.minIntervalSeconds", "30");

                store.save("printer-1", new PrinterSnapshot(
                                PrinterState.IDLE,
                                21.8,
                                21.5,
                                "ok T:21.80",
                                LocalDateTime.parse("2026-04-27T10:00:00")));

                store.save("printer-1", new PrinterSnapshot(
                                PrinterState.IDLE,
                                22.1,
                                21.7,
                                "ok T:22.10",
                                LocalDateTime.parse("2026-04-27T10:00:31")));

                List<PrinterSnapshot> history = store.findRecentByPrinterId("printer-1", 10);

                assertEquals(2, history.size());
        }

        @Test
        void saveAlwaysStoresStateChange() {
                System.setProperty("printerhub.snapshot.minIntervalSeconds", "30");

                store.save("printer-1", new PrinterSnapshot(
                                PrinterState.CONNECTING,
                                null,
                                null,
                                null,
                                LocalDateTime.parse("2026-04-27T10:00:00")));

                store.save("printer-1", new PrinterSnapshot(
                                PrinterState.IDLE,
                                21.8,
                                21.5,
                                "ok T:21.80",
                                LocalDateTime.parse("2026-04-27T10:00:05")));

                List<PrinterSnapshot> history = store.findRecentByPrinterId("printer-1", 10);

                assertEquals(2, history.size());
                assertEquals(PrinterState.IDLE, history.get(0).getState());
        }
}