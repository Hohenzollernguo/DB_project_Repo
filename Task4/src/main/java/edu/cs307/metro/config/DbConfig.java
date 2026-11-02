package edu.cs307.metro.config;

import edu.cs307.metro.common.AppException;
import edu.cs307.metro.common.ErrorCode;
import java.util.Map;

/** 不使用自动输出全部字段的 record，防止密码进入日志。 */
public final class DbConfig {
    private final String url;
    private final String user;
    private final String password;

    private DbConfig(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    public static DbConfig fromEnvironment() {
        return from(System.getenv());
    }

    public static DbConfig from(Map<String, String> environment) {
        String url = environment.getOrDefault("DB_URL", "").trim();
        String user = environment.get("DB_USER");
        String password = environment.get("DB_PASSWORD");
        if (!url.startsWith("jdbc:postgresql:") || user == null || user.isBlank() || password == null) {
            throw new AppException(ErrorCode.INVALID_INPUT,
                    "请配置 PostgreSQL 的 DB_URL、DB_USER、DB_PASSWORD 环境变量。");
        }
        // 密码按原样传递，不能 trim；本地数据库也可能允许空密码。
        return new DbConfig(url, user.trim(), password);
    }

    public String url() { return url; }
    public String user() { return user; }
    public String password() { return password; }

    @Override
    public String toString() {
        return "DbConfig[credentials hidden]";
    }
}
