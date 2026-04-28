package printerhub;

import java.time.Instant;

public final class PrinterSnapshot {

    private final PrinterState state;
    private final String lastResponse;
    private final String errorMessage;
    private final Instant updatedAt;

    private PrinterSnapshot(
            PrinterState state,
            String lastResponse,
            String errorMessage,
            Instant updatedAt
    ) {
        this.state = state;
        this.lastResponse = lastResponse;
        this.errorMessage = errorMessage;
        this.updatedAt = updatedAt;
    }

    public static PrinterSnapshot fromResponse(
            PrinterState state,
            String lastResponse,
            Instant updatedAt
    ) {
        return new PrinterSnapshot(state, lastResponse, null, updatedAt);
    }

    public static PrinterSnapshot error(
            PrinterState state,
            String errorMessage,
            Instant updatedAt
    ) {
        return new PrinterSnapshot(state, null, errorMessage, updatedAt);
    }

    public PrinterState state() {
        return state;
    }

    public String lastResponse() {
        return lastResponse;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}