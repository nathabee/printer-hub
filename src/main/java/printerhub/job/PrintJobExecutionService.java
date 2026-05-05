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
    private final PrinterWorkflowPlanner printerWorkflowPlanner;
    private final PrinterResponseClassifier printerResponseClassifier;

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
        this.printerWorkflowPlanner = new PrinterWorkflowPlanner();
        this.printerResponseClassifier = new PrinterResponseClassifier();
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
                    null,
                    decision.failureReason(),
                    decision.detail()
            );
        }

        PrinterActionRequest request = PrinterActionRequest.fromJob(job);
        PrinterWorkflowPlan workflowPlan = printerWorkflowPlanner.plan(request, printerActionMapper);

        node.beginJobExecution(job.id());
        monitoringScheduler.stopMonitoring(node.id());

        String currentCommand = null;
        String lastResponse = null;

        try {
            printJobService.markRunning(job.id());
            printJobService.recordJobAuditEvent(
                    job.id(),
                    OperationMessages.EVENT_JOB_EXECUTION_STARTED,
                    "Job execution started: " + describePlan(workflowPlan)
            );

            node.printerPort().connect();

            for (PrinterWorkflowStep step : workflowPlan.steps()) {
                currentCommand = step.wireCommand();

                printJobService.recordJobAuditEvent(
                        job.id(),
                        OperationMessages.EVENT_JOB_EXECUTION_STARTED,
                        "Workflow step started: " + step.name() + " -> " + currentCommand
                );

                String response = node.printerPort().sendCommand(currentCommand);
                lastResponse = response;

                PrinterResponseClassifier.ResponseClassification classification =
                        printerResponseClassifier.classifyResponse(currentCommand, response);

                if (!classification.success()) {
                    String failureDetail = classification.detail();

                    printJobService.recordJobAuditEvent(
                            job.id(),
                            OperationMessages.EVENT_JOB_EXECUTION_FAILED,
                            "Workflow step failed: "
                                    + step.name()
                                    + " -> "
                                    + currentCommand
                                    + " | outcome="
                                    + classification.failureReason().name()
                                    + " | response="
                                    + OperationMessages.safeDetail(classification.response(), "no response")
                    );

                    printJobService.markFailed(
                            job.id(),
                            classification.failureReason(),
                            failureDetail
                    );

                    return PrinterActionExecutionResult.failure(
                            currentCommand,
                            classification.response(),
                            classification.failureReason(),
                            failureDetail
                    );
                }

                printJobService.recordJobAuditEvent(
                        job.id(),
                        OperationMessages.EVENT_JOB_EXECUTION_SUCCEEDED,
                        "Workflow step succeeded: "
                                + step.name()
                                + " -> "
                                + currentCommand
                                + " | response="
                                + OperationMessages.safeDetail(classification.response(), "no response")
                );
            }

            printJobService.markCompleted(job.id());

            printJobService.recordJobAuditEvent(
                    job.id(),
                    OperationMessages.EVENT_JOB_EXECUTION_SUCCEEDED,
                    "Job execution completed: "
                            + OperationMessages.safeDetail(currentCommand, "n/a")
                            + " -> "
                            + OperationMessages.safeDetail(lastResponse, "no response")
            );

            return PrinterActionExecutionResult.success(currentCommand, lastResponse);
        } catch (Exception exception) {
            PrinterResponseClassifier.ResponseClassification classification =
                    printerResponseClassifier.classifyException(currentCommand, exception);

            printJobService.recordJobAuditEvent(
                    job.id(),
                    OperationMessages.EVENT_JOB_EXECUTION_FAILED,
                    "Job execution failed: "
                            + OperationMessages.safeDetail(currentCommand, "n/a")
                            + " | outcome="
                            + classification.failureReason().name()
                            + " | detail="
                            + OperationMessages.safeDetail(classification.detail(), JobFailureReason.UNKNOWN.name())
            );

            printJobService.markFailed(
                    job.id(),
                    classification.failureReason(),
                    classification.detail()
            );

            return PrinterActionExecutionResult.failure(
                    currentCommand,
                    classification.response(),
                    classification.failureReason(),
                    classification.detail()
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

    private String describePlan(PrinterWorkflowPlan workflowPlan) {
        StringBuilder builder = new StringBuilder();
        builder.append(workflowPlan.actionType().name()).append(" [");

        boolean first = true;

        for (PrinterWorkflowStep step : workflowPlan.steps()) {
            if (!first) {
                builder.append(" | ");
            }

            builder.append(step.name()).append(": ").append(step.wireCommand());
            first = false;
        }

        builder.append("]");
        return builder.toString();
    }
}