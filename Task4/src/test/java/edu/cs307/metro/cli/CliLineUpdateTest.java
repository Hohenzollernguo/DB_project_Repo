package edu.cs307.metro.cli;

import edu.cs307.metro.bootstrap.AppServices;
import edu.cs307.metro.model.LineView;
import edu.cs307.metro.support.LineUpdateSelectionProbe;
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

class CliLineUpdateTest {
    private static String run(LineUpdateSelectionProbe probe, String input) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        AppServices services = new AppServices(null, probe.service(), null, null, null, null, null);
        try (PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8);
             Scanner scanner = new Scanner(input)) {
            new CliApp(services).run(scanner, output);
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private static void multipleCandidates(LineUpdateSelectionProbe probe) {
        probe.candidates = List.of(new LineView(42, "13号线", "Line 13"),
                new LineView(99, "13号线二期北延", "Line 13 Phase 2 North Extension"));
    }

    @Test
    void lineNumberResolvesToActualIdAndReturnsToTheMenu() {
        LineUpdateSelectionProbe probe = new LineUpdateSelectionProbe();
        String output = run(probe, "2\n 13 \n 新线路 \n New Line \n0\n");
        assertTrue(output.contains("修改成功：ID=42，中文名=新线路，英文名=New Line"));
        assertEquals(List.of("13"), probe.searches);
        assertEquals(Map.of(1, "新线路", 2, "New Line", 3, 42L), probe.writes.parameters);
        assertTrue(output.lastIndexOf("请选择操作") > output.indexOf("修改成功"));
    }

    @Test
    void multipleCandidatesListNamesAndUpdateOnlyTheChosenId() {
        LineUpdateSelectionProbe probe = new LineUpdateSelectionProbe();
        multipleCandidates(probe);
        String output = run(probe, "2\n13\n99\n北延新名\nNew North Extension\n0\n");
        assertTrue(output.contains("ID=42"));
        assertTrue(output.contains("ID=99"));
        assertTrue(output.contains("Line 13 Phase 2 North Extension"));
        assertEquals(99L, probe.writes.parameters.get(3));
        assertEquals(1, probe.writes.events.stream().filter("update"::equals).count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"13", "0", "-1", "abc", "9223372036854775808"})
    void invalidOrUnlistedCandidateIdDoesNotWrite(String id) {
        LineUpdateSelectionProbe probe = new LineUpdateSelectionProbe();
        multipleCandidates(probe);
        String output = run(probe, "2\n13\n" + id + "\n0\n");
        assertTrue(output.contains("[INVALID_INPUT]"));
        assertTrue(probe.writes.events.isEmpty());
    }

    @Test
    void blankCandidateChoiceCancelsWithoutWriting() {
        LineUpdateSelectionProbe probe = new LineUpdateSelectionProbe();
        multipleCandidates(probe);
        String output = run(probe, "2\n13\n\n0\n");
        assertTrue(output.contains("已取消修改"));
        assertTrue(probe.writes.events.isEmpty());
    }

    @Test
    void noMatchingLineReturnsToMenuWithoutRequestingNewNames() {
        LineUpdateSelectionProbe probe = new LineUpdateSelectionProbe();
        probe.candidates = List.of();
        String output = run(probe, "2\n13\n0\n");
        assertTrue(output.contains("未找到编号为 13 的线路"));
        assertFalse(output.contains("请输入新中文名"));
        assertTrue(probe.writes.events.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "-1", "1%", "1_", "13.0", ""})
    void invalidLineNumberDoesNotSearchOrWrite(String number) {
        LineUpdateSelectionProbe probe = new LineUpdateSelectionProbe();
        String output = run(probe, "2\n" + number + "\n0\n");
        assertTrue(output.contains("线路编号必须是正整数"));
        assertTrue(probe.searches.isEmpty());
        assertTrue(probe.writes.events.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2\n", "2\n13\n", "2\n13\n中文名\n"})
    void incompleteInputExitsCleanlyWithoutUpdatingAnything(String input) {
        LineUpdateSelectionProbe probe = new LineUpdateSelectionProbe();
        assertDoesNotThrow(() -> run(probe, input));
        assertTrue(probe.writes.events.isEmpty());
    }

    @Test
    void eofDuringCandidateSelectionDoesNotWrite() {
        LineUpdateSelectionProbe probe = new LineUpdateSelectionProbe();
        multipleCandidates(probe);
        assertDoesNotThrow(() -> run(probe, "2\n13\n"));
        assertTrue(probe.writes.events.isEmpty());
    }

    @Test
    void duplicateNamePrintsSafeErrorAndReturnsToTheMenu() {
        LineUpdateSelectionProbe probe = new LineUpdateSelectionProbe();
        probe.writes.executionFailure = new SQLException("private SQL detail", "23505");
        String output = run(probe, "2\n13\n重复线\nDuplicate\n0\n");
        assertTrue(output.contains("[DUPLICATE]"));
        assertFalse(output.contains("修改成功"));
        assertFalse(output.contains("private SQL detail"));
        assertTrue(output.lastIndexOf("请选择操作") > output.indexOf("[DUPLICATE]"));
    }

    @Test
    void invalidBusinessInputCanBeCorrectedInTheSameRun() {
        LineUpdateSelectionProbe probe = new LineUpdateSelectionProbe();
        String output = run(probe, "2\n13\n \nInvalid\n2\n13\n测试线\nTest Line\n0\n");
        assertTrue(output.contains("[INVALID_INPUT]"));
        assertTrue(output.contains("修改成功"));
        assertEquals(1, probe.writes.events.stream().filter("open"::equals).count());
    }
}
