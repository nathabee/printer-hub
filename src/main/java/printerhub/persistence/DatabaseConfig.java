package printerhub.persistence;

public final class DatabaseConfig {

    public static final String DEFAULT_DATABASE_FILE = "printerhub.db";

    private static final String DATABASE_FILE_PROPERTY = "printerhub.databaseFile";

    private DatabaseConfig() {
    }

    public static String databaseFile() {
        String configuredFile = System.getProperty(DATABASE_FILE_PROPERTY);

        if (configuredFile == null || configuredFile.isBlank()) {
            return DEFAULT_DATABASE_FILE;
        }

        return configuredFile.trim();
    }

    public static String jdbcUrl() {
        return "jdbc:sqlite:" + databaseFile();
    }
}