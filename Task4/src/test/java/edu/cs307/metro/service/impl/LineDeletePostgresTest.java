package edu.cs307.metro.service.impl;

import edu.cs307.metro.bootstrap.AppBootstrap;
import edu.cs307.metro.common.AppException;
import edu.cs307.metro.common.ErrorCode;
import edu.cs307.metro.service.LineService;
import edu.cs307.metro.support.PostgresTestDatabase;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

/** 在独立临时库验证删除语义、外键限制、数据保留和重复请求。 */
@EnabledIfEnvironmentVariable(named = "METRO_TEST_DB_URL", matches = "jdbc:postgresql://.+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LineDeletePostgresTest {
    private PostgresTestDatabase database;
    private LineService lines;

    @BeforeAll
    void createTestDatabase() throws Exception {
        database = PostgresTestDatabase.create();
        lines = AppBootstrap.create(database.connections()).lines();
    }

    @BeforeEach
    void seedLinkedAndEmptyLines() throws Exception {
        database.reset();
        try (Connection connection = database.connections().open(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO public.district (district_id, name_en) VALUES (1, 'TestDistrict');
                    INSERT INTO public.station (station_id, chinese_name, english_name, district_id)
                    VALUES (10, '测试站', 'Test Station', 1);
                    INSERT INTO public.metro_line (line_id, chinese_name, english_name)
                    VALUES (1, '关联线路', 'Linked Line'), (2, '空线路', 'Empty Line');
                    INSERT INTO public.line_station (line_id, station_id, position, status, opening_year)
                    VALUES (1, 10, 1, 'Operation', 2020);
                    INSERT INTO public.station_exit (exit_id, station_id, exit_code) VALUES (20, 10, 'A');
                    INSERT INTO public.landmark (landmark_id, name) VALUES (30, '测试地标');
                    INSERT INTO public.exit_landmark (exit_id, landmark_id) VALUES (20, 30);
                    """);
        }
    }

    @AfterAll
    void closeTestDatabase() throws Exception {
        if (database != null) database.close();
    }

    private long count(String table) throws Exception {
        try (Connection connection = database.connections().open(); Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT count(*) FROM public." + table)) {
            assertTrue(rows.next());
            return rows.getLong(1);
        }
    }

    private Map<String, Long> counts() throws Exception {
        Map<String, Long> result = new LinkedHashMap<>();
        for (String table : List.of("district", "station", "metro_line", "line_station",
                "station_exit", "landmark", "exit_landmark")) {
            result.put(table, count(table));
        }
        return result;
    }

    private boolean lineExists(long id) throws Exception {
        try (Connection connection = database.connections().open();
             var statement = connection.prepareStatement("SELECT 1 FROM public.metro_line WHERE line_id = ?")) {
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    @Test
    void deletingAnEmptyLinePreservesStationsExitsLandmarksAndOtherRelationships() throws Exception {
        Map<String, Long> expected = counts();
        expected.put("metro_line", 1L);
        lines.delete(2);
        assertFalse(lineExists(2));
        assertTrue(lineExists(1));
        assertEquals(expected, counts());
    }

    @Test
    void deletingALinkedLineReturnsInUseAndPreservesEveryTableAndAttribute() throws Exception {
        Map<String, Long> before = counts();
        AppException error = assertThrows(AppException.class, () -> lines.delete(1));
        assertEquals(ErrorCode.IN_USE, error.code());
        assertEquals(before, counts());
        assertTrue(lineExists(1));
        assertTrue(lineExists(2));
        try (Connection connection = database.connections().open(); Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT l.chinese_name, l.english_name, ls.station_id, ls.position,
                            ls.status, ls.opening_year, s.chinese_name, e.exit_code, m.name
                     FROM public.metro_line l
                     JOIN public.line_station ls ON ls.line_id = l.line_id
                     JOIN public.station s ON s.station_id = ls.station_id
                     JOIN public.station_exit e ON e.station_id = s.station_id
                     JOIN public.exit_landmark em ON em.exit_id = e.exit_id
                     JOIN public.landmark m ON m.landmark_id = em.landmark_id
                     WHERE l.line_id = 1
                     """)) {
            assertTrue(rows.next());
            assertEquals("关联线路", rows.getString(1));
            assertEquals("Linked Line", rows.getString(2));
            assertEquals(10, rows.getLong(3));
            assertEquals(1, rows.getInt(4));
            assertEquals("Operation", rows.getString(5));
            assertEquals(2020, rows.getInt(6));
            assertEquals("测试站", rows.getString(7));
            assertEquals("A", rows.getString(8));
            assertEquals("测试地标", rows.getString(9));
            assertFalse(rows.next());
        }
    }

    @Test
    void missingIdReturnsNotFoundAndPreservesData() throws Exception {
        Map<String, Long> before = counts();
        AppException error = assertThrows(AppException.class, () -> lines.delete(Long.MAX_VALUE));
        assertEquals(ErrorCode.NOT_FOUND, error.code());
        assertEquals(before, counts());
    }

    @Test
    void aRepeatedDeleteReturnsNotFound() throws Exception {
        lines.delete(2);
        AppException error = assertThrows(AppException.class, () -> lines.delete(2));
        assertEquals(ErrorCode.NOT_FOUND, error.code());
        assertFalse(lineExists(2));
        assertTrue(lineExists(1));
        assertEquals(1, count("line_station"));
    }

    @Test
    void invalidIdIsRejectedAndTheNextDeleteStillSucceeds() throws Exception {
        Map<String, Long> before = counts();
        AppException error = assertThrows(AppException.class, () -> lines.delete(0));
        assertEquals(ErrorCode.INVALID_INPUT, error.code());
        assertEquals(before, counts());
        lines.delete(2);
        assertFalse(lineExists(2));
    }

    @Test
    void explicitRelationshipRemovalAllowsDeletionWhileStationDataIsPreserved() throws Exception {
        try (Connection connection = database.connections().open(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM public.line_station WHERE line_id = 1");
        }
        lines.delete(1);
        assertFalse(lineExists(1));
        assertTrue(lineExists(2));
        assertEquals(0, count("line_station"));
        assertEquals(1, count("station"));
        assertEquals(1, count("station_exit"));
        assertEquals(1, count("landmark"));
        assertEquals(1, count("exit_landmark"));
    }

    @Test
    void concurrentDeletesHaveOneSuccessAndOneNotFound() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> deleteWhenReleased(start));
            var second = executor.submit(() -> deleteWhenReleased(start));
            start.countDown();
            List<Object> results = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter("DELETED"::equals).count());
            assertEquals(1, results.stream().filter(ErrorCode.NOT_FOUND::equals).count());
            assertFalse(lineExists(2));
            assertTrue(lineExists(1));
            assertEquals(1, count("line_station"));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private Object deleteWhenReleased(CountDownLatch start) throws Exception {
        assertTrue(start.await(5, TimeUnit.SECONDS));
        try {
            lines.delete(2);
            return "DELETED";
        } catch (AppException failure) {
            return failure.code();
        }
    }
}
