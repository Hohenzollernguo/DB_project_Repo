package edu.cs307.metro.service.impl;

import edu.cs307.metro.bootstrap.AppBootstrap;
import edu.cs307.metro.common.AppException;
import edu.cs307.metro.common.ErrorCode;
import edu.cs307.metro.db.Transactions;
import edu.cs307.metro.support.LineUpdateProbe;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class JdbcLineDeleteTest {
    @Test
    void deletesTheBoundIdAndCommitsBeforeReturning() {
        LineUpdateProbe probe = new LineUpdateProbe();
        AppBootstrap.create(probe.connections()).lines().delete(42);
        assertEquals("DELETE FROM public.metro_line WHERE line_id = ?", probe.sql);
        assertEquals(Map.of(1, 42L), probe.parameters);
        assertEquals(List.of("open", "autoCommit:false", "prepare", "update", "closeStatement",
                "commit", "closeConnection"), probe.events);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void invalidIdsFailBeforeOpeningAConnection(long id) {
        JdbcLineService service = new JdbcLineService(new Transactions(
                () -> { throw new AssertionError("Invalid ID must not open a connection"); }));
        AppException error = assertThrows(AppException.class, () -> service.delete(id));
        assertEquals(ErrorCode.INVALID_INPUT, error.code());
    }

    @Test
    void missingLineRollsBackAndReturnsNotFound() {
        LineUpdateProbe probe = new LineUpdateProbe();
        probe.affectedRows = 0;
        AppException error = assertThrows(AppException.class,
                () -> AppBootstrap.create(probe.connections()).lines().delete(Long.MAX_VALUE));
        assertEquals(ErrorCode.NOT_FOUND, error.code());
        assertEquals(List.of("open", "autoCommit:false", "prepare", "update", "closeStatement",
                "rollback", "closeConnection"), probe.events);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 2})
    void unexpectedRowCountsRollback(int count) {
        LineUpdateProbe probe = new LineUpdateProbe();
        probe.affectedRows = count;
        AppException error = assertThrows(AppException.class,
                () -> AppBootstrap.create(probe.connections()).lines().delete(1));
        assertEquals(ErrorCode.DB_ERROR, error.code());
        assertTrue(probe.events.contains("rollback"));
        assertFalse(probe.events.contains("commit"));
    }

    static Stream<Arguments> sqlFailures() {
        return Stream.of(Arguments.of("23503", ErrorCode.IN_USE),
                Arguments.of("08006", ErrorCode.DB_ERROR));
    }

    @ParameterizedTest
    @MethodSource("sqlFailures")
    void constraintAndConnectionFailuresRollbackWithSafeErrorCodes(String state, ErrorCode code) {
        LineUpdateProbe probe = new LineUpdateProbe();
        probe.executionFailure = new SQLException("private database detail", state);
        AppException error = assertThrows(AppException.class,
                () -> AppBootstrap.create(probe.connections()).lines().delete(1));
        assertEquals(code, error.code());
        assertSame(probe.executionFailure, error.getCause());
        assertFalse(error.getMessage().contains("private database detail"));
        assertEquals(List.of("open", "autoCommit:false", "prepare", "update", "closeStatement",
                "rollback", "closeConnection"), probe.events);
    }

    @Test
    void commitFailureIsReportedAndRolledBack() {
        LineUpdateProbe probe = new LineUpdateProbe();
        probe.commitFailure = new SQLException("private commit detail", "08006");
        AppException error = assertThrows(AppException.class,
                () -> AppBootstrap.create(probe.connections()).lines().delete(1));
        assertEquals(ErrorCode.DB_ERROR, error.code());
        assertEquals(List.of("open", "autoCommit:false", "prepare", "update", "closeStatement",
                "commit", "rollback", "closeConnection"), probe.events);
    }
}
