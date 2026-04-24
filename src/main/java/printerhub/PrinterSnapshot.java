package printerhub;

import java.time.LocalDateTime;

public final class PrinterSnapshot {

    private final PrinterState state;
    private final Double hotendTemperature;
    private final Double bedTemperature;
    private final String lastResponse;
    private final LocalDateTime updatedAt;

    public PrinterSnapshot(PrinterState state,
                           Double hotendTemperature,
                           Double bedTemperature,
                           String lastResponse,
                           LocalDateTime updatedAt) {
        this.state = state == null ? PrinterState.UNKNOWN : state;
        this.hotendTemperature = hotendTemperature;
        this.bedTemperature = bedTemperature;
        this.lastResponse = lastResponse;
        this.updatedAt = updatedAt == null ? LocalDateTime.now() : updatedAt;
    }

    public PrinterState getState() {
        return state;
    }

    public Double getHotendTemperature() {
        return hotendTemperature;
    }

    public Double getBedTemperature() {
        return bedTemperature;
    }

    public String getLastResponse() {
        return lastResponse;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "PrinterSnapshot{"
                + "state=" + state
                + ", hotendTemperature=" + hotendTemperature
                + ", bedTemperature=" + bedTemperature
                + ", updatedAt=" + updatedAt
                + '}';
    }
}