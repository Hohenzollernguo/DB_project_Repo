package edu.cs307.metro.cli;

import edu.cs307.metro.bootstrap.AppBootstrap;
import edu.cs307.metro.bootstrap.AppServices;
import edu.cs307.metro.common.AppException;
import edu.cs307.metro.common.ErrorCode;
import edu.cs307.metro.db.ConnectionFactory;
import edu.cs307.metro.model.LineView;
import edu.cs307.metro.support.LineCreateProbe;
import edu.cs307.metro.support.LineDeleteSelectionProbe;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class CliLineManagementTest {
    private static String run(ConnectionFactory connections, String input) {
        return run(AppBootstrap.create(connections), input);
    }

    private static String run(LineDeleteSelectionProbe probe, String input) {
        return run(new AppServices(null, probe.lines(), null, null, null, null, null), input);
    }

    private static String run(AppServices services, String input) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8);
             Scanner scanner = new Scanner(input)) {
            new CliApp(services).run(scanner, output);
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void createDisplaysTheGeneratedIdAndNormalizedNames() {
        LineCreateProbe probe = new LineCreateProbe();
        String output = run(probe.connections(), "9\n 新线路 \n New Line \n0\n");
        assertTrue(output.contains("添加成功：ID=42，中文名=新线路，英文名=New Line"));
        assertEquals(Map.of(1, "新线路", 2, "New Line"), probe.parameters);
        assertTrue(output.lastIndexOf("请选择操作") > output.indexOf("添加成功"));
    }

    @Test
    void duplicateCreateShowsTheErrorAndReturnsToTheMenu() {
        LineCreateProbe probe = new LineCreateProbe();
        probe.executionFailure = new SQLException("private database detail", "23505");
        String output = run(probe.connections(), "9\n重复线\nDuplicate\n0\n");
        assertTrue(output.contains("[DUPLICATE]"));
        assertFalse(output.contains("添加成功"));
        assertFalse(output.contains("private database detail"));
        assertTrue(output.lastIndexOf("请选择操作") > output.indexOf("[DUPLICATE]"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"9\n", "9\n中文名\n", "10\n"})
    void incompleteCreateOrDeleteInputExitsBeforeQuerying(String input) {
        assertDoesNotThrow(() -> run(
                () -> { throw new AssertionError("Incomplete input must not open a connection"); }, input));
    }

    @Test
    void deleteResolvesNumberToInternalIdAndConfirmsTheNamedTarget() {
        LineDeleteSelectionProbe probe = new LineDeleteSelectionProbe();
        String output = run(probe, "10\n13\nyes\n0\n");
        assertTrue(output.contains("删除成功：ID=42"));
        assertTrue(output.contains("确认删除线路 ID=42（13号线 / Line 13）"));
        assertEquals(List.of("13"), probe.searches);
        assertEquals(List.of(42L), probe.deletions);
        assertTrue(output.lastIndexOf("请选择操作") > output.indexOf("删除成功"));
    }

    @Test
    void multipleMatchesListAllCandidatesAndDeleteOnlyTheChosenId() {
        LineDeleteSelectionProbe probe = new LineDeleteSelectionProbe();
        probe.matches = List.of(new LineView(42, "13号线", "Line 13"),
                new LineView(71, "13号线北延段", "Line 13 Phase 2 North Extension"));
        String output = run(probe, "10\n13\n71\nYES\n0\n");
        assertTrue(output.contains("ID=42"));
        assertTrue(output.contains("ID=71"));
        assertTrue(output.contains("Line 13 Phase 2 North Extension"));
        assertEquals(List.of(71L), probe.deletions);
        assertTrue(output.contains("删除成功：ID=71"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"13", "999", "abc", "9223372036854775808"})
    void invalidOrUnlistedSelectionNeverDeletes(String selectedId) {
        LineDeleteSelectionProbe probe = new LineDeleteSelectionProbe();
        probe.matches = List.of(new LineView(42, "13号线", "Line 13"),
                new LineView(71, "13号线北延段", "Line 13 Phase 2 North Extension"));
        String output = run(probe, "10\n13\n" + selectedId + "\n0\n");
        assertTrue(output.contains("[INVALID_INPUT]"));
        assertTrue(probe.deletions.isEmpty());
        assertFalse(output.contains("确认删除线路"));
    }

    @Test
    void noMatchesGiveFeedbackBeforeAnyConfirmationOrDeletion() {
        LineDeleteSelectionProbe probe = new LineDeleteSelectionProbe();
        probe.matches = List.of();
        String output = run(probe, "10\n13\n0\n");
        assertTrue(output.contains("未找到编号为 13 的线路"));
        assertTrue(probe.deletions.isEmpty());
        assertFalse(output.contains("确认删除线路"));
    }

    @Test
    void cancellingDeletionDoesNotWrite() {
        LineDeleteSelectionProbe probe = new LineDeleteSelectionProbe();
        String output = run(probe, "10\n13\nNO\n0\n");
        assertTrue(output.contains("已取消删除"));
        assertEquals(List.of("13"), probe.searches);
        assertTrue(probe.deletions.isEmpty());
    }

    @Test
    void blankSelectionCancelsMultipleMatchDeletion() {
        LineDeleteSelectionProbe probe = new LineDeleteSelectionProbe();
        probe.matches = List.of(new LineView(42, "13号线", "Line 13"),
                new LineView(71, "13号线北延段", "Line 13 Phase 2 North Extension"));
        String output = run(probe, "10\n13\n\n0\n");
        assertTrue(output.contains("已取消删除"));
        assertTrue(probe.deletions.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "-1", "1%", ""})
    void malformedDeleteNumbersReturnBeforeSearching(String number) {
        LineDeleteSelectionProbe probe = new LineDeleteSelectionProbe();
        String output = run(probe, "10\n" + number + "\n0\n");
        assertTrue(output.contains("线路编号必须是正整数"));
        assertTrue(probe.searches.isEmpty());
        assertTrue(probe.deletions.isEmpty());
    }

    @Test
    void eofAfterQueryDoesNotDelete() {
        LineDeleteSelectionProbe probe = new LineDeleteSelectionProbe();
        assertDoesNotThrow(() -> run(probe, "10\n13\n"));
        assertEquals(List.of("13"), probe.searches);
        assertTrue(probe.deletions.isEmpty());
    }

    @Test
    void inUseDeletionShowsTheErrorAndReturnsToTheMenu() {
        LineDeleteSelectionProbe probe = new LineDeleteSelectionProbe();
        probe.deleteFailure = new AppException(ErrorCode.IN_USE, "线路仍有关联站点。");
        String output = run(probe, "10\n13\nYES\n0\n");
        assertTrue(output.contains("[IN_USE]"));
        assertFalse(output.contains("删除成功"));
        assertEquals(List.of(42L), probe.deletions);
    }

    @Test
    void targetRemovedAfterSelectionDoesNotPrintSuccessfulDeletion() {
        LineDeleteSelectionProbe probe = new LineDeleteSelectionProbe();
        probe.deleteFailure = new AppException(ErrorCode.NOT_FOUND, "指定线路不存在。");
        String output = run(probe, "10\n13\nYES\n0\n");
        assertTrue(output.contains("[NOT_FOUND]"));
        assertFalse(output.contains("删除成功"));
    }
}
