package com.sisjuan.utilities;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

public class ConnectionPool {
    private static final HikariConfig config = new HikariConfig();
    private static final HikariDataSource ds;

    static {
        config.setJdbcUrl(DBConnection.URL);
        config.setUsername(DBConnection.USER);
        config.setPassword(DBConnection.PASSWORD);
        config.setMaximumPoolSize(25);
        config.setMinimumIdle(2);
        config.setIdleTimeout(30000);
        config.setMaxLifetime(600000);
        config.setLeakDetectionThreshold(3000); // logs if a connection is held >3s
        config.setPoolName("PUPSISMainDashboardHikariCP");
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        ds = new HikariDataSource(config);
    }

    private ConnectionPool() {}

    public static Connection getConnection() throws SQLException {
        return ds.getConnection();
    }

    public static void closePool() {
        ds.close();
    }
}
