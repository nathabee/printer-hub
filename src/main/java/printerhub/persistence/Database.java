package printerhub.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class Database {

    private Database() {
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DatabaseConfig.jdbcUrl());
    }
}