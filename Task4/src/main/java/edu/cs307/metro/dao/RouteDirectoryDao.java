package edu.cs307.metro.dao;

import edu.cs307.metro.model.LineView;
import edu.cs307.metro.model.StationKey;
import edu.cs307.metro.model.StationView;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/** 只读取数据库身份映射，路径及展示名称由固定缓存提供。 */
public final class RouteDirectoryDao {
    public record Directory(Map<Long, StationView> stations, Map<StationKey, StationView> stationsByKey,
                            Map<String, LineView> lines) {}

    public Directory load(Connection connection) throws SQLException {
        var stations = new HashMap<Long, StationView>();
        var keys = new HashMap<StationKey, StationView>();
        try (var statement = connection.prepareStatement("""
                SELECT s.station_id, s.chinese_name, s.english_name, d.name_en AS district_name
                FROM public.station s JOIN public.district d ON d.district_id = s.district_id
                """); var rows = statement.executeQuery()) {
            while (rows.next()) {
                var key = new StationKey(rows.getString("chinese_name"), rows.getString("district_name"));
                var station = new StationView(rows.getLong("station_id"), key, rows.getString("english_name"), "UNASSIGNED");
                stations.put(station.stationId(), station);
                keys.put(key, station);
            }
        }
        var lines = new HashMap<String, LineView>();
        try (var statement = connection.prepareStatement(
                "SELECT line_id, chinese_name, english_name FROM public.metro_line");
             var rows = statement.executeQuery()) {
            while (rows.next()) {
                var line = new LineView(rows.getLong("line_id"), rows.getString("chinese_name"), rows.getString("english_name"));
                lines.put(line.chineseName(), line);
            }
        }
        return new Directory(Map.copyOf(stations), Map.copyOf(keys), Map.copyOf(lines));
    }
}
