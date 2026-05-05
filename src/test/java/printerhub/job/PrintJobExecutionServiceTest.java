package printerhub.job;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import printerhub.PrinterPort;
import printerhub.config.RuntimeDefaults;
import printerhub.monitoring.PrinterMonitoringScheduler;
import printerhub.persistence.DatabaseInitializer;
import printerhub.persistence.PrintJobStore;
import printerhub.persistence.PrinterEvent;
import printerhub.persistence.PrinterEventStore;
import printerhub.runtime.PrinterRegistry;
import printerhub.runtime.PrinterRuntimeNode;
import printerhub.runtime.PrinterRuntimeStateCache;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PrintJobExecutionServiceTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        System.clearProperty(RuntimeDefaults.DATABASE_FILE_PROPERTY);
    }

    @Test
    void executeAssignedReadFirmwareJobSucceedsAndCompletesLifecycle() {
        initializeDatabase("job-execution-success.db");

        PrintJobStore store = new PrintJobStore();
        PrinterEventStore eventStore = new PrinterEventStore();
        Clock clock = Clock.fixed(Instant.parse("2026-05-04T08:00:00Z"), ZoneOffset.UTC);

        PrintJobService jobService = new PrintJobService(store, eventStore, clock);

        PrinterRegistry registry = new PrinterRegistry();
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();
        PrinterMonitoringScheduler scheduler = new PrinterMonitoringScheduler(registry, stateCache);

        try {
            PrinterRuntimeNode node = new PrinterRuntimeNode(
                    "printer-1",
                    "Printer 1",
                    "SIM_PORT",
                    "sim",
                    new SingleResponsePrinterPort("ok FIRMWARE_NAME:Marlin"),
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
            assertEquals("SUCCESS", result.outcome());
            assertNull(result.failureReason());
            assertNull(result.failureDetail());

            PrintJob loaded = store.findById(job.id()).orElseThrow();
            assertEquals(JobState.COMPLETED, loaded.state());
            assertFalse(node.executionInProgress());
            assertNull(node.activeJobId());

            List<PrinterEvent> events = eventStore.findRecentByJobId(job.id(), 20);
            assertTrue(events.stream().anyMatch(event ->
                    event.message() != null && event.message().contains("read-firmware-info")));
        } finally {
            scheduler.stop();
        }
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

        try {
            PrinterRuntimeNode node = new PrinterRuntimeNode(
                    "printer-1",
                    "Printer 1",
                    "SIM_PORT",
                    "sim",
                    new SingleResponsePrinterPort("ok"),
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
            assertNull(result.wireCommand());
            assertNull(result.response());
            assertEquals("PRINTER_DISABLED", result.outcome());
            assertEquals(JobFailureReason.PRINTER_DISABLED, result.failureReason());

            PrintJob loaded = store.findById(job.id()).orElseThrow();
            assertEquals(JobState.FAILED, loaded.state());
            assertEquals(JobFailureReason.PRINTER_DISABLED.name(), loaded.failureReason());
        } finally {
            scheduler.stop();
        }
    }

    @Test
    void executeWorkflowExceptionMarksJobFailedAndClearsNodeBusyState() {
        initializeDatabase("job-execution-io-fail.db");

        PrintJobStore store = new PrintJobStore();
        PrinterEventStore eventStore = new PrinterEventStore();
        Clock clock = Clock.fixed(Instant.parse("2026-05-04T08:00:00Z"), ZoneOffset.UTC);

        PrintJobService jobService = new PrintJobService(store, eventStore, clock);

        PrinterRegistry registry = new PrinterRegistry();
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();
        PrinterMonitoringScheduler scheduler = new PrinterMonitoringScheduler(registry, stateCache);

        try {
            PrinterRuntimeNode node = new PrinterRuntimeNode(
                    "printer-1",
                    "Printer 1",
                    "SIM_PORT",
                    "sim",
                    new ExceptionOnSecondCommandPrinterPort("ok X:0.00 Y:0.00 Z:0.00", "communication failure"),
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
            assertEquals("G28", result.wireCommand());
            assertNull(result.response());
            assertEquals("COMMUNICATION_FAILURE", result.outcome());
            assertEquals(JobFailureReason.COMMUNICATION_FAILURE, result.failureReason());
            assertTrue(result.failureDetail().contains("communication failure"));

            PrintJob loaded = store.findById(job.id()).orElseThrow();
            assertEquals(JobState.FAILED, loaded.state());
            assertEquals(JobFailureReason.COMMUNICATION_FAILURE.name(), loaded.failureReason());
            assertFalse(node.executionInProgress());
            assertNull(node.activeJobId());
        } finally {
            scheduler.stop();
        }
    }

    @Test
    void executeHomeAxesWithPrinterReportedFailurePersistsActualResponse() {
        initializeDatabase("job-execution-home-axes-printer-failure.db");

        PrintJobStore store = new PrintJobStore();
        PrinterEventStore eventStore = new PrinterEventStore();
        Clock clock = Clock.fixed(Instant.parse("2026-05-04T08:00:00Z"), ZoneOffset.UTC);

        PrintJobService jobService = new PrintJobService(store, eventStore, clock);

        PrinterRegistry registry = new PrinterRegistry();
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();
        PrinterMonitoringScheduler scheduler = new PrinterMonitoringScheduler(registry, stateCache);

        try {
            PrinterRuntimeNode node = new PrinterRuntimeNode(
                    "printer-1",
                    "Printer 1",
                    "/dev/ttyUSB0",
                    "real",
                    new SequencePrinterPort(
                            "ok X:0.00 Y:0.00 Z:0.00",
                            "echo:busy: processing echo:Homing Failed Error:Printer halted. kill() called!"
                    ),
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
            assertEquals("G28", result.wireCommand());
            assertEquals(
                    "echo:busy: processing echo:Homing Failed Error:Printer halted. kill() called!",
                    result.response()
            );
            assertEquals("PRINTER_REPORTED_FAILURE", result.outcome());
            assertEquals(JobFailureReason.PRINTER_REPORTED_FAILURE, result.failureReason());
            assertNotNull(result.failureDetail());
            assertTrue(result.failureDetail().contains("Homing Failed"));
            assertTrue(result.failureDetail().contains("Printer halted"));

            PrintJob loaded = store.findById(job.id()).orElseThrow();
            assertEquals(JobState.FAILED, loaded.state());
            assertEquals(JobFailureReason.PRINTER_REPORTED_FAILURE.name(), loaded.failureReason());
            assertNotNull(loaded.failureDetail());
            assertTrue(loaded.failureDetail().contains("Homing Failed"));
            assertTrue(loaded.failureDetail().contains("Printer halted"));

            List<PrinterEvent> events = eventStore.findRecentByJobId(job.id(), 20);
            assertTrue(events.stream().anyMatch(event ->
                    event.message() != null
                            && event.message().contains("Workflow step started: validate-position-before-home -> M114")));
            assertTrue(events.stream().anyMatch(event ->
                    event.message() != null
                            && event.message().contains("Workflow step started: home-axes -> G28")));
            assertTrue(events.stream().anyMatch(event ->
                    event.message() != null
                            && event.message().contains("outcome=PRINTER_REPORTED_FAILURE")));
            assertTrue(events.stream().anyMatch(event ->
                    event.message() != null
                            && event.message().contains("Homing Failed Error:Printer halted. kill() called!")));

            assertFalse(node.executionInProgress());
            assertNull(node.activeJobId());
        } finally {
            scheduler.stop();
        }
    }

    private void initializeDatabase(String fileName) {
        String databaseFile = tempDir.resolve(fileName).toString();
        System.setProperty(RuntimeDefaults.DATABASE_FILE_PROPERTY, databaseFile);
        new DatabaseInitializer().initialize();
    }

    private static final class SingleResponsePrinterPort implements PrinterPort {

        private final String response;

        private SingleResponsePrinterPort(String response) {
            this.response = response;
        }

        @Override
        public void connect() {
        }

        @Override
        public String sendCommand(String command) {
            return response;
        }

        @Override
        public void disconnect() {
        }
    }

    private static final class SequencePrinterPort implements PrinterPort {

        private final String[] responses;
        private int index = 0;

        private SequencePrinterPort(String... responses) {
            this.responses = responses;
        }

        @Override
        public void connect() {
        }

        @Override
        public String sendCommand(String command) {
            if (index >= responses.length) {
                return responses[responses.length - 1];
            }

            String response = responses[index];
            index++;
            return response;
        }

        @Override
        public void disconnect() {
        }
    }

    private static final class ExceptionOnSecondCommandPrinterPort implements PrinterPort {

        private final String firstResponse;
        private final String exceptionMessage;
        private int commandCount = 0;

        private ExceptionOnSecondCommandPrinterPort(
                String firstResponse,
                String exceptionMessage
        ) {
            this.firstResponse = firstResponse;
            this.exceptionMessage = exceptionMessage;
        }

        @Override
        public void connect() {
        }

        @Override
        public String sendCommand(String command) {
            commandCount++;

            if (commandCount == 1) {
                return firstResponse;
            }

            throw new IllegalStateException(exceptionMessage);
        }

        @Override
        public void disconnect() {
        }
    }
}