package printerhub.config;

public final class SerialDefaults {

    private SerialDefaults() {
    }

    public static final int DEFAULT_BAUD_RATE = 115200;
    public static final int READ_TIMEOUT_MS = 2000;
    public static final int LONG_RUNNING_COMMAND_READ_TIMEOUT_MS = 60000;
    public static final int QUIET_PERIOD_MS = 200;
    public static final int READ_ACTIVITY_SLEEP_MS = 50;
    public static final int READ_IDLE_SLEEP_MS = 25;
    public static final String DEFAULT_COMMAND_TERMINATOR = "\n";


}
