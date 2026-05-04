package printerhub.job;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import printerhub.PrinterPort;
import printerhub.config.RuntimeDefaults;
import printerhub.monitoring.PrinterMonitoringScheduler;
import printerhub.persistence.DatabaseInitializer;
import printerhub.persistence.PrintJobStore;
import printerhub.persistence.PrinterEventStore;
import printerhub.runtime.PrinterRegistry;
import printerhub.runtime.PrinterRuntimeNode;
import printerhub.runtime.PrinterRuntimeStateCache;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class PrintJobExecutionServiceTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        System.clearProperty(RuntimeDefaults.DATABASE_FILE_PROPERTY);
    }

    @Test
    void executeAssignedJobSucceedsAndCompletesLifecycle() {
        initializeDatabase("job-execution-success.db");

        PrintJobStore store = new PrintJobStore();
        PrinterEventStore eventStore = new PrinterEventStore();
        Clock clock = Clock.fixed(Instant.parse("2026-05-04T08:00:00Z"), ZoneOffset.UTC);

        PrintJobService jobService = new PrintJobService(store, eventStore, clock);

        PrinterRegistry registry = new PrinterRegistry();
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();
        PrinterMonitoringScheduler scheduler = new PrinterMonitoringScheduler(registry, stateCache);

        PrinterRuntimeNode node = new PrinterRuntimeNode(
                "printer-1",
                "Printer 1",
                "SIM_PORT",
                "sim",
                new SuccessPrinterPort(),
                true
        );
        registry.register(node);

        PrintJob job = jobService.create(
                "Read firmware",
                JobType.READ_FIRMWARE_INFO,
                "printer-1",
                null,
                null
        );

        PrintJobExecutionService executionService = new PrintJobExecutionService(
                jobService,
                registry,
                scheduler,
                new PrinterActionGuard(),
                new PrinterActionMapper()
        );

        PrinterActionExecutionResult result = executionService.execute(job.id());

        assertTrue(result.success());
        assertEquals("M115", result.wireCommand());
        assertEquals("ok FIRMWARE_NAME:Marlin", result.response());

        PrintJob loaded = store.findById(job.id()).orElseThrow();
        assertEquals(JobState.COMPLETED, loaded.state());
        assertFalse(node.executionInProgress());
        assertNull(node.activeJobId());
    }

    @Test
    void executeRejectedByGuardMarksJobFailed() {
        initializeDatabase("job-execution-guard-fail.db");

        PrintJobStore store = new PrintJobStore();
        PrinterEventStore eventStore = new PrinterEventStore();
        Clock clock = Clock.fixed(Instant.parse("2026-05-04T08:00:00Z"), ZoneOffset.UTC);

        PrintJobService jobService = new PrintJobService(store, eventStore, clock);

        PrinterRegistry registry = new PrinterRegistry();
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();
        PrinterMonitoringScheduler scheduler = new PrinterMonitoringScheduler(registry, stateCache);

        PrinterRuntimeNode node = new PrinterRuntimeNode(
                "printer-1",
                "Printer 1",
                "SIM_PORT",
                "sim",
                new SuccessPrinterPort(),
                false
        );
        registry.register(node);

        PrintJob job = jobService.create(
                "Home axes",
                JobType.HOME_AXES,
                "printer-1",
                null,
                null
        );

        PrintJobExecutionService executionService = new PrintJobExecutionService(
                jobService,
                registry,
                scheduler,
                new PrinterActionGuard(),
                new PrinterActionMapper()
        );

        PrinterActionExecutionResult result = executionService.execute(job.id());

        assertFalse(result.success());
        assertEquals(JobFailureReason.PRINTER_DISABLED, result.failureReason());

        PrintJob loaded = store.findById(job.id()).orElseThrow();
        assertEquals(JobState.FAILED, loaded.state());
    }

    @Test
    void executeCommunicationFailureMarksJobFailedAndClearsNodeBusyState() {
        initializeDatabase("job-execution-io-fail.db");

        PrintJobStore store = new PrintJobStore();
        PrinterEventStore eventStore = new PrinterEventStore();
        Clock clock = Clock.fixed(Instant.parse("2026-05-04T08:00:00Z"), ZoneOffset.UTC);

        PrintJobService jobService = new PrintJobService(store, eventStore, clock);

        PrinterRegistry registry = new PrinterRegistry();
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();
        PrinterMonitoringScheduler scheduler = new PrinterMonitoringScheduler(registry, stateCache);

        PrinterRuntimeNode node = new PrinterRuntimeNode(
                "printer-1",
                "Printer 1",
                "SIM_PORT",
                "sim",
                new FailingPrinterPort(),
                true
        );
        registry.register(node);

        PrintJob job = jobService.create(
                "Home axes",
                JobType.HOME_AXES,
                "printer-1",
                null,
                null
        );

        PrintJobExecutionService executionService = new PrintJobExecutionService(
                jobService,
                registry,
                scheduler,
                new PrinterActionGuard(),
                new PrinterActionMapper()
        );

        PrinterActionExecutionResult result = executionService.execute(job.id());

        assertFalse(result.success());
        assertEquals(JobFailureReason.COMMUNICATION_FAILURE, result.failureReason());

        PrintJob loaded = store.findById(job.id()).orElseThrow();
        assertEquals(JobState.FAILED, loaded.state());
        assertFalse(node.executionInProgress());
        assertNull(node.activeJobId());
    }

    private void initializeDatabase(String fileName) {
        String databaseFile = tempDir.resolve(fileName).toString();
        System.setProperty(RuntimeDefaults.DATABASE_FILE_PROPERTY, databaseFile);
        new DatabaseInitializer().initialize();
    }

    private static final class SuccessPrinterPort implements PrinterPort {
        @Override
        public void connect() {
        }

        @Override
        public String sendCommand(String command) {
            return "ok FIRMWARE_NAME:Marlin";
        }

        @Override
        public void disconnect() {
        }
    }

    private static final class FailingPrinterPort implements PrinterPort {
        @Override
        public void connect() {
        }

        @Override
        public String sendCommand(String command) {
            throw new IllegalStateException("communication failure");
        }

        @Override
        public void disconnect() {
        }
    }
}