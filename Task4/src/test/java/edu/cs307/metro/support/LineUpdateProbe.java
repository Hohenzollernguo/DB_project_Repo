package edu.cs307.metro.support;

import edu.cs307.metro.db.ConnectionFactory;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 记录 JDBC 边界行为；真实约束和数据回滚另由 PostgreSQL 测试验证。 */
public final class LineUpdateProbe {
    public final List<String> events = new ArrayList<>();
    public final Map<Integer, Object> parameters = new LinkedHashMap<>();
    public String sql;
    public int affectedRows = 1;
    public SQLException executionFailure;
    public SQLException commitFailure;
    public Runnable beforeExecute = () -> {};

    private final PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(
            PreparedStatement.class.getClassLoader(), new Class<?>[]{PreparedStatement.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "setString", "setLong" -> {
                    parameters.put((int) args[0], args[1]);
                    yield null;
                }
                case "executeUpdate" -> {
                    beforeExecute.run();
                    events.add("update");
                    if (executionFailure != null) throw executionFailure;
                    yield affectedRows;
                }
                case "close" -> { events.add("closeStatement"); yield null; }
                default -> throw new AssertionError("Unexpected statement call: " + method.getName());
            });

    private final Connection connection = (Connection) Proxy.newProxyInstance(
            Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "prepareStatement" -> {
                    sql = (String) args[0];
                    events.add("prepare");
                    yield statement;
                }
                case "setAutoCommit" -> { events.add("autoCommit:" + args[0]); yield null; }
                case "commit" -> {
                    events.add("commit");
                    if (commitFailure != null) throw commitFailure;
                    yield null;
                }
                case "rollback" -> { events.add("rollback"); yield null; }
                case "close" -> { events.add("closeConnection"); yield null; }
                default -> throw new AssertionError("Unexpected connection call: " + method.getName());
            });

    public ConnectionFactory connections() {
        return () -> {
            events.add("open");
            return connection;
        };
    }
}
