package edu.cs307.metro.service.impl;

import edu.cs307.metro.bootstrap.AppBootstrap;
import edu.cs307.metro.cli.CliApp;
import edu.cs307.metro.common.AppException;
import edu.cs307.metro.common.ErrorCode;
import edu.cs307.metro.model.LineStopView;
import edu.cs307.metro.model.StationKey;
import edu.cs307.metro.model.StopDraft;
import edu.cs307.metro.service.LineService;
import edu.cs307.metro.support.PostgresTestDatabase;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.LocalTime;
import java.util.List;
import java.util.Scanner;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "METRO_TEST_DB_URL", matches = "jdbc:postgresql://.+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LineStopsPostgresTest {
    private PostgresTestDatabase database;
    private LineService lines;

    @BeforeAll
    void open() throws Exception {
        database = PostgresTestDatabase.create();
        lines = AppBootstrap.create(database.connections()).lines();
    }

    @BeforeEach
    void seed() throws Exception {
        database.reset();
        try (Connection connection = database.connections().open(); var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO public.district (district_id, name_en) VALUES (1, 'Test');
                    INSERT INTO public.station (station_id, chinese_name, english_name, district_id)
                    VALUES (101, '甲', 'A', 1), (102, '乙', 'B', 1), (103, '丙', 'C', 1), (104, '丁', 'D', 1);
                    INSERT INTO public.metro_line (line_id, chinese_name, english_name)
                    VALUES (42, '13号线', 'Line 13'), (71, '13号线北延', 'Line 13 Phase 2'),
                           (72, '空线', 'Line 99');
                    INSERT INTO public.line_station (line_id, station_id, position, status, opening_year)
                    VALUES (42, 103, 3, 'Construction', 2027), (42, 101, 1, 'Operation', 2020),
                           (42, 102, 2, 'Construction', 2028), (71, 102, 1, 'Operation', 2020);
                    UPDATE public.line_station SET start_time = '06:30', end_time = '23:45',
                        source_latitude = 113.123456, source_longitude = 22.654321
                    WHERE line_id = 42 AND station_id = 101;
                    """);
        }
    }

    @AfterAll
    void close() throws Exception {
        if (database != null) database.close();
    }

    @Test
    void readsInPositionOrderWithAllAttributesAndStationWideState() {
        List<LineStopView> stops = lines.getStops(42);
        assertEquals(List.of(101L, 102L, 103L), stops.stream().map(s -> s.station().stationId()).toList());
        assertEquals(List.of(1, 2, 3), stops.stream().map(LineStopView::position).toList());
        assertTrue(stops.stream().allMatch(s -> s.lineId() == 42));
        assertEquals(new StationKey("甲", "Test"), stops.get(0).station().key());
        assertEquals("A", stops.get(0).station().englishName());
        assertEquals(new StopDraft(101, "Operation", 2020, LocalTime.of(6, 30), LocalTime.of(23, 45),
                new BigDecimal("113.123456"), new BigDecimal("22.654321")), stops.get(0).attributes());
        assertEquals(new StopDraft(102, "Construction", 2028, null, null, null, null), stops.get(1).attributes());
        assertEquals("Operation", stops.get(1).station().state()); // 在另一条线上运营。
        assertEquals("Construction", stops.get(2).station().state());
    }

    @Test
    void distinguishesEmptyLinesFromMissingLines() {
        assertEquals(List.of(), lines.getStops(72));
        assertEquals(ErrorCode.NOT_FOUND, assertThrows(AppException.class, () -> lines.getStops(999)).code());
    }

    @ParameterizedTest
    @ValueSource(longs = {101, 102, 103})
    void cliRemovesTheFirstMiddleAndLastStopWithoutDeletingStations(long removedId) throws Exception {
        String text = runCli("3\n2\n13\n42\n" + removedId + "\nYES\n0\n0\n");
        assertTrue(text.contains("移除成功"), text);
        assertFalse(text.contains("尚未实现"), text);
        List<Long> expected = List.of(101L, 102L, 103L).stream().filter(id -> id != removedId).toList();
        assertEquals(expected, lines.getStops(42).stream().map(s -> s.station().stationId()).toList());
        assertEquals(List.of(1, 2), lines.getStops(42).stream().map(LineStopView::position).toList());
        assertEquals(102, lines.getStops(71).get(0).station().stationId());
        try (Connection connection = database.connections().open(); var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT COUNT(*) FROM public.station")) {
            assertTrue(rows.next());
            assertEquals(4, rows.getLong(1));
        }
    }

    @Test
    void cliCancellationAndAnIdOutsideTheLineDoNotRemoveAnything() {
        List<LineStopView> before = lines.getStops(42);
        assertTrue(runCli("3\n2\n13\n42\n102\nNO\n0\n0\n").contains("已取消移除"));
        assertEquals(before, lines.getStops(42));
        assertTrue(runCli("3\n2\n13\n42\n104\n0\n").contains("[INVALID_INPUT]"));
        assertEquals(before, lines.getStops(42));
    }

    @Test
    void cliHandlesEmptyLinesAndRemovalOfTheOnlyStop() {
        assertTrue(runCli("3\n2\n99\n0\n0\n").contains("该线路暂无站点"));
        assertTrue(runCli("3\n2\n13\n71\n102\nYES\n0\n0\n").contains("移除成功"));
        assertEquals(List.of(), lines.getStops(71));
        assertEquals(3, lines.getStops(42).size());
        assertEquals("Construction", lines.getStops(42).get(1).station().state());
    }

    private String runCli(String script) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (Scanner input = new Scanner(script);
             PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            new CliApp(AppBootstrap.create(database.connections())).run(input, output);
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }
}
