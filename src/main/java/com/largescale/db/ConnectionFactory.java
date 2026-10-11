package com.largescale.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public final class ConnectionFactory {
    private final String url;
    public ConnectionFactory(String url) { this.url = url; }
    public Connection open() throws SQLException {
        String user = System.getenv("APP_DB_USER");
        String password = System.getenv("APP_DB_PASSWORD");
        if (user == null || user.isBlank() || password == null)
            throw new IllegalArgumentException("Set APP_DB_USER and APP_DB_PASSWORD");
        var properties = new Properties();
        properties.setProperty("user", user);
        properties.setProperty("password", password);
        return DriverManager.getConnection(url, properties);
    }
}
