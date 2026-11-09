package edu.cs307.metro.cli;

import edu.cs307.metro.bootstrap.AppServices;
import edu.cs307.metro.common.AppException;
import edu.cs307.metro.common.ErrorCode;
import edu.cs307.metro.model.*;
import edu.cs307.metro.service.LineService;
import edu.cs307.metro.service.StationService;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class CliInsertStopsTest {
    private static final String[] ANSWERS = {
            "3", "1", "13", "2", "1", "测试站", "Operation", "2026", "", "", "", "", "YES", "0", "0"
    };

    private static class Probe {
        List<LineView> lines = List.of(new LineView(42, "13号线", "Line 13"));
        List<StationView> stations = List.of(station(91, "Nanshan"));
        Map<String, List<StationView>> stationMatches = Map.of();
        List<String> stationKeywords = new ArrayList<>();
        List<StopDraft> inserted;
        long insertedLine;
        int insertedPosition;
        RuntimeException stationFailure;
        RuntimeException insertFailure;

        @SuppressWarnings("unchecked")
        AppServices services() {
            LineService lineService = (LineService) Proxy.newProxyInstance(
                    LineService.class.getClassLoader(), new Class<?>[]{LineService.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "search": return lines;
                            case "insertStops":
                                insertedLine = (long) args[0];
                                insertedPosition = (int) args[1];
                                inserted = List.copyOf((List<StopDraft>) args[2]);
                                if (insertFailure != null) throw insertFailure;
                                return null;
                            default: throw new AssertionError("Unexpected line call " + method.getName());
                        }
                    });
            StationService stationService = (StationService) Proxy.newProxyInstance(
                    StationService.class.getClassLoader(), new Class<?>[]{StationService.class},
                    (proxy, method, args) -> {
                        if (!method.getName().equals("search")) {
                            throw new AssertionError("Unexpected station call " + method.getName());
                        }
                        stationKeywords.add((String) args[0]);
                        if (stationFailure != null) throw stationFailure;
                        return stationMatches.getOrDefault((String) args[0], stations);
                    });
            return new AppServices(stationService, lineService, null, null, null, null, null);
        }
    }

    private static StationView station(long id, String district) {
        return new StationView(id, new StationKey("测试站", district), "Test", "UNASSIGNED");
    }

    private static String run(Probe probe, String input) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (Scanner scanner = new Scanner(input);
             PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            new CliApp(probe.services()).run(scanner, output);
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void selectsExistingStationAndPassesNullableAttributesToTheInsertionService() {
        Probe probe = new Probe();
        String output = run(probe, String.join("\n", ANSWERS) + "\n");
        assertEquals(42, probe.insertedLine);
        assertEquals(2, probe.insertedPosition);
        assertEquals(List.of("测试站"), probe.stationKeywords);
        assertEquals(List.of(new StopDraft(91, "Operation", 2026, null, null, null, null)), probe.inserted);
        assertTrue(output.contains("Nanshan"));
        assertTrue(output.contains("插入成功"));
    }

    @Test
    void collectsMultipleStationsInOrderWithProvidedAttributes() {
        Probe probe = new Probe();
        probe.stationMatches = Map.of("乙", List.of(station(92, "Luohu")));
        String input = "3\n1\n13\n1\n2\n甲\nOperation\n2026\n06:30\n23:45\n113.123456\n22.654321\n"
                + "乙\nConstruction\n2027\n\n\n\n\nYES\n0\n0\n";
        run(probe, input);
        assertEquals(List.of("甲", "乙"), probe.stationKeywords);
        assertEquals(2, probe.inserted.size());
        assertEquals(List.of(91L, 92L), probe.inserted.stream().map(StopDraft::stationId).toList());
        StopDraft first = probe.inserted.get(0);
        assertEquals(LocalTime.of(6, 30), first.startTime());
        assertEquals(LocalTime.of(23, 45), first.endTime());
        assertEquals(new BigDecimal("113.123456"), first.sourceLatitude());
        assertEquals(new BigDecimal("22.654321"), first.sourceLongitude());
        assertEquals("Construction", probe.inserted.get(1).status());
    }

    @Test
    void resolvesMultipleLineAndStationCandidatesByInternalIds() {
        Probe probe = new Probe();
        probe.lines = List.of(new LineView(42, "13号线", "Line 13"),
                new LineView(71, "北延", "Line 13 Phase 2"));
        probe.stations = List.of(station(91, "Nanshan"), station(92, "Luohu"));
        run(probe, "3\n1\n13\n71\n1\n1\n测试站\n92\nOperation\n2026\n\n\n\n\nYES\n0\n0\n");
        assertEquals(71, probe.insertedLine);
        assertEquals(92, probe.inserted.get(0).stationId());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12})
    void eofAtEveryInputStageExitsWithoutInserting(int suppliedAnswers) {
        Probe probe = new Probe();
        String input = String.join("\n", Arrays.copyOf(ANSWERS, suppliedAnswers));
        if (suppliedAnswers > 0) input += "\n";
        String output = run(probe, input);
        assertNull(probe.inserted);
        assertFalse(output.contains("插入成功"));
    }

    static Stream<Arguments> invalidAnswers() {
        return Stream.of(Arguments.of(3, "0"), Arguments.of(3, "-1"), Arguments.of(3, "abc"),
                Arguments.of(3, "2147483648"), Arguments.of(4, "0"), Arguments.of(4, "-2"),
                Arguments.of(4, "1.5"), Arguments.of(6, "Closed"), Arguments.of(7, "32768"),
                Arguments.of(7, "0"), Arguments.of(8, "25:00"), Arguments.of(9, "bad"),
                Arguments.of(10, "NaN"), Arguments.of(11, "Infinity"));
    }

    @ParameterizedTest
    @MethodSource("invalidAnswers")
    void malformedInputReportsInvalidInputBeforeCallingInsertion(int index, String invalid) {
        Probe probe = new Probe();
        String[] answers = Arrays.copyOf(ANSWERS, index + 1);
        answers[index] = invalid;
        String output = run(probe, String.join("\n", answers) + "\n0\n");
        assertTrue(output.contains("[INVALID_INPUT]"));
        assertNull(probe.inserted);
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 4, 5, 12})
    void blankInputAtCancellationPromptsNeverInserts(int index) {
        Probe probe = new Probe();
        String[] answers = Arrays.copyOf(ANSWERS, index + 1);
        answers[index] = "";
        String output = run(probe, String.join("\n", answers) + "\n0\n0\n");
        assertTrue(output.contains("已取消插入"));
        assertNull(probe.inserted);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "999", "9223372036854775808"})
    void invalidStationSelectionDoesNotReachInsertion(String selectedId) {
        Probe probe = new Probe();
        probe.stations = List.of(station(91, "Nanshan"), station(92, "Luohu"));
        String output = run(probe, "3\n1\n13\n1\n1\n测试站\n" + selectedId + "\n0\n");
        assertTrue(output.contains("[INVALID_INPUT]"));
        assertNull(probe.inserted);
    }

    @Test
    void noMatchingStationCancelsTheBatch() {
        Probe probe = new Probe();
        probe.stations = List.of();
        String output = run(probe, "3\n1\n13\n1\n1\n无匹配\n0\n0\n");
        assertTrue(output.contains("未找到匹配车站"));
        assertNull(probe.inserted);
    }

    @Test
    void pendingStationSearchAndFailedInsertionGiveFeedbackWithoutSuccess() {
        Probe pending = new Probe();
        pending.stationFailure = new UnsupportedOperationException("search");
        String pendingOutput = run(pending, "3\n1\n13\n1\n1\n测试站\n0\n");
        assertTrue(pendingOutput.contains("此功能尚未实现"));
        assertNull(pending.inserted);

        Probe failing = new Probe();
        failing.insertFailure = new AppException(ErrorCode.DUPLICATE, "车站已属于该线路。");
        String output = run(failing, String.join("\n", ANSWERS) + "\n");
        assertTrue(output.contains("[DUPLICATE]"));
        assertFalse(output.contains("插入成功"));
    }
}
