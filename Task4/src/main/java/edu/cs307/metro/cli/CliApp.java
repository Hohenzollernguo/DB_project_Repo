package edu.cs307.metro.cli;

import edu.cs307.metro.bootstrap.AppServices;
import edu.cs307.metro.common.AppException;
import edu.cs307.metro.db.DatabaseStatus;
import java.io.PrintStream;
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
                    2. 线路管理（新增/修改/删除/查询，待实现）
                    3. 站线关系（插入/移除/属性修改，待实现）
                    4. 前后第 n 站（待接入）
                    5. 两站路径（待实现）
                    6. 两地标路线（待实现）
                    7. 票价（规则待配置）
                    8. 检查数据库连接
                    0. 退出""");
            output.print("请选择操作：");
            if (!input.hasNextLine()) {
                return;
            }
            String choice = input.nextLine().trim();
            try {
                switch (choice) {
                    case "0" -> { return; }
                    case "1", "2", "3", "4", "5", "6" ->
                            output.println("此功能的接口和入口已预留，业务与输入流程尚未实现。");
                    case "7" -> output.println("计费规则与基础数据待确认，当前无法计算票价。");
                    case "8" -> {
                        DatabaseStatus status = services.database().check();
                        output.printf("已连接：数据库=%s，schema=%s，PostgreSQL=%s%n",
                                status.database(), status.schema(), status.serverVersion());
                    }
                    default -> output.println("请输入 0 到 8。");
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
