package printerhub;

public enum PrinterState {
    DISCONNECTED,
    CONNECTING,
    IDLE,
    HEATING,
    PRINTING,
    ERROR,
    UNKNOWN
}