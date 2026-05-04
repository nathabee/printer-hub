package printerhub.job;

import printerhub.OperationMessages;
import printerhub.monitoring.PrinterMonitoringScheduler;
import printerhub.runtime.PrinterRegistry;
import printerhub.runtime.PrinterRuntimeNode;

public final class PrintJobExecutionService {

    private final PrintJobService printJobService;
    private final PrinterRegistry printerRegistry;
    private final PrinterMonitoringScheduler monitoringScheduler;
    private final PrinterActionGuard printerActionGuard;
    private final PrinterActionMapper printerActionMapper;

    public PrintJobExecutionService(
            PrintJobService printJobService,
            PrinterRegistry printerRegistry,
            PrinterMonitoringScheduler monitoringScheduler,
            PrinterActionGuard printerActionGuard,
            PrinterActionMapper printerActionMapper
    ) {
        if (printJobService == null) {
            throw new IllegalArgumentException(OperationMessages.PRINT_JOB_SERVICE_MUST_NOT_BE_NULL);
        }
        if (printerRegistry == null) {
            throw new IllegalArgumentException(OperationMessages.PRINTER_REGISTRY_MUST_NOT_BE_NULL);
        }
        if (monitoringScheduler == null) {
            throw new IllegalArgumentException(OperationMessages.MONITORING_SCHEDULER_MUST_NOT_BE_NULL);
        }
        if (printerActionGuard == null) {
            throw new IllegalArgumentException(OperationMessages.PRINTER_ACTION_GUARD_MUST_NOT_BE_NULL);
        }
        if (printerActionMapper == null) {
            throw new IllegalArgumentException(OperationMessages.PRINTER_ACTION_MAPPER_MUST_NOT_BE_NULL);
        }

        this.printJobService = printJobService;
        this.printerRegistry = printerRegistry;
        this.monitoringScheduler = monitoringScheduler;
        this.printerActionGuard = printerActionGuard;
        this.printerActionMapper = printerActionMapper;
    }

    public PrinterActionExecutionResult execute(String jobId) {
        PrintJob job = printJobService.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException(OperationMessages.JOB_NOT_FOUND));

        if (job.state() != JobState.ASSIGNED) {
            throw new IllegalStateException(OperationMessages.INVALID_JOB_STATE);
        }

        PrinterRuntimeNode node = printerRegistry.findById(job.printerId())
                .orElseThrow(() -> new IllegalStateException(OperationMessages.PRINTER_NOT_FOUND));

        PrinterActionGuard.GuardDecision decision =
                printerActionGuard.validateForExecution(job, node);

        if (!decision.allowed()) {
            printJobService.recordJobAuditEvent(
                    job.id(),
                    OperationMessages.EVENT_JOB_EXECUTION_FAILED,
                    "Job execution rejected before start: "
                            + OperationMessages.safeDetail(decision.detail(), decision.failureReason().name())
            );

            printJobService.markFailed(job.id(), decision.failureReason(), decision.detail());

            return PrinterActionExecutionResult.failure(
                    null,
                    decision.failureReason(),
                    decision.detail()
            );
        }

        PrinterActionRequest request = PrinterActionRequest.fromJob(job);
        String wireCommand = printerActionMapper.toWireCommand(request);

        node.beginJobExecution(job.id());
        monitoringScheduler.stopMonitoring(node.id());

        try {
            printJobService.markRunning(job.id());
            printJobService.recordJobAuditEvent(
                    job.id(),
                    OperationMessages.EVENT_JOB_EXECUTION_STARTED,
                    "Job execution started: " + wireCommand
            );

            node.printerPort().connect();
            String response = node.printerPort().sendCommand(wireCommand);

            printJobService.recordJobAuditEvent(
                    job.id(),
                    OperationMessages.EVENT_JOB_EXECUTION_SUCCEEDED,
                    "Job execution succeeded: " + wireCommand + " -> "
                            + OperationMessages.safeDetail(response, "no response")
            );

            printJobService.markCompleted(job.id());

            return PrinterActionExecutionResult.success(wireCommand, response);
        } catch (Exception exception) {
            JobFailureReason failureReason = classifyFailure(exception);
            String failureDetail = OperationMessages.safeDetail(
                    exception.getMessage(),
                    JobFailureReason.UNKNOWN.name()
            );

            printJobService.recordJobAuditEvent(
                    job.id(),
                    OperationMessages.EVENT_JOB_EXECUTION_FAILED,
                    "Job execution failed: " + wireCommand + " -> " + failureDetail
            );

            printJobService.markFailed(job.id(), failureReason, failureDetail);

            return PrinterActionExecutionResult.failure(
                    wireCommand,
                    failureReason,
                    failureDetail
            );
        } finally {
            try {
                node.printerPort().disconnect();
            } catch (Exception exception) {
                System.err.println(OperationMessages.failedToDisconnectPrinterNode(
                        node.id(),
                        OperationMessages.safeDetail(
                                exception.getMessage(),
                                OperationMessages.UNKNOWN_RUNTIME_CLOSE_ERROR
                        )
                ));
            }

            node.endJobExecution();

            try {
                if (node.enabled()) {
                    monitoringScheduler.startMonitoring(node);
                }
            } catch (Exception exception) {
                System.err.println(OperationMessages.apiOperationFailed(
                        OperationMessages.safeDetail(
                                exception.getMessage(),
                                OperationMessages.UNKNOWN_API_ERROR
                        )
                ));
            }
        }
    }

    private JobFailureReason classifyFailure(Exception exception) {
        String message = OperationMessages.safeDetail(
                exception == null ? null : exception.getMessage(),
                JobFailureReason.UNKNOWN.name()
        ).toLowerCase();

        if (message.contains("timeout") || message.contains("no response")) {
            return JobFailureReason.TIMEOUT;
        }

        if (message.contains("disconnected")
                || message.contains("not connected")
                || message.contains("not open")
                || message.contains("failed to open serial port")) {
            return JobFailureReason.PRINTER_DISCONNECTED;
        }

        if (message.contains("busy")) {
            return JobFailureReason.PRINTER_BUSY;
        }

        if (message.contains("parameter")) {
            return JobFailureReason.INVALID_PARAMETER;
        }

        return JobFailureReason.COMMUNICATION_FAILURE;
    }
}