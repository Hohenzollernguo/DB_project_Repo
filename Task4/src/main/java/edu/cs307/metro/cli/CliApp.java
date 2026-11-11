package edu.cs307.metro.cli;

import edu.cs307.metro.bootstrap.AppServices;
import edu.cs307.metro.common.AppException;
import edu.cs307.metro.common.ErrorCode;
import edu.cs307.metro.db.DatabaseStatus;
import edu.cs307.metro.model.*;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

/** 双方在对应菜单接入自己的服务；本类不包含 SQL 或事务。 */
public final class CliApp {
    private final AppServices services;

    public CliApp(AppServices services) {
        this.services = services;
    }

    public void run(Scanner input, PrintStream output) {
        output.println("CS307 地铁管理系统 · Task4 开发骨架");
        while (true) {
            output.println("""
                    1. 车站管理
                    2. 修改线路中英文名
                    3. 站线关系（插入/移除）
                    4. 前后第 n 站
                    5. 两站路径
                    6. 两地标路线
                    7. 查询票价
                    8. 检查数据库连接
                    9. 添加线路
                    10. 删除线路
                    11. 查询线路
                    0. 退出""");
            output.print("请选择操作：");
            if (!input.hasNextLine()) {
                return;
            }
            String choice = input.nextLine().trim();
            try {
                switch (choice) {
                    case "0" -> { return; }
                    case "1" -> {
                        if (!runStationMenu(input, output)) return;
                    }
                    case "4" -> runNeighborQuery(input, output);
                    case "6" -> runLandmarkRouteQuery(input, output);
                    case "5" -> runRouteQuery(input, output);
                    case "2" -> {
                        LineView selected = CliLineSelection.select(services.lines(), input, output, "修改");
                        if (selected == null) continue;
                        output.print("请输入新中文名：");
                        if (!input.hasNextLine()) return;
                        String chineseName = input.nextLine();
                        output.print("请输入新英文名：");
                        if (!input.hasNextLine()) return;
                        String englishName = input.nextLine();
                        LineDraft draft = new LineDraft(chineseName, englishName);
                        LineView result = services.lines().update(selected.lineId(), draft);
                        output.printf("修改成功：ID=%d，中文名=%s，英文名=%s%n",
                                result.lineId(), result.chineseName(), result.englishName());
                    }
                    case "3" -> {
                        if (!runLineStationMenu(input, output)) return;
                    }
                    case "7" -> runFareQuery(input, output);
                    case "8" -> {
                        DatabaseStatus status = services.database().check();
                        output.printf("已连接：数据库=%s，schema=%s，PostgreSQL=%s%n",
                                status.database(), status.schema(), status.serverVersion());
                    }
                    case "9" -> {
                        output.print("请输入新线路中文名：");
                        if (!input.hasNextLine()) return;
                        String chineseName = input.nextLine();
                        output.print("请输入新线路英文名：");
                        if (!input.hasNextLine()) return;
                        String englishName = input.nextLine();
                        LineView result = services.lines().create(new LineDraft(chineseName, englishName));
                        output.printf("添加成功：ID=%d，中文名=%s，英文名=%s%n",
                                result.lineId(), result.chineseName(), result.englishName());
                    }
                    case "10" -> {
                        LineView selected = CliLineSelection.select(services.lines(), input, output, "删除");
                        if (selected == null) continue;
                        output.printf("确认删除线路 ID=%d（%s / %s）？输入 YES 确认，其他输入取消：",
                                selected.lineId(), selected.chineseName(), selected.englishName());
                        if (!input.hasNextLine()) return;
                        if (!"YES".equalsIgnoreCase(input.nextLine().trim())) {
                            output.println("已取消删除。");
                            continue;
                        }
                        services.lines().delete(selected.lineId());
                        output.printf("删除成功：ID=%d%n", selected.lineId());
                    }
                    case "11" -> {
                        output.print("请输入希望查询的线路编号：");
                        if (!input.hasNextLine()) return;
                        String keyword = input.nextLine();

                        List<LineView> results = services.lines().search(keyword);

                        if (results.isEmpty()) {
                            output.println("未找到对应线路。");
                        } else {
                            output.printf("共找到 %d 条线路记录。%n", results.size());

                            for (LineView line : results) {
                                output.printf("ID=%d，中文名=%s，英文名=%s%n",
                                        line.lineId(),
                                        line.chineseName(),
                                        line.englishName());
                            }
                        }
                    }
                    default -> output.println("请输入 0 到 11。");
                }
            } catch (AppException failure) {
                output.printf("[%s] %s%n", failure.code(), failure.getMessage());
            } catch (UnsupportedOperationException failure) {
                output.println("此功能尚未实现。");
            }
            output.println();
        }
    }

    private void runNeighborQuery(Scanner input, PrintStream output) {
        String station = readLine(input, output, "请输入车站英文名，例如 Mawan：");
        if (station == null) return;
        String line = readLine(input, output, "请输入线路英文名，例如 Line 5：");
        if (line == null) return;
        String count = readLine(input, output, "请输入 n（非负整数，0 表示本站）：");
        if (count == null) return;
        final int n;
        try {
            n = Integer.parseInt(count);
        } catch (NumberFormatException failure) {
            throw new AppException(ErrorCode.INVALID_INPUT, "n 必须是 0 到 2147483647 之间的整数。");
        }
        NeighborPair result = services.neighbors().get(station, line, n);
        output.printf("向前第 %d 站（站序减小）：%s%n", n,
                result.lowerPositionName() == null ? "无此站（超出线路范围）" : result.lowerPositionName());
        output.printf("向后第 %d 站（站序增大）：%s%n", n,
                result.higherPositionName() == null ? "无此站（超出线路范围）" : result.higherPositionName());
    }

    private void runLandmarkRouteQuery(Scanner input, PrintStream output) {
        output.print("请输入起点地标全名：");
        if (!input.hasNextLine()) {
            return;
        }
        String fromName = input.nextLine();

        output.print("请输入终点地标全名：");
        if (!input.hasNextLine()) {
            return;
        }
        String toName = input.nextLine();

        LandmarkRoute result = services.landmarks().findRoute(fromName, toName);

        LandmarkHit from = result.from();
        LandmarkHit to = result.to();
        RouteResult route = result.route();

        output.printf("起点：%s，%s站 %s口%n",
                from.landmarkName(), from.station().key().chineseName(), from.exitCode());

        for (RouteLeg leg : route.legs()) {
            output.printf("乘坐 %s：", leg.line().chineseName());
            for (StationView station : leg.stations()) {
                output.print(station.key().chineseName() + " ");
            }
            output.println();
        }

        output.printf("终点：%s，%s站 %s口%n",
                to.landmarkName(), to.station().key().chineseName(), to.exitCode());
        output.printf("共 %d 个站间段，换乘 %d 次。%n",
                route.stopCount(), route.transferCount());
    }

    private void runFareQuery(Scanner input, PrintStream output) {
        StationView from = selectStation(input, output, "作为票价起点");
        if (from == null) {
            return;
        }

        StationView to = selectStation(input, output, "作为票价终点");
        if (to == null) {
            return;
        }

        FareQuote quote = services.fares().quote(from.stationId(), to.stationId());

        output.printf("从 %s 到 %s%n",
                from.key().chineseName(),
                to.key().chineseName());
        output.printf("项目票价：%s 元%n", quote.amount());
        output.println(quote.message());
    }

    private void runRouteQuery(Scanner input, PrintStream output) {
        StationView from = selectStation(input, output, "作为起点");
        if (from == null) return;
        StationView to = selectStation(input, output, "作为终点");
        if (to == null) return;
        RouteResult result = services.routes().findRoute(from.stationId(), to.stationId());
        output.printf("路线：%s → %s%n", from.key().chineseName(), to.key().chineseName());
        for (int i = 0; i < result.legs().size(); i++) {
            RouteLeg leg = result.legs().get(i);
            if (i > 0) output.printf("在 %s 换乘。%n", leg.stations().get(0).key().chineseName());
            output.printf("乘坐 %s：%s%n", leg.line().chineseName(), leg.stations().stream()
                    .map(station -> station.key().chineseName()).collect(Collectors.joining(" → ")));
        }
        output.printf("共 %d 个站间段，换乘 %d 次。%n", result.stopCount(), result.transferCount());
    }

    /** 返回 true 表示回到主菜单，false 表示输入结束，需要退出程序。 */
    private boolean runStationMenu(Scanner input, PrintStream output) {
        output.println();
        output.println("""
                车站管理
                1. 新增车站
                2. 修改车站
                3. 删除车站
                4. 查询车站
                0. 返回主菜单
                """);
        output.print("请选择车站操作：");

        if (!input.hasNextLine()) {
            return false;
        }

        String stationChoice = input.nextLine().strip();

        switch (stationChoice) {
            case "1" -> {
                output.print("请输入车站中文名：");
                if (!input.hasNextLine()) return false;
                String chineseName = input.nextLine();

                output.print("请输入车站英文名：");
                if (!input.hasNextLine()) return false;
                String englishName = input.nextLine();

                output.print("请输入行政区英文名：");
                if (!input.hasNextLine()) return false;
                String districtName = input.nextLine();

                StationDraft draft = new StationDraft(chineseName, englishName, districtName);
                StationView createdStation = services.stations().create(draft);

                output.printf("新增成功：ID=%d，中文名=%s，行政区=%s，状态=%s%n",
                        createdStation.stationId(),
                        createdStation.key().chineseName(),
                        createdStation.key().districtName(),
                        createdStation.state());
            }
            case "2" -> {
                StationView selectedStation = selectStation(input, output, "修改");
                if (selectedStation == null) {
                    return true;
                }

                long stationId = selectedStation.stationId();
                output.printf("已选中：%s · %s%n",
                        selectedStation.key().chineseName(),
                        selectedStation.key().districtName());
                output.print("请输入修改后的车站中文名：");
                if (!input.hasNextLine()) {
                    return false;
                }
                String newChineseName = input.nextLine();

                output.print("请输入修改后的车站英文名：");
                if (!input.hasNextLine()) {
                    return false;
                }
                String newEnglishName = input.nextLine();

                output.print("请输入修改后的行政区英文名：");
                if (!input.hasNextLine()) {
                    return false;
                }
                String newDistrictName = input.nextLine();

                StationDraft draft = new StationDraft(newChineseName, newEnglishName, newDistrictName);
                StationView updatedStation = services.stations().update(stationId, draft);

                output.printf("修改成功：ID=%d | %s (%s) | %s | %s%n",
                        updatedStation.stationId(),
                        updatedStation.key().chineseName(),
                        updatedStation.englishName(),
                        updatedStation.key().districtName(),
                        updatedStation.state());
            }

            case "3" -> {
                StationView selectedStation = selectStation(input, output, "删除");
                if (selectedStation == null) {
                    return true;
                }

                long stationId = selectedStation.stationId();
                StationKey key = selectedStation.key();
                output.printf("确认删除车站 ID=%d（%s · %s）？输入 YES 确认，其他输入取消：",
                        stationId, key.chineseName(), key.districtName());
                if (!input.hasNextLine()) {
                    return false;
                }

                String confirmation = input.nextLine().strip();
                if (!"YES".equalsIgnoreCase(confirmation)) {
                    output.println("已取消删除。");
                    return true;
                }

                services.stations().delete(stationId);
                output.printf("删除成功：ID=%d%n", stationId);
            }
            case "4" -> {
                output.print("请输入车站中文或英文名 ：");
                if (!input.hasNextLine()) {
                    return false;
                }

                String keyword = input.nextLine();
                List<StationView> stations = services.stations().search(keyword);

                if (stations.isEmpty()) {
                    output.println("未找到匹配的车站。");
                } else {
                    for (StationView station : stations) {
                        StationKey key = station.key();
                        output.printf("ID=%d | %s (%s) | %s | %s%n",
                                station.stationId(),
                                key.chineseName(),
                                station.englishName(),
                                key.districtName(),
                                station.state());
                    }
                }
            }
            case "0" -> {
                return true;
            }
            default -> {
                output.println("请输入 0 到 4。");
                return true;
            }
        }
        return true;
    }

    private StationView selectStation(Scanner input, PrintStream output, String action) {
        output.print("请输入要" + action + "的车站名（中文或英文）：");
        if (!input.hasNextLine()) {
            return null;
        }

        String keyword = input.nextLine().strip();
        if (keyword.isEmpty()) {
            output.println("请输入车站名关键词。");
            return null;
        }

        List<StationView> candidates = services.stations().search(keyword);
        if (candidates.isEmpty()) {
            output.println("未找到匹配的车站。");
            return null;
        }

        output.println("找到以下车站：");
        for (StationView station : candidates) {
            StationKey key = station.key();
            output.printf("ID=%d | %s (%s) | %s%n",
                    station.stationId(),
                    key.chineseName(),
                    station.englishName(),
                    key.districtName());
        }

        output.print("请输入要" + action + "的车站 ID：");
        if (!input.hasNextLine()) {
            return null;
        }

        String stationIdText = input.nextLine().strip();
        long stationId;
        try {
            stationId = Long.parseLong(stationIdText);
        } catch (NumberFormatException exception) {
            output.println("请输入列表中的数字 ID。");
            return null;
        }

        for (StationView candidate : candidates) {
            if (candidate.stationId() == stationId) {
                return candidate;
            }
        }

        output.println("这个 ID 不在刚才的候选列表里。");
        return null;
    }

    /** 返回 true 表示回到主菜单，false 表示输入结束，需要退出程序。 */
    private boolean runLineStationMenu(Scanner input, PrintStream output) {
        while (true) {
            output.println("""
                    站线关系管理
                    1. 插入站点
                    2. 移除站点
                    0. 返回主菜单""");
            output.print("请选择站线操作：");
            if (!input.hasNextLine()) {
                return false;
            }
            String choice = input.nextLine().trim();
            switch (choice) {
                case "0" -> { return true; }
                case "1" -> {
                    if (!runInsertStops(input, output)) return false;
                }
                case "2" -> {
                    LineView selectedLine = CliLineSelection.select(
                            services.lines(), input, output, "移除站点");
                    if (selectedLine == null) continue;

                    long lineId = selectedLine.lineId();
                    List<LineStopView> stops = services.lines().getStops(lineId);
                    if (stops.isEmpty()) {
                        output.println("该线路暂无站点。");
                        continue;
                    }
                    for (LineStopView stop : stops) {
                        StationView station = stop.station();
                        output.printf("站序=%d，车站 ID=%d，站名=%s / %s，行政区=%s%n",
                                stop.position(), station.stationId(), station.key().chineseName(),
                                station.englishName(), station.key().districtName());
                    }

                    output.print("请输入要移除的车站 ID（留空取消）：");
                    if (!input.hasNextLine()) return false;
                    String stationChoice = input.nextLine().trim();
                    if (stationChoice.isEmpty()) {
                        output.println("已取消移除。");
                        continue;
                    }
                    final long stationId;
                    try {
                        stationId = Long.parseLong(stationChoice);
                    } catch (NumberFormatException failure) {
                        throw new AppException(ErrorCode.INVALID_INPUT, "请选择列表中的车站 ID。");
                    }
                    StationView selectedStation = stops.stream()
                            .map(LineStopView::station)
                            .filter(station -> station.stationId() == stationId)
                            .findFirst()
                            .orElseThrow(() -> new AppException(
                                    ErrorCode.INVALID_INPUT, "请选择列表中的车站 ID。"));

                    output.printf("确认从线路 ID=%d（%s）移除车站 ID=%d（%s）？输入 YES 确认，其他输入取消：",
                            lineId, selectedLine.chineseName(), stationId,
                            selectedStation.key().chineseName());
                    if (!input.hasNextLine()) return false;
                    if (!"YES".equalsIgnoreCase(input.nextLine().trim())) {
                        output.println("已取消移除。");
                        continue;
                    }
                    services.lines().removeStop(lineId, stationId);
                    output.printf("移除成功：线路 ID=%d，车站 ID=%d%n", lineId, stationId);
                }
                case "" -> output.println("输入不能为空，请输入 0 到 2。");
                default -> output.println("无效选项，请输入 0 到 2。");
            }
            output.println();
        }
    }

    private boolean runInsertStops(Scanner input, PrintStream output) {
        LineView selectedLine = CliLineSelection.select(
                services.lines(), input, output, "插入站点");
        if (selectedLine == null) return true;

        String positionText = readLine(input, output, "请输入插入位置（从 1 开始，留空取消）：");
        if (positionText == null) return false;
        if (positionText.isEmpty()) {
            output.println("已取消插入。");
            return true;
        }
        int position = parsePositiveInt(positionText, "插入位置");
        String countText = readLine(input, output, "请输入本次插入的车站数量（留空取消）：");
        if (countText == null) return false;
        if (countText.isEmpty()) {
            output.println("已取消插入。");
            return true;
        }
        int count = parsePositiveInt(countText, "车站数量");
        List<StopDraft> stops = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            output.printf("选择第 %d 个待插入车站。%n", index + 1);
            StationView station = selectStationForInsertion(input, output);
            if (station == null) return true;

            String status = readLine(input, output, "请输入运营状态（Operation / Construction）：");
            if (status == null) return false;
            if (!status.equals("Operation") && !status.equals("Construction")) {
                throw new AppException(ErrorCode.INVALID_INPUT, "运营状态必须是 Operation 或 Construction。");
            }
            String yearText = readLine(input, output, "请输入开通或预计开通年份：");
            if (yearText == null) return false;
            int openingYear = parsePositiveInt(yearText, "开通年份");
            if (openingYear > Short.MAX_VALUE) {
                throw new AppException(ErrorCode.INVALID_INPUT, "开通年份必须在 1 到 32767 之间。");
            }

            String startText = readLine(input, output, "请输入首班时间（HH:mm，可留空）：");
            if (startText == null) return false;
            LocalTime startTime = parseOptionalTime(startText);
            String endText = readLine(input, output, "请输入末班时间（HH:mm，可留空）：");
            if (endText == null) return false;
            LocalTime endTime = parseOptionalTime(endText);
            String latitudeText = readLine(input, output, "请输入 source_latitude 原值（可留空）：");
            if (latitudeText == null) return false;
            BigDecimal latitude = parseOptionalCoordinate(latitudeText);
            String longitudeText = readLine(input, output, "请输入 source_longitude 原值（可留空）：");
            if (longitudeText == null) return false;
            BigDecimal longitude = parseOptionalCoordinate(longitudeText);
            stops.add(new StopDraft(station.stationId(), status, openingYear,
                    startTime, endTime, latitude, longitude));
        }

        output.printf("确认向线路 ID=%d（%s）的位置 %d 插入以下 %d 个车站？%n",
                selectedLine.lineId(), selectedLine.chineseName(), position, count);
        for (StopDraft stop : stops) {
            output.printf("车站 ID=%d，状态=%s，开通年份=%d%n",
                    stop.stationId(), stop.status(), stop.openingYear());
        }
        String confirmation = readLine(input, output, "输入 YES 确认，其他输入取消：");
        if (confirmation == null) return false;
        if (!"YES".equalsIgnoreCase(confirmation)) {
            output.println("已取消插入。");
            return true;
        }
        services.lines().insertStops(selectedLine.lineId(), position, stops);
        output.printf("插入成功：线路 ID=%d，起始位置=%d，车站数量=%d%n",
                selectedLine.lineId(), position, count);
        return true;
    }

    private StationView selectStationForInsertion(Scanner input, PrintStream output) {
        String keyword = readLine(input, output, "请输入要插入的车站查询关键词（留空取消）：");
        if (keyword == null) return null;
        if (keyword.isEmpty()) {
            output.println("已取消插入。");
            return null;
        }
        List<StationView> candidates = services.stations().search(keyword);
        if (candidates.isEmpty()) {
            output.println("未找到匹配车站，本次插入已取消。");
            return null;
        }
        for (StationView station : candidates) {
            output.printf("车站 ID=%d，站名=%s / %s，行政区=%s%n", station.stationId(),
                    station.key().chineseName(), station.englishName(), station.key().districtName());
        }
        if (candidates.size() == 1) return candidates.get(0);

        String choice = readLine(input, output, "请输入候选车站 ID（留空取消）：");
        if (choice == null) return null;
        if (choice.isEmpty()) {
            output.println("已取消插入。");
            return null;
        }
        final long stationId;
        try {
            stationId = Long.parseLong(choice);
        } catch (NumberFormatException failure) {
            throw new AppException(ErrorCode.INVALID_INPUT, "请选择列表中的车站 ID。");
        }
        return candidates.stream()
                .filter(station -> station.stationId() == stationId)
                .findFirst()
                .orElseThrow(() -> new AppException(
                        ErrorCode.INVALID_INPUT, "请选择列表中的车站 ID。"));
    }

    private static String readLine(Scanner input, PrintStream output, String prompt) {
        output.print(prompt);
        if (!input.hasNextLine()) return null;
        return input.nextLine().trim();
    }

    private static int parsePositiveInt(String text, String label) {
        try {
            int value = Integer.parseInt(text);
            if (value > 0) return value;
        } catch (NumberFormatException failure) {
            throw new AppException(ErrorCode.INVALID_INPUT, label + "必须是有效的正整数。");
        }
        throw new AppException(ErrorCode.INVALID_INPUT, label + "必须是有效的正整数。");
    }

    private static LocalTime parseOptionalTime(String text) {
        if (text.isEmpty()) return null;
        try {
            return LocalTime.parse(text);
        } catch (DateTimeParseException failure) {
            throw new AppException(ErrorCode.INVALID_INPUT, "时间格式不正确，请输入 HH:mm，例如 06:30。");
        }
    }

    private static BigDecimal parseOptionalCoordinate(String text) {
        if (text.isEmpty()) return null;
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException failure) {
            throw new AppException(ErrorCode.INVALID_INPUT, "源坐标必须是有效数字。");
        }
    }
}
