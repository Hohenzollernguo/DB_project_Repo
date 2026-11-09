package edu.cs307.metro.service.impl;

import edu.cs307.metro.bootstrap.AppBootstrap;
import edu.cs307.metro.common.AppException;
import edu.cs307.metro.common.ErrorCode;
import edu.cs307.metro.db.Transactions;
import edu.cs307.metro.model.LineDraft;
import edu.cs307.metro.model.LineView;
import edu.cs307.metro.support.LineUpdateProbe;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class JdbcLineServiceTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "\u3000\u2003", "0", "-1", "+13", "01", "1.3",
            "1 3", "1%", "1_", "abc", "Line 13", "１３", "13\0"})
    void invalidSearchNumbersFailBeforeOpeningADatabaseConnection(String number) {
        JdbcLineService service = new JdbcLineService(new Transactions(
                () -> { throw new AssertionError("Invalid input must not open a connection"); }));

        AppException error = assertThrows(AppException.class, () -> service.search(number));

        assertEquals(ErrorCode.INVALID_INPUT, error.code());
    }

    @Test
    void bootstrapUpdatesBothNamesWithBoundParametersAndCommitsBeforeReturning() {
        LineUpdateProbe probe = new LineUpdateProbe();
        String english = "O'Brien / Line 2; --";
        LineDraft input = new LineDraft("\u3000 新 二号线 \u3000", "  " + english + "  ");

        LineView result = AppBootstrap.create(probe.connections()).lines().update(42, input);

        assertEquals(new LineView(42, "新 二号线", english), result);
        assertEquals(Map.of(1, "新 二号线", 2, english, 3, 42L), probe.parameters);
        assertEquals("UPDATE public.metro_line SET chinese_name = ?, english_name = ? WHERE line_id = ?",
                probe.sql.replaceAll("\\s+", " ").trim());
        assertEquals(List.of("open", "autoCommit:false", "prepare", "update", "closeStatement",
                "commit", "closeConnection"), probe.events);
        assertEquals("  " + english + "  ", input.englishName());
    }

    static Stream<Arguments> invalidInputs() {
        LineDraft valid = new LineDraft("测试线", "Test Line");
        return Stream.of(
                Arguments.of(0L, valid), Arguments.of(-1L, valid),
                Arguments.of(1L, null),
                Arguments.of(1L, new LineDraft(null, "Test")),
                Arguments.of(1L, new LineDraft("测试", null)),
                Arguments.of(1L, new LineDraft("", "Test")),
                Arguments.of(1L, new LineDraft("测试", " \t\n")),
                Arguments.of(1L, new LineDraft("\u3000\u2003", "Test")),
                Arguments.of(1L, new LineDraft("测试\0线", "Test")),
                Arguments.of(1L, new LineDraft("测试", "Test\0Line")));
    }

    @ParameterizedTest
    @MethodSource("invalidInputs")
    void invalidInputsFailBeforeOpeningADatabaseConnection(long id, LineDraft input) {
        JdbcLineService service = new JdbcLineService(new Transactions(
                () -> { throw new AssertionError("Invalid input must not open a connection"); }));
        AppException error = assertThrows(AppException.class, () -> service.update(id, input));
        assertEquals(ErrorCode.INVALID_INPUT, error.code());
    }

    @Test
    void missingLineRollsBackInsteadOfReportingSuccess() {
        LineUpdateProbe probe = new LineUpdateProbe();
        probe.affectedRows = 0;
        AppException error = assertThrows(AppException.class,
                () -> AppBootstrap.create(probe.connections()).lines()
                        .update(Long.MAX_VALUE, new LineDraft("测试线", "Test Line")));
        assertEquals(ErrorCode.NOT_FOUND, error.code());
        assertEquals(List.of("open", "autoCommit:false", "prepare", "update", "closeStatement",
                "rollback", "closeConnection"), probe.events);
    }

    @Test
    void unexpectedRowCountIsRolledBack() {
        LineUpdateProbe probe = new LineUpdateProbe();
        probe.affectedRows = 2;
        AppException error = assertThrows(AppException.class,
                () -> AppBootstrap.create(probe.connections()).lines()
                        .update(1, new LineDraft("测试线", "Test Line")));
        assertEquals(ErrorCode.DB_ERROR, error.code());
        assertTrue(probe.events.contains("rollback"));
        assertFalse(probe.events.contains("commit"));
    }

    static Stream<Arguments> sqlFailures() {
        return Stream.of(Arguments.of("23505", ErrorCode.DUPLICATE),
                Arguments.of("08006", ErrorCode.DB_ERROR));
    }

    @ParameterizedTest
    @MethodSource("sqlFailures")
    void sqlErrorsCloseTheStatementAndRollbackWithoutExposingDatabaseDetails(String state, ErrorCode code) {
        LineUpdateProbe probe = new LineUpdateProbe();
        probe.executionFailure = new SQLException("private database detail", state);
        AppException error = assertThrows(AppException.class,
                () -> AppBootstrap.create(probe.connections()).lines()
                        .update(1, new LineDraft("测试线", "Test Line")));
        assertEquals(code, error.code());
        assertSame(probe.executionFailure, error.getCause());
        assertFalse(error.getMessage().contains("private database detail"));
        assertEquals(List.of("open", "autoCommit:false", "prepare", "update", "closeStatement",
                "rollback", "closeConnection"), probe.events);
    }

    @Test
    void commitFailureDoesNotReturnASuccessfulLineView() {
        LineUpdateProbe probe = new LineUpdateProbe();
        probe.commitFailure = new SQLException("private commit detail", "08006");
        AppException error = assertThrows(AppException.class,
                () -> AppBootstrap.create(probe.connections()).lines()
                        .update(1, new LineDraft("测试线", "Test Line")));
        assertEquals(ErrorCode.DB_ERROR, error.code());
        assertEquals(List.of("open", "autoCommit:false", "prepare", "update", "closeStatement",
                "commit", "rollback", "closeConnection"), probe.events);
    }
}
