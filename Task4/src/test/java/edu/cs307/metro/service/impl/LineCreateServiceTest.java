package edu.cs307.metro.service.impl;

import edu.cs307.metro.bootstrap.AppBootstrap;
import edu.cs307.metro.common.AppException;
import edu.cs307.metro.common.ErrorCode;
import edu.cs307.metro.db.Transactions;
import edu.cs307.metro.model.LineDraft;
import edu.cs307.metro.model.LineView;
import edu.cs307.metro.support.LineCreateProbe;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class LineCreateServiceTest {
    @Test
    void createNormalizesNamesBindsParametersAndReturnsDatabaseAssignedIdAfterCommit() {
        LineCreateProbe probe = new LineCreateProbe();
        String english = "O'Brien / New Line; --";
        LineView result = AppBootstrap.create(probe.connections()).lines()
                .create(new LineDraft("\u3000 新 线路 \u3000", "  " + english + "  "));

        assertEquals(new LineView(42, "新 线路", english), result);
        assertEquals(Map.of(1, "新 线路", 2, english), probe.parameters);
        assertEquals("INSERT INTO public.metro_line (chinese_name, english_name) VALUES (?, ?) "
                        + "RETURNING line_id, chinese_name, english_name",
                probe.sql.replaceAll("\\s+", " ").trim());
        assertEquals(List.of("open", "autoCommit:false", "prepare", "insert", "closeRows",
                "closeStatement", "commit", "closeConnection"), probe.events);
    }

    static Stream<Arguments> invalidNames() {
        return Stream.of(
                Arguments.of((LineDraft) null),
                Arguments.of(new LineDraft(null, "Test")),
                Arguments.of(new LineDraft("测试", null)),
                Arguments.of(new LineDraft("", "Test")),
                Arguments.of(new LineDraft("测试", "")),
                Arguments.of(new LineDraft("\u3000\u2003", "Test")),
                Arguments.of(new LineDraft("测试", " \t\n")),
                Arguments.of(new LineDraft("测试\0线", "Test")),
                Arguments.of(new LineDraft("测试", "Test\0Line")));
    }

    @ParameterizedTest
    @MethodSource("invalidNames")
    void invalidInputDoesNotOpenADatabaseConnection(LineDraft input) {
        var service = new JdbcLineService(new Transactions(
                () -> { throw new AssertionError("Invalid input must not open a database connection"); }));
        AppException error = assertThrows(AppException.class, () -> service.create(input));
        assertEquals(ErrorCode.INVALID_INPUT, error.code());
    }

    @Test
    void duplicateFailureRollsBackAndClosesStatementWithoutExposingDatabaseDetail() {
        LineCreateProbe probe = new LineCreateProbe();
        probe.executionFailure = new SQLException("private unique constraint detail", "23505");
        AppException error = assertThrows(AppException.class,
                () -> AppBootstrap.create(probe.connections()).lines()
                        .create(new LineDraft("测试线", "Test Line")));
        assertEquals(ErrorCode.DUPLICATE, error.code());
        assertSame(probe.executionFailure, error.getCause());
        assertFalse(error.getMessage().contains("private unique constraint detail"));
        assertEquals(List.of("open", "autoCommit:false", "prepare", "insert", "closeStatement",
                "rollback", "closeConnection"), probe.events);
    }

    @Test
    void missingReturningRowFailsAndClosesBothResources() {
        LineCreateProbe probe = new LineCreateProbe();
        probe.returnsRow = false;
        AppException error = assertThrows(AppException.class,
                () -> AppBootstrap.create(probe.connections()).lines()
                        .create(new LineDraft("测试线", "Test Line")));
        assertEquals(ErrorCode.DB_ERROR, error.code());
        assertEquals(List.of("open", "autoCommit:false", "prepare", "insert", "closeRows",
                "closeStatement", "rollback", "closeConnection"), probe.events);
    }

    @Test
    void commitFailurePreventsReturningSuccessfulResult() {
        LineCreateProbe probe = new LineCreateProbe();
        probe.commitFailure = new SQLException("private commit detail", "08006");
        AppException error = assertThrows(AppException.class,
                () -> AppBootstrap.create(probe.connections()).lines()
                        .create(new LineDraft("测试线", "Test Line")));
        assertEquals(ErrorCode.DB_ERROR, error.code());
        assertEquals(List.of("open", "autoCommit:false", "prepare", "insert", "closeRows",
                "closeStatement", "commit", "rollback", "closeConnection"), probe.events);
    }
}
