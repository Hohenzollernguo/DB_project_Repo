package edu.cs307.metro.support;

import edu.cs307.metro.db.ConnectionFactory;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 线路添加的 JDBC 边界探针，用于事务、资源关闭和界面后台线程验证。 */
public final class LineCreateProbe {
    public final List<String> events = new ArrayList<>();
    public final Map<Integer, String> parameters = new LinkedHashMap<>();
    public String sql;
    public long generatedId = 42;
    public boolean returnsRow = true;
    public SQLException executionFailure;
    public SQLException commitFailure;
    public Runnable beforeExecute = () -> {};

    private final ResultSet rows = (ResultSet) Proxy.newProxyInstance(
            ResultSet.class.getClassLoader(), new Class<?>[]{ResultSet.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "next" -> returnsRow;
                case "getLong" -> {
                    if (!"line_id".equals(args[0])) throw new AssertionError(args[0]);
                    yield generatedId;
                }
                case "getString" -> switch ((String) args[0]) {
                    case "chinese_name" -> parameters.get(1);
                    case "english_name" -> parameters.get(2);
                    default -> throw new AssertionError(args[0]);
                };
                case "close" -> { events.add("closeRows"); yield null; }
                default -> throw new AssertionError("Unexpected result call: " + method.getName());
            });

    private final PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(
            PreparedStatement.class.getClassLoader(), new Class<?>[]{PreparedStatement.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "setString" -> {
                    parameters.put((int) args[0], (String) args[1]);
                    yield null;
                }
                case "executeQuery" -> {
                    beforeExecute.run();
                    events.add("insert");
                    if (executionFailure != null) throw executionFailure;
                    yield rows;
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
