package edu.cs307.metro.service.impl;

import edu.cs307.metro.bootstrap.AppBootstrap;
import edu.cs307.metro.common.AppException;
import edu.cs307.metro.common.ErrorCode;
import edu.cs307.metro.db.ConnectionFactory;
import edu.cs307.metro.model.LineDraft;
import edu.cs307.metro.model.LineView;
import edu.cs307.metro.service.LineService;
import edu.cs307.metro.support.PostgresTestDatabase;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** 显式启用后创建独立临时数据库，复用仓库 DDL，结束时只删除自己创建的数据库。 */
@EnabledIfEnvironmentVariable(named = "METRO_TEST_DB_URL", matches = "jdbc:postgresql://.+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LineUpdatePostgresTest {
    private PostgresTestDatabase database;
    private ConnectionFactory connections;
    private LineService lines;

    @BeforeAll
    void createIsolatedDatabaseWithTheSharedDdl() throws Exception {
        database = PostgresTestDatabase.create();
        connections = database.connections();
        lines = AppBootstrap.create(connections).lines();
    }

    @BeforeEach
    void seedTwoLinesAndAStationRelationship() throws Exception {
        database.reset();
        try (Connection connection = connections.open(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO public.district (district_id, name_en) VALUES (1, 'TestDistrict');
                    INSERT INTO public.station (station_id, chinese_name, english_name, district_id)
                    VALUES (10, '测试站', 'Test Station', 1);
                    INSERT INTO public.metro_line (line_id, chinese_name, english_name)
                    VALUES (1, '一号线', 'Line 1'), (2, '二号线', 'Line 2');
                    INSERT INTO public.line_station (line_id, station_id, position, status, opening_year)
                    VALUES (1, 10, 1, 'Operation', 2020);
                    """);
        }
    }

    @AfterAll
    void removeOnlyTheDatabaseCreatedByThisTest() throws Exception {
        if (database != null) database.close();
    }

    private LineView read(long id) throws Exception {
        try (Connection connection = connections.open();
             var statement = connection.prepareStatement(
                     "SELECT line_id, chinese_name, english_name FROM public.metro_line WHERE line_id = ?")) {
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return new LineView(rows.getLong(1), rows.getString(2), rows.getString(3));
            }
        }
    }

    @Test
    void updateCommitsBothNamesAndKeepsTheIdentityAndStationRelationship() throws Exception {
        LineView updated = lines.update(1, new LineDraft("  新 一号线  ", "  New Line 1  "));
        assertEquals(new LineView(1, "新 一号线", "New Line 1"), updated);
        assertEquals(updated, read(1));
        assertEquals(new LineView(2, "二号线", "Line 2"), read(2));
        try (Connection connection = connections.open(); Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT line_id, station_id, position, status, opening_year
                     FROM public.line_station
                     """)) {
            assertTrue(rows.next());
            assertEquals(1, rows.getLong(1));
            assertEquals(10, rows.getLong(2));
            assertEquals(1, rows.getInt(3));
            assertEquals("Operation", rows.getString(4));
            assertEquals(2020, rows.getInt(5));
            assertFalse(rows.next());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void eitherDuplicateNameRollsBackBothColumnsAndTheNextRequestCanSucceed(boolean chineseConflict) throws Exception {
        LineDraft input = chineseConflict ? new LineDraft("二号线", "Unused English")
                : new LineDraft("未使用中文名", "Line 2");
        AppException error = assertThrows(AppException.class, () -> lines.update(1, input));
        assertEquals(ErrorCode.DUPLICATE, error.code());
        assertEquals(new LineView(1, "一号线", "Line 1"), read(1));
        assertEquals(new LineView(2, "二号线", "Line 2"), read(2));
        assertEquals(new LineView(1, "可用新名称", "Available Name"),
                lines.update(1, new LineDraft("可用新名称", "Available Name")));
    }

    @Test
    void missingIdIsNotFound() throws Exception {
        AppException error = assertThrows(AppException.class,
                () -> lines.update(Long.MAX_VALUE, new LineDraft("不存在", "Missing")));
        assertEquals(ErrorCode.NOT_FOUND, error.code());
        assertEquals(new LineView(1, "一号线", "Line 1"), read(1));
    }

    @Test
    void savingTheSameNamesAgainIsSuccessful() throws Exception {
        LineView result = lines.update(1, new LineDraft("一号线", "Line 1"));
        assertEquals(new LineView(1, "一号线", "Line 1"), result);
        assertEquals(result, read(1));
    }

    @Test
    void quotesAndSqlLookingTextRemainLiteralNames() throws Exception {
        String english = "O'Brien'; DROP TABLE public.metro_line; -- / Line";
        LineView result = lines.update(1, new LineDraft("线路'名称", english));
        assertEquals(new LineView(1, "线路'名称", english), result);
        assertEquals(result, read(1));
        assertEquals(new LineView(2, "二号线", "Line 2"), read(2));
    }

    @Test
    void concurrentRenamesCannotCommitTheSameEnglishName() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> concurrentUpdate(1, start));
            var second = executor.submit(() -> concurrentUpdate(2, start));
            start.countDown();
            List<Object> results = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter(LineView.class::isInstance).count());
            assertEquals(1, results.stream().filter(ErrorCode.DUPLICATE::equals).count());
            for (long id : new long[]{1, 2}) {
                LineView persisted = read(id);
                if ("Shared Name".equals(persisted.englishName())) {
                    assertEquals("并发新名称" + id, persisted.chineseName());
                } else {
                    assertEquals(id == 1 ? new LineView(1, "一号线", "Line 1")
                            : new LineView(2, "二号线", "Line 2"), persisted);
                }
            }
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private Object concurrentUpdate(long id, CountDownLatch start) throws Exception {
        assertTrue(start.await(5, TimeUnit.SECONDS));
        try {
            return lines.update(id, new LineDraft("并发新名称" + id, "Shared Name"));
        } catch (AppException failure) {
            return failure.code();
        }
    }
}
