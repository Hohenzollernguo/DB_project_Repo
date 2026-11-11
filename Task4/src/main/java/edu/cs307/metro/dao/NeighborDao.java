package edu.cs307.metro.dao;

import edu.cs307.metro.model.NeighborPair;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** 调用共享 Q7 函数，按返回顺序保留前、后两侧及 SQL NULL。 */
public final class NeighborDao {
    public NeighborPair get(Connection connection, String stationEnglishName,
                            String lineEnglishName, int n) throws SQLException {
        String sql = """
                SELECT name
                FROM public.get_station_n_neighbor(?, ?, ?)
                WITH ORDINALITY AS t(name, ord)
                ORDER BY ord
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, stationEnglishName);
            statement.setString(2, lineEnglishName);
            statement.setInt(3, n);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException("Neighbor function returned fewer than two rows");
                }
                String lowerPositionName = rows.getString("name");
                if (!rows.next()) {
                    throw new SQLException("Neighbor function returned fewer than two rows");
                }
                String higherPositionName = rows.getString("name");
                if (rows.next()) {
                    throw new SQLException("Neighbor function returned more than two rows");
                }
                return new NeighborPair(lowerPositionName, higherPositionName);
            }
        }
    }
}
