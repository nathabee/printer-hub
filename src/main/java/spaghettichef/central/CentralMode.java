package spaghettichef.central;

import java.util.Locale;

public final class CentralMode {
    public static final String MODE_PROPERTY = "spaghettichef.mode";
    public static final String CENTRAL_MODE_PROPERTY = "spaghettichef.centralMode";
    public static final String MODE_ENV = "SPAGHETTICHEF_MODE";
    public static final String CENTRAL_MODE_ENV = "CENTRAL_MODE";

    private CentralMode() {
    }

    public static boolean enabled() {
        return isCentralMode(System.getProperty(MODE_PROPERTY))
                || isTrue(System.getProperty(CENTRAL_MODE_PROPERTY))
                || isCentralMode(System.getenv(MODE_ENV))
                || isTrue(System.getenv(CENTRAL_MODE_ENV));
    }

    private static boolean isCentralMode(String value) {
        return value != null && "central".equals(value.trim().toLowerCase(Locale.ROOT));
    }

    private static boolean isTrue(String value) {
        return value != null && Boolean.parseBoolean(value.trim());
    }
}
