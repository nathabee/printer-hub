package printerhub.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Provides JDBC connections to the SQLite database.
 */
public final class Database {

    private Database() {
        // utility class
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DatabaseConfig.jdbcUrl());
    }
}