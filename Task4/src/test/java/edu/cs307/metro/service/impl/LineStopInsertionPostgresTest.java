package edu.cs307.metro.service.impl;

import edu.cs307.metro.bootstrap.AppBootstrap;
import edu.cs307.metro.bootstrap.AppServices;
import edu.cs307.metro.cli.CliApp;
import edu.cs307.metro.common.AppException;
import edu.cs307.metro.common.ErrorCode;
import edu.cs307.metro.model.StopDraft;
import edu.cs307.metro.model.StationKey;
import edu.cs307.metro.model.StationView;
import edu.cs307.metro.service.LineService;
import edu.cs307.metro.service.StationService;
import edu.cs307.metro.support.PostgresTestDatabase;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "METRO_TEST_DB_URL", matches = "jdbc:postgresql://.+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LineStopInsertionPostgresTest {
    private PostgresTestDatabase database;
    private LineService lines;

    @BeforeAll
    void createDatabase() throws Exception {
        database = PostgresTestDatabase.create();
        lines = AppBootstrap.create(database.connections()).lines();
    }

    @BeforeEach
    void seed() throws Exception {
        database.reset();
        try (Connection connection = database.connections().open();
             var statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO public.district (district_id, name_en) VALUES (1, 'Test');
                    INSERT INTO public.station (station_id, chinese_name, english_name, district_id)
                    SELECT n, '站' || n, 'Station ' || n, 1 FROM generate_series(101, 108) n;
                    INSERT INTO public.metro_line (line_id, chinese_name, english_name)
                    VALUES (42, '13号线', 'Line 13'), (71, '13号线北延', 'Line 13 Phase 2'),
                           (72, '空线', 'Line 99');
                    INSERT INTO public.line_station (line_id, station_id, position, status, opening_year)
                    VALUES (42, 101, 1, 'Operation', 2020), (42, 102, 2, 'Operation', 2020),
                           (42, 103, 3, 'Construction', 2027), (71, 102, 1, 'Operation', 2020);
                    """);
        }
    }

    @AfterAll
    void closeDatabase() throws Exception {
        if (database != null) database.close();
    }

    private StopDraft stop(long stationId) {
        return new StopDraft(stationId, "Operation", 2026, null, null, null, null);
    }

    private List<Long> stationIds(long lineId) throws Exception {
        List<Long> result = new ArrayList<>();
        try (Connection connection = database.connections().open();
             var statement = connection.prepareStatement(
                     "SELECT station_id, position FROM public.line_station WHERE line_id = ? ORDER BY position")) {
            statement.setLong(1, lineId);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(rows.getLong(1));
                    assertEquals(result.size(), rows.getInt(2));
                }
            }
        }
        return result;
    }

    private List<String> snapshot() throws Exception {
        List<String> result = new ArrayList<>();
        try (Connection connection = database.connections().open();
             var statement = connection.createStatement();
             var rows = statement.executeQuery(
                     "SELECT row_to_json(s)::text FROM public.line_station s ORDER BY line_id, position")) {
            while (rows.next()) result.add(rows.getString(1));
        }
        return result;
    }

    @Test
    void insertsThroughCliWithStationSearchSuppliedAsAServiceBoundary() throws Exception {
        StationService stations = (StationService) Proxy.newProxyInstance(
                StationService.class.getClassLoader(), new Class<?>[]{StationService.class},
                (proxy, method, args) -> {
                    assertEquals("search", method.getName());
                    long id = Long.parseLong((String) args[0]);
                    return List.of(new StationView(id, new StationKey("站" + id, "Test"),
                            "Station " + id, "UNASSIGNED"));
                });
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        String input = "3\n1\n13\n42\n2\n2\n104\nOperation\n2026\n\n\n\n\n"
                + "105\nConstruction\n2027\n\n\n\n\nYES\n0\n0\n";
        try (Scanner scanner = new Scanner(input);
             PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            new CliApp(new AppServices(stations, lines, null, null, null, null, null))
                    .run(scanner, output);
        }
        assertTrue(bytes.toString(StandardCharsets.UTF_8).contains("插入成功"));
        assertEquals(List.of(101L, 104L, 105L, 102L, 103L), stationIds(42));
        assertEquals(List.of(102L), stationIds(71));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4})
    void insertsBatchesAtTheFrontMiddleAndEndInRequestedOrder(int position) throws Exception {
        lines.insertStops(42, position, List.of(stop(104), stop(105)));
        List<Long> expected = new ArrayList<>(List.of(101L, 102L, 103L));
        expected.addAll(position - 1, List.of(104L, 105L));
        assertEquals(expected, stationIds(42));
        assertEquals(List.of(102L), stationIds(71));
    }

    @Test
    void insertsIntoAnEmptyLineAndPreservesNullableAndProvidedAttributes() throws Exception {
        StopDraft draft = new StopDraft(104, " Construction ", 2027,
                LocalTime.of(6, 30), LocalTime.of(23, 45),
                new BigDecimal("113.123456"), new BigDecimal("22.654321"));
        lines.insertStops(72, 1, List.of(draft, stop(105)));
        assertEquals(List.of(104L, 105L), stationIds(72));
        try (Connection connection = database.connections().open();
             var statement = connection.createStatement();
             var rows = statement.executeQuery(
                     "SELECT * FROM public.line_station WHERE line_id = 72 ORDER BY position")) {
            assertTrue(rows.next());
            assertEquals("Construction", rows.getString("status"));
            assertEquals(2027, rows.getInt("opening_year"));
            assertEquals(draft.startTime(), rows.getObject("start_time", LocalTime.class));
            assertEquals(draft.endTime(), rows.getObject("end_time", LocalTime.class));
            assertEquals(draft.sourceLatitude(), rows.getBigDecimal("source_latitude"));
            assertEquals(draft.sourceLongitude(), rows.getBigDecimal("source_longitude"));
            assertTrue(rows.next());
            assertNull(rows.getObject("start_time"));
            assertNull(rows.getObject("end_time"));
            assertNull(rows.getObject("source_latitude"));
            assertNull(rows.getObject("source_longitude"));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 5})
    void rejectsOutOfRangePositionsWithoutChangingAnyRelationship(int position) throws Exception {
        List<String> before = snapshot();
        AppException error = assertThrows(AppException.class,
                () -> lines.insertStops(42, position, List.of(stop(104))));
        assertEquals(ErrorCode.INVALID_INPUT, error.code());
        assertEquals(before, snapshot());
    }

    @Test
    void rejectsMissingLines() throws Exception {
        List<String> before = snapshot();
        AppException error = assertThrows(AppException.class,
                () -> lines.insertStops(999, 1, List.of(stop(104))));
        assertEquals(ErrorCode.NOT_FOUND, error.code());
        assertEquals(before, snapshot());
    }

    @Test
    void rejectsExistingAndRepeatedStationsAsWholeBatches() throws Exception {
        List<String> before = snapshot();
        AppException existing = assertThrows(AppException.class,
                () -> lines.insertStops(42, 2, List.of(stop(104), stop(102))));
        assertEquals(ErrorCode.DUPLICATE, existing.code());
        assertEquals(before, snapshot());
        AppException repeated = assertThrows(AppException.class,
                () -> lines.insertStops(42, 2, List.of(stop(104), stop(104))));
        assertEquals(ErrorCode.DUPLICATE, repeated.code());
        assertEquals(before, snapshot());
    }

    @Test
    void laterMissingStationRollsBackBothTheEarlierInsertAndPositionShift() throws Exception {
        List<String> before = snapshot();
        AppException error = assertThrows(AppException.class,
                () -> lines.insertStops(42, 2, List.of(stop(104), stop(999))));
        assertEquals(ErrorCode.NOT_FOUND, error.code());
        assertEquals(before, snapshot());
        lines.insertStops(42, 2, List.of(stop(104)));
        assertEquals(List.of(101L, 104L, 102L, 103L), stationIds(42));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void concurrentInsertsSerializeAndRejectAnAlreadyInsertedStation(boolean sameStation) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> insertAfterStart(start, 104));
            var second = executor.submit(() -> insertAfterStart(start, sameStation ? 104 : 105));
            start.countDown();
            List<String> outcomes = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            if (sameStation) {
                assertEquals(1, outcomes.stream().filter("OK"::equals).count());
                assertTrue(outcomes.contains("DUPLICATE"));
                assertEquals(List.of(101L, 104L, 102L, 103L), stationIds(42));
            } else {
                assertEquals(List.of("OK", "OK"), outcomes);
                List<Long> ids = stationIds(42);
                assertEquals(5, ids.size());
                assertEquals(101L, ids.get(0));
                assertTrue(ids.subList(1, 3).containsAll(List.of(104L, 105L)));
                assertEquals(List.of(102L, 103L), ids.subList(3, 5));
            }
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private String insertAfterStart(CountDownLatch start, long stationId) throws Exception {
        assertTrue(start.await(5, TimeUnit.SECONDS));
        try {
            lines.insertStops(42, 2, List.of(stop(stationId)));
            return "OK";
        } catch (AppException error) {
            return error.code().name();
        }
    }

    @Test
    void concurrentInsertionAndRemovalKeepTheLineContinuous() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var insert = executor.submit(() -> insertAfterStart(start, 104));
            var remove = executor.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                lines.removeStop(42, 102);
                return "OK";
            });
            start.countDown();
            assertEquals("OK", insert.get(10, TimeUnit.SECONDS));
            assertEquals("OK", remove.get(10, TimeUnit.SECONDS));
            assertEquals(List.of(101L, 104L, 103L), stationIds(42));
            assertEquals(List.of(102L), stationIds(71));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
