package printerhub.serial;

import java.util.Locale;

public enum SimulationProfile {
    NORMAL("sim"),
    DISCONNECTED("sim-disconnected"),
    TIMEOUT("sim-timeout"),
    ERROR("sim-error");

    private static final String CONNECT_PROPERTY = "printerhub.sim.connect";
    private static final String NO_RESPONSE_M105_PROPERTY = "printerhub.sim.noresponse.M105";
    private static final String RESPONSE_M105_PROPERTY = "printerhub.sim.response.M105";

    private final String modeName;

    SimulationProfile(String modeName) {
        this.modeName = modeName;
    }

    public String getModeName() {
        return modeName;
    }

    public static boolean isSimulationMode(String mode) {
        return fromMode(mode) != null;
    }

    public static SimulationProfile fromMode(String mode) {
        String normalized = mode == null ? "" : mode.trim().toLowerCase(Locale.ROOT);

        return switch (normalized) {
            case "sim", "simulated" -> NORMAL;
            case "sim-disconnected" -> DISCONNECTED;
            case "sim-timeout" -> TIMEOUT;
            case "sim-error" -> ERROR;
            default -> null;
        };
    }
    
    public void apply() {
        switch (this) {
            case NORMAL -> {
                // Keep explicit simulation overrides.
                // Normal simulated mode must still support system-property based tests and demos.
            }
            case DISCONNECTED -> {
                clearManagedProperties();
                System.setProperty(CONNECT_PROPERTY, "false");
            }
            case TIMEOUT -> {
                clearManagedProperties();
                System.setProperty(NO_RESPONSE_M105_PROPERTY, "true");
            }
            case ERROR -> {
                clearManagedProperties();
                System.setProperty(
                        RESPONSE_M105_PROPERTY,
                        "Error: Simulated printer failure"
                );
            }
        }
    }

    public static void clearManagedProperties() {
        System.clearProperty(CONNECT_PROPERTY);
        System.clearProperty(NO_RESPONSE_M105_PROPERTY);
        System.clearProperty(RESPONSE_M105_PROPERTY);
    }
}