package printerhub.job;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import printerhub.OperationMessages;
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
import printerhub.persistence.PrintJobExecutionStepStore;

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
                    true);
            registry.register(node);

            PrintJob job = jobService.create(
                    "Read firmware",
                    JobType.READ_FIRMWARE_INFO,
                    "printer-1",
                    null,
                    null);

            PrintJobExecutionService executionService = new PrintJobExecutionService(
                    jobService,
                    registry,
                    scheduler,
                    new PrinterActionGuard(),
                    new PrinterActionMapper(),
                    new PrintJobExecutionStepStore());

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
            assertTrue(events.stream()
                    .anyMatch(event -> event.message() != null && event.message().contains("read-firmware-info")));
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
                    false);
            registry.register(node);

            PrintJob job = jobService.create(
                    "Home axes",
                    JobType.HOME_AXES,
                    "printer-1",
                    null,
                    null);

            PrintJobExecutionService executionService = new PrintJobExecutionService(
                    jobService,
                    registry,
                    scheduler,
                    new PrinterActionGuard(),
                    new PrinterActionMapper(),
                    new PrintJobExecutionStepStore());
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
                    true);
            registry.register(node);

            PrintJob job = jobService.create(
                    "Home axes",
                    JobType.HOME_AXES,
                    "printer-1",
                    null,
                    null);

            PrintJobExecutionService executionService = new PrintJobExecutionService(
                    jobService,
                    registry,
                    scheduler,
                    new PrinterActionGuard(),
                    new PrinterActionMapper(),
                    new PrintJobExecutionStepStore());

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
                            "echo:busy: processing echo:Homing Failed Error:Printer halted. kill() called!"),
                    true);
            registry.register(node);

            PrintJob job = jobService.create(
                    "Home axes",
                    JobType.HOME_AXES,
                    "printer-1",
                    null,
                    null);
            PrintJobExecutionService executionService = new PrintJobExecutionService(
                    jobService,
                    registry,
                    scheduler,
                    new PrinterActionGuard(),
                    new PrinterActionMapper(),
                    new PrintJobExecutionStepStore());

            PrinterActionExecutionResult result = executionService.execute(job.id());

            assertFalse(result.success());
            assertEquals("G28", result.wireCommand());
            assertEquals(
                    "echo:busy: processing echo:Homing Failed Error:Printer halted. kill() called!",
                    result.response());
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
            assertTrue(events.stream().anyMatch(event -> event.message() != null
                    && event.message().contains("Workflow step started: validate-position-before-home -> M114")));
            assertTrue(events.stream().anyMatch(event -> event.message() != null
                    && event.message().contains("Workflow step started: home-axes -> G28")));
            assertTrue(events.stream().anyMatch(event -> event.message() != null
                    && event.message().contains("outcome=PRINTER_REPORTED_FAILURE")));
            assertTrue(events.stream().anyMatch(event -> event.message() != null
                    && event.message().contains("Homing Failed Error:Printer halted. kill() called!")));

            assertFalse(node.executionInProgress());
            assertNull(node.activeJobId());
        } finally {
            scheduler.stop();
        }
    }

    @Test
    void executeHomeAxesTreatsBusyThenOkAsInProgressThenSuccess() {
        initializeDatabase("job-execution-home-axes-busy-ok.db");

        PrintJobStore store = new PrintJobStore();
        PrinterEventStore eventStore = new PrinterEventStore();
        PrintJobExecutionStepStore stepStore = new PrintJobExecutionStepStore();
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
                            "echo:busy: processing\nok"),
                    true);
            registry.register(node);

            PrintJob job = jobService.create(
                    "Home axes",
                    JobType.HOME_AXES,
                    "printer-1",
                    null,
                    null);

            PrintJobExecutionService executionService = new PrintJobExecutionService(
                    jobService,
                    registry,
                    scheduler,
                    new PrinterActionGuard(),
                    new PrinterActionMapper(),
                    stepStore);

            PrinterActionExecutionResult result = executionService.execute(job.id());

            assertTrue(result.success());
            assertEquals("G28", result.wireCommand());
            assertEquals("echo:busy: processing\nok", result.response());

            PrintJob loaded = store.findById(job.id()).orElseThrow();
            assertEquals(JobState.COMPLETED, loaded.state());

            List<PrinterEvent> events = eventStore.findRecentByJobId(job.id(), 20);
            assertTrue(events.stream().anyMatch(event ->
                    OperationMessages.EVENT_JOB_EXECUTION_IN_PROGRESS.equals(event.eventType())
                            && event.message() != null
                            && event.message().contains("echo:busy: processing")));
            assertTrue(events.stream().anyMatch(event ->
                    OperationMessages.EVENT_JOB_EXECUTION_SUCCEEDED.equals(event.eventType())
                            && event.message() != null
                            && event.message().contains("Job execution completed: G28")));
        } finally {
            scheduler.stop();
        }
    }

    @Test
    void executeAssignedReadFirmwareJobPersistsStructuredExecutionStep() {
        initializeDatabase("job-execution-step-success.db");

        PrintJobStore store = new PrintJobStore();
        PrinterEventStore eventStore = new PrinterEventStore();
        PrintJobExecutionStepStore stepStore = new PrintJobExecutionStepStore();
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
                    true);
            registry.register(node);

            PrintJob job = jobService.create(
                    "Read firmware",
                    JobType.READ_FIRMWARE_INFO,
                    "printer-1",
                    null,
                    null);

            PrintJobExecutionService executionService = new PrintJobExecutionService(
                    jobService,
                    registry,
                    scheduler,
                    new PrinterActionGuard(),
                    new PrinterActionMapper(),
                    stepStore);

            PrinterActionExecutionResult result = executionService.execute(job.id());

            assertTrue(result.success());

            List<PrintJobExecutionStep> steps = stepStore.findByJobId(job.id());
            assertEquals(1, steps.size());

            PrintJobExecutionStep step = steps.get(0);
            assertEquals(job.id(), step.jobId());
            assertEquals(0, step.stepIndex());
            assertEquals("read-firmware-info", step.stepName());
            assertEquals("M115", step.wireCommand());
            assertEquals("ok FIRMWARE_NAME:Marlin", step.response());
            assertEquals("SUCCESS", step.outcome());
            assertTrue(step.success());
            assertNull(step.failureReason());
            assertNull(step.failureDetail());
        } finally {
            scheduler.stop();
        }
    }

    @Test
    void executePrintFileJobCompletesAsPreparedMetadataWithoutSendingGcode() {
        initializeDatabase("job-execution-print-file-prepared.db");

        PrintJobStore store = new PrintJobStore();
        PrinterEventStore eventStore = new PrinterEventStore();
        PrintJobExecutionStepStore stepStore = new PrintJobExecutionStepStore();
        Clock clock = Clock.fixed(Instant.parse("2026-05-04T08:00:00Z"), ZoneOffset.UTC);

        PrintJobService jobService = new PrintJobService(store, eventStore, clock);

        PrinterRegistry registry = new PrinterRegistry();
        PrinterRuntimeStateCache stateCache = new PrinterRuntimeStateCache();
        PrinterMonitoringScheduler scheduler = new PrinterMonitoringScheduler(registry, stateCache);
        CountingPrinterPort printerPort = new CountingPrinterPort();

        try {
            PrinterRuntimeNode node = new PrinterRuntimeNode(
                    "printer-1",
                    "Printer 1",
                    "SIM_PORT",
                    "sim",
                    printerPort,
                    true);
            registry.register(node);

            PrintJob job = jobService.create(
                    "Print cube",
                    JobType.PRINT_FILE,
                    "printer-1",
                    "print-file-1",
                    null,
                    null);

            PrintJobExecutionService executionService = new PrintJobExecutionService(
                    jobService,
                    registry,
                    scheduler,
                    new PrinterActionGuard(),
                    new PrinterActionMapper(),
                    stepStore);

            PrinterActionExecutionResult result = executionService.execute(job.id());

            assertTrue(result.success());
            assertNull(result.wireCommand());
            assertEquals(0, printerPort.commandCount());

            PrintJob loaded = store.findById(job.id()).orElseThrow();
            assertEquals(JobState.COMPLETED, loaded.state());
            assertEquals("print-file-1", loaded.printFileId());

            List<PrintJobExecutionStep> steps = stepStore.findByJobId(job.id());
            assertEquals(1, steps.size());
            assertEquals("file-backed-print-prepared", steps.get(0).stepName());
            assertNull(steps.get(0).wireCommand());
            assertTrue(steps.get(0).success());
        } finally {
            scheduler.stop();
        }
    }

    @Test
    void executeHomeAxesWithPrinterReportedFailurePersistsStructuredFailureStep() {
        initializeDatabase("job-execution-step-printer-failure.db");

        PrintJobStore store = new PrintJobStore();
        PrinterEventStore eventStore = new PrinterEventStore();
        PrintJobExecutionStepStore stepStore = new PrintJobExecutionStepStore();
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
                            "echo:busy: processing echo:Homing Failed Error:Printer halted. kill() called!"),
                    true);
            registry.register(node);

            PrintJob job = jobService.create(
                    "Home axes",
                    JobType.HOME_AXES,
                    "printer-1",
                    null,
                    null);

            PrintJobExecutionService executionService = new PrintJobExecutionService(
                    jobService,
                    registry,
                    scheduler,
                    new PrinterActionGuard(),
                    new PrinterActionMapper(),
                    stepStore);

            PrinterActionExecutionResult result = executionService.execute(job.id());

            assertFalse(result.success());

            List<PrintJobExecutionStep> steps = stepStore.findByJobId(job.id());
            assertEquals(2, steps.size());

            PrintJobExecutionStep validationStep = steps.get(0);
            assertEquals(0, validationStep.stepIndex());
            assertEquals("validate-position-before-home", validationStep.stepName());
            assertEquals("M114", validationStep.wireCommand());
            assertEquals("ok X:0.00 Y:0.00 Z:0.00", validationStep.response());
            assertEquals("SUCCESS", validationStep.outcome());
            assertTrue(validationStep.success());
            assertNull(validationStep.failureReason());
            assertNull(validationStep.failureDetail());

            PrintJobExecutionStep failedStep = steps.get(1);
            assertEquals(1, failedStep.stepIndex());
            assertEquals("home-axes", failedStep.stepName());
            assertEquals("G28", failedStep.wireCommand());
            assertEquals(
                    "echo:busy: processing echo:Homing Failed Error:Printer halted. kill() called!",
                    failedStep.response());
            assertEquals("PRINTER_REPORTED_FAILURE", failedStep.outcome());
            assertFalse(failedStep.success());
            assertEquals(JobFailureReason.PRINTER_REPORTED_FAILURE.name(), failedStep.failureReason());
            assertNotNull(failedStep.failureDetail());
            assertTrue(failedStep.failureDetail().contains("Homing Failed"));
            assertTrue(failedStep.failureDetail().contains("Printer halted"));
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

    private static final class CountingPrinterPort implements PrinterPort {

        private int commandCount = 0;

        @Override
        public void connect() {
        }

        @Override
        public String sendCommand(String command) {
            commandCount++;
            return "ok";
        }

        @Override
        public void disconnect() {
        }

        private int commandCount() {
            return commandCount;
        }
    }

    private static final class ExceptionOnSecondCommandPrinterPort implements PrinterPort {

        private final String firstResponse;
        private final String exceptionMessage;
        private int commandCount = 0;

        private ExceptionOnSecondCommandPrinterPort(
                String firstResponse,
                String exceptionMessage) {
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
