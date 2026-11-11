package edu.cs307.metro.routing;

import edu.cs307.metro.model.StationKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 与 Task3 ImportSqlGenerator 使用同一原始字段、trim 规则和 Num 站序。 */
public final class CsvTopologyLoader {
    public record Source(RouteNetwork network, Map<StationKey, String> stationNames,
                         Map<String, String> lineNames) {}
    private static final String HEADER = "Line Chineses,Line En,Station Name,Station Name EN,"
            + "Num,District,Status,Opening,Start Time,End Time,latitude,longitude";

    private CsvTopologyLoader() {}

    public static RouteNetwork load(Path source) throws IOException {
        return read(source).network();
    }

    public static Source read(Path source) throws IOException {
        List<String> rows = Files.readAllLines(source, StandardCharsets.UTF_8);
        if (rows.isEmpty() || !rows.get(0).replaceFirst("^\uFEFF", "").equals(HEADER)) {
            throw new IllegalArgumentException("CSV 列名不符合官方站点文件格式");
        }
        var stops = new ArrayList<RouteNetwork.Stop>();
        var englishToChinese = new HashMap<String, String>();
        var chineseToEnglish = new HashMap<String, String>();
        var stationNames = new HashMap<StationKey, String>();
        for (int i = 1; i < rows.size(); i++) {
            String row = rows.get(i);
            String where = "CSV 第 " + (i + 1) + " 行";
            // 和现有导入器一致：固定官方文件不含引号字段，变更格式时明确拒绝。
            if (row.contains("\"")) {
                throw new IllegalArgumentException(where + " 包含引号字段，超出当前导入格式");
            }
            String[] fields = row.split(",", -1);
            if (fields.length != 12) {
                throw new IllegalArgumentException(where + " 列数不正确");
            }
            for (int column = 0; column < fields.length; column++) fields[column] = fields[column].trim();
            for (int column = 0; column < 8; column++) {
                if (fields[column].isEmpty()) {
                    throw new IllegalArgumentException(where + " 必填字段为空");
                }
            }
            checkLineName(englishToChinese, fields[1], fields[0], where);
            checkLineName(chineseToEnglish, fields[0], fields[1], where);
            if (!fields[6].equals("Operation") && !fields[6].equals("Construction")) {
                throw new IllegalArgumentException(where + " 运营状态无效");
            }
            int position;
            try {
                position = Integer.parseInt(fields[4]);
            } catch (NumberFormatException failure) {
                throw new IllegalArgumentException(where + " Num 必须是整数", failure);
            }
            StationKey station = new StationKey(fields[2], fields[5]);
            stationNames.putIfAbsent(station, fields[3]);
            stops.add(new RouteNetwork.Stop(station, fields[0],
                    position, fields[6].equals("Operation")));
        }
        return new Source(new RouteNetwork(stops), Map.copyOf(stationNames), Map.copyOf(chineseToEnglish));
    }

    private static void checkLineName(Map<String, String> names, String key, String name, String where) {
        String previous = names.putIfAbsent(key, name);
        if (previous != null && !previous.equals(name)) {
            throw new IllegalArgumentException(where + " 线路中英文名称不一致");
        }
    }
}
