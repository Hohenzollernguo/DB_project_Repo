package edu.cs307.metro;

import edu.cs307.metro.bootstrap.AppBootstrap;
import edu.cs307.metro.bootstrap.AppServices;
import edu.cs307.metro.cli.CliApp;
import edu.cs307.metro.common.AppException;
import edu.cs307.metro.db.DatabaseStatus;
import edu.cs307.metro.ui.DesktopApp;
import java.awt.GraphicsEnvironment;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public final class Main {
    private Main() {}

    public static void main(String[] args) {
        // Windows 的控制台原生编码可能与源码/终端不一致，明确输出 UTF-8。
        PrintStream output = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        PrintStream error = new PrintStream(System.err, true, StandardCharsets.UTF_8);
        int code = run(args, System.in, output, error);
        if (code != 0) {
            System.exit(code);
        }
    }

    /** 返回状态码便于自动验证；默认 CLI，一次启动后可反复选择菜单。 */
    public static int run(String[] args, InputStream input, PrintStream output, PrintStream error) {
        if (args.length > 1) {
            error.println("只接受一个启动参数：--cli / --gui / --check-db / --help");
            return 2;
        }
        String mode = args.length == 0 ? "--cli" : args[0];
        if ("--help".equals(mode)) {
            output.println("用法：edu.cs307.metro.Main [--cli | --gui | --check-db | --help]");
            output.println("默认 --cli，支持线路添加、修改和删除。--check-db 检查连接与目标库身份。");
            return 0;
        }
        if (!"--cli".equals(mode) && !"--gui".equals(mode) && !"--check-db".equals(mode)) {
            error.println("未知启动参数；使用 --help 查看用法。");
            return 2;
        }
        AppServices services = AppBootstrap.create();
        try {
            switch (mode) {
                case "--cli" -> new CliApp(services).run(new Scanner(input, StandardCharsets.UTF_8), output);
                case "--gui" -> {
                    if (GraphicsEnvironment.isHeadless()) {
                        error.println("当前环境不支持图形界面，请使用 --cli。");
                        return 1;
                    }
                    new DesktopApp(services).show();
                }
                case "--check-db" -> {
                    DatabaseStatus status = services.database().check();
                    output.printf("数据库=%s，schema=%s，PostgreSQL=%s%n",
                            status.database(), status.schema(), status.serverVersion());
                }
                default -> throw new IllegalStateException("Unreachable mode");
            }
            return 0;
        } catch (AppException failure) {
            error.printf("[%s] %s%n", failure.code(), failure.getMessage());
            return 1;
        }
    }
}
