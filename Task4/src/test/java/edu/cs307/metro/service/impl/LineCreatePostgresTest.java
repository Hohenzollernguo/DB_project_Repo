package edu.cs307.metro.service.impl;

import edu.cs307.metro.bootstrap.AppBootstrap;
import edu.cs307.metro.common.AppException;
import edu.cs307.metro.common.ErrorCode;
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

@EnabledIfEnvironmentVariable(named = "METRO_TEST_DB_URL", matches = "jdbc:postgresql://.+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LineCreatePostgresTest {
    private PostgresTestDatabase database;
    private LineService lines;

    @BeforeAll
    void createIsolatedDatabase() throws Exception {
        database = PostgresTestDatabase.create();
        lines = AppBootstrap.create(database.connections()).lines();
    }

    @BeforeEach
    void emptyAllTables() throws Exception {
        database.reset();
    }

    @AfterAll
    void removeIsolatedDatabase() throws Exception {
        if (database != null) database.close();
    }

    private LineView read(long id) throws Exception {
        try (Connection connection = database.connections().open();
             var statement = connection.prepareStatement(
                     "SELECT line_id, chinese_name, english_name FROM public.metro_line WHERE line_id = ?")) {
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return new LineView(rows.getLong(1), rows.getString(2), rows.getString(3));
            }
        }
    }

    private long lineCount() throws Exception {
        try (Connection connection = database.connections().open();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT count(*) FROM public.metro_line")) {
            assertTrue(rows.next());
            return rows.getLong(1);
        }
    }

    @Test
    void createsEmptyLinesWithDatabaseAssignedIdsAndNormalizedNames() throws Exception {
        LineView first = lines.create(new LineDraft("\u3000 新 一号线 \u3000", "  New Line 1  "));
        LineView second = lines.create(new LineDraft("新二号线", "New Line 2"));
        assertTrue(first.lineId() > 0);
        assertTrue(second.lineId() > 0);
        assertNotEquals(first.lineId(), second.lineId());
        assertEquals(new LineView(first.lineId(), "新 一号线", "New Line 1"), first);
        assertEquals(first, read(first.lineId()));
        assertEquals(second, read(second.lineId()));
        assertEquals(2, lineCount());
        try (Connection connection = database.connections().open();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT count(*) FROM public.line_station")) {
            assertTrue(rows.next());
            assertEquals(0, rows.getLong(1));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void eitherDuplicateNameRejectsTheEntireInsertAndAllowsANewRequest(boolean chineseConflict) throws Exception {
        LineView original = lines.create(new LineDraft("一号线", "Line 1"));
        LineDraft duplicate = chineseConflict ? new LineDraft(" 一号线 ", "Unused English")
                : new LineDraft("未使用中文名", " Line 1 ");
        AppException error = assertThrows(AppException.class, () -> lines.create(duplicate));
        assertEquals(ErrorCode.DUPLICATE, error.code());
        assertEquals(1, lineCount());
        assertEquals(original, read(original.lineId()));
        LineView retry = lines.create(new LineDraft("二号线", "Line 2"));
        assertEquals(retry, read(retry.lineId()));
        assertEquals(2, lineCount());
    }

    @Test
    void invalidNamesLeaveTheDatabaseEmpty() throws Exception {
        AppException error = assertThrows(AppException.class,
                () -> lines.create(new LineDraft(" \t\u3000", "Test Line")));
        assertEquals(ErrorCode.INVALID_INPUT, error.code());
        assertEquals(0, lineCount());
    }

    @Test
    void quotesAndSqlLookingTextAreStoredAsLiteralNames() throws Exception {
        String english = "O'Brien'; DROP TABLE public.metro_line; -- / New Line";
        LineView result = lines.create(new LineDraft("新'线路", english));
        assertEquals(new LineView(result.lineId(), "新'线路", english), result);
        assertEquals(result, read(result.lineId()));
        assertEquals(1, lineCount());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void concurrentCreatesCannotCommitEitherDuplicateName(boolean chineseConflict) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> concurrentCreate(1, chineseConflict, start));
            var second = executor.submit(() -> concurrentCreate(2, chineseConflict, start));
            start.countDown();
            List<Object> results = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter(LineView.class::isInstance).count());
            assertEquals(1, results.stream().filter(ErrorCode.DUPLICATE::equals).count());
            assertEquals(1, lineCount());
            for (Object result : results) {
                if (result instanceof LineView created) {
                    assertEquals(created, read(created.lineId()));
                }
            }
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private Object concurrentCreate(int number, boolean chineseConflict, CountDownLatch start) throws Exception {
        assertTrue(start.await(5, TimeUnit.SECONDS));
        LineDraft input = chineseConflict ? new LineDraft("同一条线路", "Concurrent Line " + number)
                : new LineDraft("并发线路" + number, "Shared English Name");
        try {
            return lines.create(input);
        } catch (AppException failure) {
            return failure.code();
        }
    }
}
