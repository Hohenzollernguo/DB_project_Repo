package edu.cs307.metro.dao;

import edu.cs307.metro.db.DatabaseStatus;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** 一个真实的只读 DAO，示范资源归属；调用者负责 Connection 生命周期。 */
public final class DatabaseInfoDao {
    public DatabaseStatus read(Connection connection) throws SQLException {
        String sql = "SELECT current_database(), current_schema(), current_setting('server_version')";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            if (!rows.next()) {
                throw new SQLException("Database identity query returned no row");
            }
            return new DatabaseStatus(rows.getString(1), rows.getString(2), rows.getString(3));
        }
    }
}
