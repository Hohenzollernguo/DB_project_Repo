package edu.cs307.metro.cli;

import edu.cs307.metro.bootstrap.AppServices;
import edu.cs307.metro.common.AppException;
import edu.cs307.metro.db.DatabaseStatus;
import edu.cs307.metro.model.LineDraft;
import edu.cs307.metro.model.LineView;
import java.io.PrintStream;
import java.util.List;
import java.util.Scanner;

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
                    1. 车站管理（新增/修改/删除/查询，待实现）
                    2. 修改线路中英文名
                    3. 站线关系（插入/移除/属性修改，待实现）
                    4. 前后第 n 站（待接入）
                    5. 两站路径（待实现）
                    6. 两地标路线（待实现）
                    7. 票价（规则待配置）
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
                    case "1", "3", "4", "5", "6" ->
                            output.println("此功能的接口和入口已预留，业务与输入流程尚未实现。");
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
                    case "7" -> output.println("计费规则与基础数据待确认，当前无法计算票价。");
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
}
