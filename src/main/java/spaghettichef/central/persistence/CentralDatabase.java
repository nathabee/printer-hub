package spaghettichef.central.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class CentralDatabase {
    private CentralDatabase() {
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(CentralDatabaseConfig.jdbcUrl());
    }
}
