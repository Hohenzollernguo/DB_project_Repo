package edu.cs307.metro.db;

import edu.cs307.metro.common.AppException;
import edu.cs307.metro.common.ErrorCode;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TransactionsTest {
    private static final class Probe {
        final List<String> events = new ArrayList<>();
        SQLException commitFailure;
        SQLException rollbackFailure;
        final Connection connection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "setAutoCommit" -> events.add("autoCommit:" + args[0]);
                        case "setReadOnly" -> events.add("readOnly:" + args[0]);
                        case "setTransactionIsolation" -> events.add("isolation:" + args[0]);
                        case "commit" -> {
                            events.add("commit");
                            if (commitFailure != null) throw commitFailure;
                        }
                        case "rollback" -> {
                            events.add("rollback");
                            if (rollbackFailure != null) throw rollbackFailure;
                        }
                        case "close" -> events.add("close");
                        default -> throw new AssertionError("Unexpected JDBC call: " + method.getName());
                    }
                    return null;
                });

        Transactions transactions() {
            return new Transactions(() -> connection);
        }
    }

    @Test
    void successCommitsBeforeClosingAndReturnsResult() {
        Probe probe = new Probe();
        int result = probe.transactions().write(connection -> {
            assertSame(probe.connection, connection);
            probe.events.add("work");
            return 42;
        });
        assertEquals(42, result);
        assertEquals(List.of("autoCommit:false", "work", "commit", "close"), probe.events);
    }

    @Test
    void sqlFailureRollsBackAndMapsTheError() {
        Probe probe = new Probe();
        SQLException original = new SQLException("private SQL detail", "23505");
        AppException error = assertThrows(AppException.class,
                () -> probe.transactions().write(connection -> { throw original; }));
        assertEquals(ErrorCode.DUPLICATE, error.code());
        assertSame(original, error.getCause());
        assertFalse(error.getMessage().contains("private SQL detail"));
        assertEquals(List.of("autoCommit:false", "rollback", "close"), probe.events);
    }

    @Test
    void businessFailureIsPreservedAndRolledBack() {
        Probe probe = new Probe();
        AppException original = new AppException(ErrorCode.NOT_FOUND, "目标不存在");
        AppException error = assertThrows(AppException.class,
                () -> probe.transactions().write(connection -> { throw original; }));
        assertSame(original, error);
        assertEquals(List.of("autoCommit:false", "rollback", "close"), probe.events);
    }

    @Test
    void deferredConstraintFailureAtCommitStillRollsBack() {
        Probe probe = new Probe();
        probe.commitFailure = new SQLException("uq_line_position", "23505");
        AppException error = assertThrows(AppException.class,
                () -> probe.transactions().write(connection -> null));
        assertEquals(ErrorCode.DUPLICATE, error.code());
        assertEquals(List.of("autoCommit:false", "commit", "rollback", "close"), probe.events);
    }

    @Test
    void rollbackFailureDoesNotHideOriginalFailure() {
        Probe probe = new Probe();
        SQLException original = new SQLException("original", "23514");
        probe.rollbackFailure = new SQLException("rollback unavailable");
        AppException error = assertThrows(AppException.class,
                () -> probe.transactions().write(connection -> { throw original; }));
        assertSame(original, error.getCause());
        assertArrayEquals(new Throwable[]{probe.rollbackFailure}, original.getSuppressed());
        assertEquals("close", probe.events.get(probe.events.size() - 1));
    }

    @Test
    void snapshotIsReadOnlyAndRepeatableReadBeforeWorkStarts() {
        Probe probe = new Probe();
        probe.transactions().readSnapshot(connection -> {
            probe.events.add("work");
            return null;
        });
        assertEquals(List.of("readOnly:true", "isolation:" + Connection.TRANSACTION_REPEATABLE_READ,
                "autoCommit:false", "work", "commit", "close"), probe.events);
    }

    @Test
    void deleteForeignKeyFailureHasInUseMeaning() {
        Probe probe = new Probe();
        AppException error = assertThrows(AppException.class,
                () -> probe.transactions().delete(connection -> { throw new SQLException("fk", "23503"); }));
        assertEquals(ErrorCode.IN_USE, error.code());
    }

    @Test
    void eachRequestObtainsAndClosesItsOwnConnection() {
        Probe first = new Probe();
        Probe second = new Probe();
        AtomicInteger opens = new AtomicInteger();
        Transactions transactions = new Transactions(
                () -> opens.getAndIncrement() == 0 ? first.connection : second.connection);
        transactions.write(connection -> null);
        transactions.write(connection -> null);
        assertEquals(2, opens.get());
        assertEquals(List.of("autoCommit:false", "commit", "close"), first.events);
        assertEquals(first.events, second.events);
    }

    @Test
    void programmingErrorAlsoRollsBack() {
        Probe probe = new Probe();
        AssertionError original = new AssertionError("invariant");
        assertSame(original, assertThrows(AssertionError.class,
                () -> probe.transactions().write(connection -> { throw original; })));
        assertEquals(List.of("autoCommit:false", "rollback", "close"), probe.events);
    }
}
