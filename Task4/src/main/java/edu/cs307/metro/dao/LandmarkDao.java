package edu.cs307.metro.dao;

import edu.cs307.metro.model.LandmarkHit;
import edu.cs307.metro.model.StationView;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class LandmarkDao {

    private static final String SELECT_LANDMARK_SQL =
            "with station_details as (\n" + StationDao.STATION_SELECT_SQL + """
        )
        select m.landmark_id, m.name as landmark_name,
               e.exit_id, e.exit_code,
               sd.station_id, sd.chinese_name, sd.english_name, sd.district_name, sd.state
        from public.landmark as m
        join public.exit_landmark as em on em.landmark_id = m.landmark_id
        join public.station_exit as e on e.exit_id = em.exit_id
        join station_details as sd on sd.station_id = e.station_id
        """;

    public List<LandmarkHit> findByName(Connection connection, String landmarkName)
            throws SQLException {
        String sql = SELECT_LANDMARK_SQL + """
                where m.name = ?
                order by m.name, e.station_id, e.exit_code
                """;
        return find(connection, sql, landmarkName);
    }

    public List<LandmarkHit> searchPrefix(Connection connection, String prefix)
            throws SQLException {
        String sql = SELECT_LANDMARK_SQL + """
                where starts_with(m.name, ?)
                order by m.landmark_id, e.station_id, e.exit_code
                """;
        return find(connection, sql, prefix);
    }

    private List<LandmarkHit> find(Connection connection, String sql, String name)
            throws SQLException {
        List<LandmarkHit> hits = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);

            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    long landmarkId = rows.getLong("landmark_id");
                    String matchedName = rows.getString("landmark_name");
                    long exitId = rows.getLong("exit_id");
                    String exitCode = rows.getString("exit_code");
                    StationView station = StationDao.readStation(rows);

                    LandmarkHit hit = new LandmarkHit(landmarkId, matchedName, station, exitId, exitCode);
                    hits.add(hit);
                }
            }
        }

        return hits;
    }
}
