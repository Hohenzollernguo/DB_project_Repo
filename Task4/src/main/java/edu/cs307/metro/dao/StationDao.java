package edu.cs307.metro.dao;

import edu.cs307.metro.model.StationDraft;
import edu.cs307.metro.model.StationKey;
import edu.cs307.metro.model.StationView;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

public final class StationDao {

     static final String STATION_SELECT_SQL = """
            select s.station_id, s.chinese_name, s.english_name, d.name_en as district_name,
                case
                    when exists (
                        select 1 from public.line_station as ls
                        where ls.station_id = s.station_id and ls.status = 'Operation'
                    ) then 'Operation'
                    when exists (
                        select 1 from public.line_station as ls
                        where ls.station_id = s.station_id
                    ) then 'Construction'
                    else 'UNASSIGNED'
                end as state
            from public.station as s
            join public.district as d on s.district_id = d.district_id
            """;

    public OptionalLong findDistrictId(Connection connection, String districtName)
            throws SQLException {
        String sql = "select district_id from public.district where name_en = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, districtName);

            try (ResultSet rows = statement.executeQuery()) {
                if (rows.next()) {
                    return OptionalLong.of(rows.getLong("district_id"));
                }
                return OptionalLong.empty();
            }
        }
    }


    public long insert(Connection connection, StationDraft draft, long districtId)
            throws SQLException {
        String sql = """
            insert into public.station (chinese_name, english_name, district_id)
            values (?, ?, ?)
            returning station_id
            """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, draft.chineseName());
            statement.setString(2, draft.englishName());
            statement.setLong(3, districtId);

            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException("插入车站后未返回车站 ID。");
                }

                long stationId = rows.getLong("station_id");
                return stationId;
            }
        }
    }

    /**
     * 根据车站 ID 修改基本资料，返回受影响的行数。
     */
    public int update(Connection connection, long stationId, StationDraft draft, long districtId)
            throws SQLException {
        String sql = """
            update public.station
            set chinese_name = ?,
                english_name = ?,
                district_id = ?
            where station_id = ?
            """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, draft.chineseName());
            statement.setString(2, draft.englishName());
            statement.setLong(3, districtId);
            statement.setLong(4, stationId);

            int updatedRows = statement.executeUpdate();
            return updatedRows;
        }
    }

    public List<StationView> search(Connection connection, String keyword)
            throws SQLException {
        String sql = STATION_SELECT_SQL + """
            where strpos(s.chinese_name, ?) > 0
               or strpos(lower(s.english_name), lower(?)) > 0
            order by s.english_name, d.name_en, s.chinese_name
            """;

        List<StationView> stations = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, keyword);
            statement.setString(2, keyword);

            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    StationView station = readStation(rows);
                    stations.add(station);
                }
            }
        }

        return stations;
    }

    public Optional<StationView> findById(Connection connection, long stationId)
            throws SQLException {
        String sql = STATION_SELECT_SQL + """
            where s.station_id = ?
            """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, stationId);

            try (ResultSet rows = statement.executeQuery()) {
                if (rows.next()) {
                    StationView station = readStation(rows);
                    return Optional.of(station);
                }

                return Optional.empty();
            }
        }
    }

    static StationView readStation(ResultSet rows) throws SQLException {
        long stationId = rows.getLong("station_id");
        String chineseName = rows.getString("chinese_name");
        String englishName = rows.getString("english_name");
        String districtName = rows.getString("district_name");
        String state = rows.getString("state");

        StationKey stationKey = new StationKey(chineseName, districtName);
        return new StationView(stationId, stationKey, englishName, state);
    }

    /**
     * 检查指定车站是否仍关联至少一条线路。
     */
    public boolean hasLineAssociation(Connection connection, long stationId)
            throws SQLException {
        String sql = """
            select 1
            from public.line_station
            where station_id = ?
            limit 1
            """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, stationId);

            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }
    /**
     * 删除指定车站下所有出口与地标的关联记录。
     */
    public void deleteExitLandmarkLinks(Connection connection, long stationId)
            throws SQLException {
        String sql = """
            delete from public.exit_landmark as el
            using public.station_exit as se
            where el.exit_id = se.exit_id
              and se.station_id = ?
            """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, stationId);
            statement.executeUpdate();
        }
    }

    /**
     * 删除指定车站的所有出口。
     */
    public void deleteExits(Connection connection, long stationId)
            throws SQLException {
        String sql = "delete from public.station_exit where station_id = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, stationId);
            statement.executeUpdate();
        }
    }

    /**
     * 删除指定车站，返回实际删除的行数。
     */
    public int deleteStation(Connection connection, long stationId)
            throws SQLException {
        String sql = "delete from public.station where station_id = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, stationId);
            int deletedRows = statement.executeUpdate();
            return deletedRows;
        }
    }


}
