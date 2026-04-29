package printerhub.config;

public final class PrinterProtocolDefaults {

    private PrinterProtocolDefaults() {
    }

    public static final String DEFAULT_STATUS_COMMAND = "M105";
    public static final double DEFAULT_HEATING_TEMPERATURE_THRESHOLD = 45.0;

    public static final String SIM_MODE = "sim";
    public static final String SIM_DISCONNECTED_MODE = "sim-disconnected";
    public static final String SIM_TIMEOUT_MODE = "sim-timeout";
    public static final String SIM_ERROR_MODE = "sim-error";

    public static final String SIMULATED_RESPONSE_DEFAULT_OK = "ok";
    public static final String SIMULATED_RESPONSE_M105 = "ok T:21.80 /0.00 B:21.52 /0.00 @:0 B@:0";
    public static final String SIMULATED_RESPONSE_M114 = "X:0.00 Y:0.00 Z:0.00 E:0.00 Count X:0 Y:0 Z:0";
    public static final String SIMULATED_RESPONSE_M115 = "FIRMWARE_NAME:Marlin SIMULATED PROTOCOL_VERSION:1.0 MACHINE_TYPE:Ender-3 V2 Neo EXTRUDER_COUNT:1";
}