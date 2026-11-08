import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalTime;
import java.time.format.*;
import java.util.*;
import java.util.regex.Pattern;

/** 固定课程CSV -> 独立SQL；Java 17，无第三方依赖，不连接数据库。 */
public class ImportSqlGenerator {
    record StationKey(String chineseName, String districtName) {}
    record ExitKey(int stationId, String code) {}
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm")
            .withResolverStyle(ResolverStyle.STRICT);

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("程序实参：仓库根目录路径");
        Path repo = Path.of(args[0]).toAbsolutePath().normalize();
        List<String[]> stations = readCsv(repo.resolve("官方描述及数据/shenzhen_stations.csv"),
                "Line Chineses,Line En,Station Name,Station Name EN,Num,District,Status,Opening,Start Time,End Time,latitude,longitude");
        List<String[]> exits = readCsv(repo.resolve("官方描述及数据/station_exits.csv"),
                "station_name,station_name_en,exit,landmark");

        Map<String, Integer> districtIds = new LinkedHashMap<>();
        Map<String, Integer> lineIds = new LinkedHashMap<>();
        Map<String, String> lineNames = new LinkedHashMap<>();
        Map<StationKey, Integer> stationIds = new LinkedHashMap<>();
        Map<StationKey, String> stationNames = new LinkedHashMap<>();
        Map<String, List<StationKey>> stationsByName = new HashMap<>();
        Map<String, String> chineseLines = new HashMap<>();
        Map<String, Set<Integer>> positions = new HashMap<>();
        Set<String> lineStationKeys = new HashSet<>();
        List<String> lineStationRows = new ArrayList<>();

        // 1. 整理行政区、线路、车站及站线关系。
        for (int i = 0; i < stations.size(); i++) {
            String[] r = stations.get(i);
            String where = "shenzhen_stations.csv 第" + (i + 2) + "行";
            for (int c = 0; c < 8; c++) check(!r[c].isEmpty(), where + " 第" + (c + 1) + "列为空");
            id(districtIds, r[5]);
            int lineId = id(lineIds, r[1]);
            consistent(lineNames, r[1], r[0], where + " 线路中文名");
            consistent(chineseLines, r[0], r[1], where + " 线路英文名");
            StationKey key = new StationKey(r[2], r[5]);
            if (!stationIds.containsKey(key)) {
                id(stationIds, key);
                stationsByName.computeIfAbsent(r[2], k -> new ArrayList<>()).add(key);
            }
            consistent(stationNames, key, r[3], where + " 车站英文名");
            int stationId = stationIds.get(key);
            int position = positive(r[4], Integer.MAX_VALUE, where + " Num");
            int year = positive(r[7], Short.MAX_VALUE, where + " Opening");
            check(Set.of("Operation", "Construction").contains(r[6]), where + " Status=" + r[6]);
            check(lineStationKeys.add(lineId + ":" + stationId), where + " 站线关系重复");
            check(positions.computeIfAbsent(r[1], k -> new HashSet<>()).add(position), where + " 站序重复");
            lineStationRows.add(tuple(lineId, stationId, position, sqlText(r[6]), year,
                    time(r[8], where), time(r[9], where), number(r[10], where), number(r[11], where)));
        }
        positions.forEach((line, values) -> {
            for (int p = 1; p <= values.size(); p++) check(values.contains(p), line + " 站序不连续");
        });

        // 2. 通过已有车站关联出口；中文逗号分隔地标，斜杠保留。
        Map<ExitKey, Integer> exitIds = new LinkedHashMap<>();
        Map<String, Integer> landmarkIds = new LinkedHashMap<>();
        Set<String> exitLandmarkRows = new LinkedHashSet<>();
        for (int i = 0; i < exits.size(); i++) {
            String[] r = exits.get(i);
            String where = "station_exits.csv 第" + (i + 2) + "行";
            for (int c = 0; c < 3; c++) check(!r[c].isEmpty(), where + " 第" + (c + 1) + "列为空");
            List<StationKey> candidates = new ArrayList<>();
            for (StationKey key : stationsByName.getOrDefault(r[0], List.of())) {
                if (stationNames.get(key).equals(r[1])) candidates.add(key);
            }
            // 团队推断：出口文件中的梨园明确归Longgang；不是源文件提供的区名。
            if (r[0].equals("梨园") && r[1].equals("Liyuan")) {
                candidates.removeIf(k -> !k.districtName().equals("Longgang"));
            }
            check(candidates.size() == 1, where + " 无法唯一匹配车站：" + r[0] + "/" + r[1]);
            ExitKey key = new ExitKey(stationIds.get(candidates.get(0)), r[2]);
            check(!exitIds.containsKey(key), where + " 出口重复");
            int exitId = id(exitIds, key);
            for (String name : r[3].split("，", -1)) {
                name = name.trim();
                if (!name.isEmpty()) exitLandmarkRows.add(tuple(exitId, id(landmarkIds, name)));
            }
        }
        check(stations.size() == 567 && exits.size() == 1276, "原始记录数不符合当前数据基线");
        check(districtIds.size() == 10 && stationIds.size() == 439 && lineIds.size() == 29,
                "行政区/车站/线路数不符合基线");
        check(exitIds.size() == 1276 && landmarkIds.size() == 2872 && exitLandmarkRows.size() == 3203,
                "出口/地标/关联数不符合基线");

        // 3. 清理和建表：复用队友的DDL，不维护第二套表结构。
        StringBuilder sql = new StringBuilder("""
                -- Generated by Task3/tools/ImportSqlGenerator.java. UTF-8.
                -- Rebuilds only the seven project tables and Q7 function.
                -- Liyuan exits -> Longgang: explicit team inference.
                -- Coordinates retain original CSV fields and values.
                BEGIN;
                SET LOCAL standard_conforming_strings = on;
                DROP FUNCTION IF EXISTS public.get_station_n_neighbor(text, text, integer);
                DROP TABLE IF EXISTS public.exit_landmark;
                DROP TABLE IF EXISTS public.station_exit;
                DROP TABLE IF EXISTS public.line_station;
                DROP TABLE IF EXISTS public.landmark;
                DROP TABLE IF EXISTS public.station;
                DROP TABLE IF EXISTS public.metro_line;
                DROP TABLE IF EXISTS public.district;

                -- Shared teammate DDL:
                """);
        sql.append(Files.readString(repo.resolve("create_table.sql"), StandardCharsets.UTF_8)).append('\n');
        List<String> rows = new ArrayList<>();
        districtIds.forEach((name, value) -> rows.add(tuple(value, sqlText(name))));
        batch(sql, "district", "district_id, name_en", rows);
        rows.clear();
        stationIds.forEach((key, value) -> rows.add(tuple(value, sqlText(key.chineseName()),
                sqlText(stationNames.get(key)), districtIds.get(key.districtName()))));
        batch(sql, "station", "station_id, chinese_name, english_name, district_id", rows);
        rows.clear();
        lineIds.forEach((name, value) -> rows.add(tuple(value, sqlText(lineNames.get(name)), sqlText(name))));
        batch(sql, "metro_line", "line_id, chinese_name, english_name", rows);
        batch(sql, "line_station", "line_id, station_id, position, status, opening_year, start_time, end_time, source_latitude, source_longitude", lineStationRows);
        rows.clear();
        exitIds.forEach((key, value) -> rows.add(tuple(value, key.stationId(), sqlText(key.code()))));
        batch(sql, "station_exit", "exit_id, station_id, exit_code", rows);
        rows.clear();
        landmarkIds.forEach((name, value) -> rows.add(tuple(value, sqlText(name))));
        batch(sql, "landmark", "landmark_id, name", rows);
        batch(sql, "exit_landmark", "exit_id, landmark_id", new ArrayList<>(exitLandmarkRows));

        // 显式ID导入后推进序列，保证Task4之后新增记录不撞主键。
        String[][] sequences = {{"district", "district_id"}, {"station", "station_id"},
                {"metro_line", "line_id"}, {"station_exit", "exit_id"}, {"landmark", "landmark_id"}};
        for (String[] pair : sequences) {
            sql.append("SELECT setval(pg_get_serial_sequence('public.").append(pair[0])
                    .append("', '").append(pair[1]).append("'), (SELECT MAX(")
                    .append(pair[1]).append(") FROM public.").append(pair[0]).append("), true);\n");
        }
        sql.append('\n').append(neighborFunction(repo)).append('\n');
        sql.append("SET CONSTRAINTS ALL IMMEDIATE;\nCOMMIT;\n");
        Path output = repo.resolve("Task3/import.sql");
        Files.createDirectories(output.getParent());
        Files.writeString(output, sql, StandardCharsets.UTF_8);
        System.out.println("已生成：" + output);
        System.out.printf("district=%d, station=%d, metro_line=%d, line_station=%d%n",
                districtIds.size(), stationIds.size(), lineIds.size(), lineStationRows.size());
        System.out.printf("station_exit=%d, landmark=%d, exit_landmark=%d%n",
                exitIds.size(), landmarkIds.size(), exitLandmarkRows.size());
        System.out.println("生成器未连接数据库。请在DataGrip的prj1中执行完整import.sql。");
    }

    private static <K> int id(Map<K, Integer> ids, K key) {
        return ids.computeIfAbsent(key, ignored -> ids.size() + 1);
    }

    private static <K> void consistent(Map<K, String> names, K key, String value, String where) {
        String old = names.putIfAbsent(key, value);
        check(old == null || old.equals(value), where + " 同键字段不一致：" + value);
    }

    private static String sqlText(String text) {
        check(text.indexOf('\0') < 0, "文本不能包含NUL");
        return "'" + text.replace("'", "''") + "'";
    }

    private static String tuple(Object... values) {
        return "(" + String.join(", ", Arrays.stream(values).map(Object::toString).toList()) + ")";
    }

    private static void batch(StringBuilder sql, String table, String columns, List<String> rows) {
        // 每批最多500行，减少SQL语句数；全部批次属于同一个事务。
        for (int start = 0; start < rows.size(); start += 500) {
            sql.append("\nINSERT INTO public.").append(table).append(" (").append(columns)
                    .append(") VALUES\n").append(String.join(",\n", rows.subList(start, Math.min(start + 500, rows.size()))))
                    .append(";\n");
        }
    }

    private static int positive(String text, int maximum, String where) {
        try {
            int value = Integer.parseInt(text);
            check(value > 0 && value <= maximum, where + " 越界：" + text);
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(where + " 不是整数：" + text, ex);
        }
    }

    private static String time(String text, String where) {
        if (text.isEmpty()) return "NULL";
        try { LocalTime.parse(text, TIME); }
        catch (RuntimeException ex) { throw new IllegalArgumentException(where + " 非法时间：" + text, ex); }
        return sqlText(text);
    }

    private static String number(String text, String where) {
        if (text.isEmpty()) return "NULL";
        try {
            BigDecimal value = new BigDecimal(text);
            check(value.stripTrailingZeros().scale() <= 6 && value.abs().compareTo(new BigDecimal("1000")) < 0,
                    where + " 数值超出NUMERIC(9,6)：" + text);
            return value.toPlainString();
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(where + " 非法数值：" + text, ex);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private static List<String[]> readCsv(Path file, String header) throws Exception {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        check(!lines.isEmpty(), file + " 文件为空");
        check(lines.get(0).replaceFirst("^\uFEFF", "").equals(header), file + " 列名不符合约定");
        int columns = header.split(",", -1).length;
        List<String[]> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String where = file + " 第" + (i + 1) + "行";
            // 固定源文件没有引号或字段内英文逗号；格式改变时明确失败。
            check(!lines.get(i).contains("\""), where + " 出现引号，请使用完整CSV解析器");
            String[] row = lines.get(i).split(",", -1);
            check(row.length == columns, where + " 列数不正确");
            for (int j = 0; j < row.length; j++) row[j] = row[j].trim();
            rows.add(row);
        }
        return rows;
    }

    private static String neighborFunction(Path repo) throws Exception {
        // 复用B的函数；不把Q2/Q4、DataGrip参数或演示SELECT混入导入脚本。
        String source = Files.readString(repo.resolve("Task3_247.sql"), StandardCharsets.UTF_8);
        var matcher = Pattern.compile("(?is)CREATE\\s+OR\\s+REPLACE\\s+function\\s+get_station_n_neighbor\\b.*?\\$\\$;")
                .matcher(source);
        check(matcher.find(), "Task3_247.sql中没有找到约定的Q7函数");
        String function = matcher.group();
        check(function.contains("raise exception 'n无效';"), "Q7版本已改变，请核对错误码后更新生成器");
        function = function.replace("function get_station_n_neighbor", "function public.get_station_n_neighbor")
                .replace("language plpgsql", "language plpgsql\n    SET search_path = public, pg_temp")
                .replace("raise exception 'n无效';", "raise exception 'n无效' USING ERRCODE = '22023';")
                .replace("t2.position = t3.position + n", "t2.position::bigint = t3.position::bigint + n")
                .replace("t2.position = t4.position - n", "t2.position::bigint = t4.position::bigint - n")
                .replaceFirst("(?s)('线路 % 不存在，或站点 % 不在这条线路上',\\s*line_name, station_name);", "$1 USING ERRCODE = 'P0002';")
                .replaceFirst("(?s)('线路 % 内有多个英文名为 % 的车站，无法唯一定位',\\s*line_name, station_name);", "$1 USING ERRCODE = 'P0003';");
        check(function.contains("ERRCODE = 'P0002'") && function.contains("ERRCODE = 'P0003'"), "Q7错误码适配失败");
        return "-- Teammate Q7 adapted: agreed SQLSTATEs, fixed search_path, bigint arithmetic.\n" + function;
    }
}
