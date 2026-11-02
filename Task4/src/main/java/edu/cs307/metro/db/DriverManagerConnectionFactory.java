package edu.cs307.metro.db;

import edu.cs307.metro.config.DbConfig;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Supplier;

public final class DriverManagerConnectionFactory implements ConnectionFactory {
    private final Supplier<DbConfig> configuration;

    public DriverManagerConnectionFactory(Supplier<DbConfig> configuration) {
        this.configuration = Objects.requireNonNull(configuration);
    }

    @Override
    public Connection open() throws SQLException {
        DbConfig config = configuration.get();
        Properties properties = new Properties();
        properties.setProperty("user", config.user());
        properties.setProperty("password", config.password());
        properties.setProperty("currentSchema", "public");
        properties.setProperty("connectTimeout", "5");
        properties.setProperty("socketTimeout", "15");
        properties.setProperty("ApplicationName", "CS307-Task4");
        // pgJDBC 通过 JDBC SPI 自动注册；Maven runtime 依赖必须在运行时类路径中。
        return DriverManager.getConnection(config.url(), properties);
    }
}
