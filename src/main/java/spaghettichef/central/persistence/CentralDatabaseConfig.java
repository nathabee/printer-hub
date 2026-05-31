package spaghettichef.central.persistence;

import spaghettichef.config.RuntimeDefaults;

public final class CentralDatabaseConfig {
    public static final String CENTRAL_DATABASE_FILE_PROPERTY = "spaghettichef.central.databaseFile";
    public static final String DEFAULT_CENTRAL_DATABASE_FILE = "spaghettichef-central.db";

    private CentralDatabaseConfig() {
    }

    public static String databaseFile() {
        String configured = System.getProperty(CENTRAL_DATABASE_FILE_PROPERTY);
        if (configured == null || configured.isBlank()) {
            return DEFAULT_CENTRAL_DATABASE_FILE;
        }
        return configured.trim();
    }

    public static String jdbcUrl() {
        return RuntimeDefaults.SQLITE_JDBC_PREFIX + databaseFile();
    }
}
