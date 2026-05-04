package printerhub.job;

public final class PrinterActionExecutionResult {

    private final boolean success;
    private final String wireCommand;
    private final String response;
    private final JobFailureReason failureReason;
    private final String failureDetail;

    public PrinterActionExecutionResult(
            boolean success,
            String wireCommand,
            String response,
            JobFailureReason failureReason,
            String failureDetail
    ) {
        this.success = success;
        this.wireCommand = wireCommand;
        this.response = response;
        this.failureReason = failureReason;
        this.failureDetail = failureDetail;
    }

    public static PrinterActionExecutionResult success(
            String wireCommand,
            String response
    ) {
        return new PrinterActionExecutionResult(
                true,
                wireCommand,
                response,
                null,
                null
        );
    }

    public static PrinterActionExecutionResult failure(
            String wireCommand,
            JobFailureReason failureReason,
            String failureDetail
    ) {
        return new PrinterActionExecutionResult(
                false,
                wireCommand,
                null,
                failureReason,
                failureDetail
        );
    }

    public boolean success() {
        return success;
    }

    public String wireCommand() {
        return wireCommand;
    }

    public String response() {
        return response;
    }

    public JobFailureReason failureReason() {
        return failureReason;
    }

    public String failureDetail() {
        return failureDetail;
    }
}