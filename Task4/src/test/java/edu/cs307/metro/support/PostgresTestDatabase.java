package edu.cs307.metro.support;

import edu.cs307.metro.config.DbConfig;
import edu.cs307.metro.db.ConnectionFactory;
import edu.cs307.metro.db.DriverManagerConnectionFactory;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** 每个实库测试类使用独立数据库，加载共享 DDL，并在结束时删除自身创建的库。 */
public final class PostgresTestDatabase implements AutoCloseable {
    private final String name = "metro_line_test_" + UUID.randomUUID().toString().replace("-", "");
    private final ConnectionFactory administration;
    private final ConnectionFactory connections;
    private boolean created;

    private PostgresTestDatabase(String adminUrl, String user, String password) {
        URI uri = URI.create(adminUrl.substring("jdbc:".length()));
        String url = "jdbc:postgresql://" + uri.getRawAuthority() + "/" + name
                + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        administration = factory(adminUrl, user, password);
        connections = factory(url, user, password);
    }

    public static PostgresTestDatabase create() throws Exception {
        String url = Objects.requireNonNull(System.getenv("METRO_TEST_DB_URL"), "METRO_TEST_DB_URL");
        String user = Objects.requireNonNull(System.getenv("METRO_TEST_DB_USER"), "METRO_TEST_DB_USER");
        String password = System.getenv().getOrDefault("METRO_TEST_DB_PASSWORD", "");
        PostgresTestDatabase database = new PostgresTestDatabase(url, user, password);
        try {
            try (Connection connection = database.administration.open(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE DATABASE " + database.name);
                database.created = true;
            }
            Path ddl = Path.of(System.getProperty("basedir", ".")).resolve("../Task3/tools/create_table.sql");
            try (Connection connection = database.connections.open(); Statement statement = connection.createStatement()) {
                statement.execute(Files.readString(ddl));
                try (var result = statement.executeQuery("SHOW server_version")) {
                    if (!result.next()) throw new SQLException("Database version query returned no row");
                    System.out.println("Line integration database: PostgreSQL " + result.getString(1));
                }
            }
            return database;
        } catch (Exception | Error failure) {
            try {
                database.close();
            } catch (SQLException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    private static ConnectionFactory factory(String url, String user, String password) {
        return new DriverManagerConnectionFactory(() -> DbConfig.from(
                Map.of("DB_URL", url, "DB_USER", user, "DB_PASSWORD", password)));
    }

    public ConnectionFactory connections() {
        return connections;
    }

    public void reset() throws SQLException {
        try (Connection connection = connections.open(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    TRUNCATE public.exit_landmark, public.station_exit, public.landmark,
                             public.line_station, public.station, public.metro_line, public.district
                    RESTART IDENTITY
                    """);
        }
    }

    @Override
    public void close() throws SQLException {
        if (created) {
            try (Connection connection = administration.open(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("DROP DATABASE " + name);
                created = false;
            }
        }
    }
}
