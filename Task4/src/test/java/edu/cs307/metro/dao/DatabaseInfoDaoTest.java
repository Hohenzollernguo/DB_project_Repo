package edu.cs307.metro.dao;

import edu.cs307.metro.db.*;
import edu.cs307.metro.service.DatabaseDiagnostics;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DatabaseInfoDaoTest {
    @Test
    void diagnosticsMapsIdentityAndClosesResourcesInOrder() {
        List<String> events = new ArrayList<>();
        ResultSet rows = (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "next" -> true;
                    case "getString" -> switch ((int) args[0]) {
                        case 1 -> "metro_test";
                        case 2 -> "public";
                        case 3 -> "16.test";
                        default -> throw new AssertionError();
                    };
                    case "close" -> { events.add("closeRows"); yield null; }
                    default -> throw new AssertionError(method.getName());
                });
        PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(), new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "executeQuery" -> { events.add("query"); yield rows; }
                    case "close" -> { events.add("closeStatement"); yield null; }
                    default -> throw new AssertionError(method.getName());
                });
        Connection connection = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "prepareStatement" -> {
                        assertTrue(((String) args[0]).startsWith("SELECT current_database()"));
                        yield statement;
                    }
                    case "setReadOnly", "setTransactionIsolation", "setAutoCommit" -> null;
                    case "commit" -> { events.add("commit"); yield null; }
                    case "close" -> { events.add("closeConnection"); yield null; }
                    default -> throw new AssertionError(method.getName());
                });
        DatabaseDiagnostics diagnostics = new DatabaseDiagnostics(
                new Transactions(() -> connection), new DatabaseInfoDao());
        assertEquals(new DatabaseStatus("metro_test", "public", "16.test"), diagnostics.check());
        assertEquals(List.of("query", "closeRows", "closeStatement", "commit", "closeConnection"), events);
    }
}
