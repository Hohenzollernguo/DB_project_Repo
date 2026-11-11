package edu.cs307.metro.service.impl;

import edu.cs307.metro.bootstrap.AppBootstrap;
import edu.cs307.metro.cli.CliApp;
import edu.cs307.metro.common.AppException;
import edu.cs307.metro.common.ErrorCode;
import edu.cs307.metro.model.NeighborPair;
import edu.cs307.metro.model.StopDraft;
import edu.cs307.metro.service.NeighborService;
import edu.cs307.metro.support.PostgresTestDatabase;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Scanner;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "METRO_TEST_DB_URL", matches = "jdbc:postgresql://.+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NeighborQueryPostgresTest {
    private PostgresTestDatabase database;
    private NeighborService neighbors;

    @BeforeAll
    void open() throws Exception {
        database = PostgresTestDatabase.create();
        neighbors = AppBootstrap.create(database.connections()).neighbors();
    }

    @BeforeEach
    void seed() throws Exception {
        database.reset();
        // 只加载共享 Q7 定义，避免执行 Q4 的 DataGrip 参数及其他题目。
        String queries = Files.readString(Path.of("../Task3/src/queries.sql"));
        int start = queries.indexOf("CREATE OR REPLACE function");
        int end = queries.indexOf("$$;", start) + 3;
        execute(queries.substring(start, end));
        execute("""
                INSERT INTO public.district (district_id,name_en) VALUES (1,'Test'),(2,'Other');
                INSERT INTO public.station (station_id,chinese_name,english_name,district_id)
                VALUES (101,'甲','A',1),(102,'乙','B',1),(103,'丙','C',1),
                       (104,'丁','D',1),(105,'同名乙','B',2);
                INSERT INTO public.metro_line (line_id,chinese_name,english_name)
                VALUES (42,'测试线','Line 5'),(71,'另一线','Line 6');
                INSERT INTO public.line_station (line_id,station_id,position,status,opening_year)
                VALUES (42,101,1,'Operation',2020),(42,102,2,'Construction',2027),
                       (42,103,3,'Operation',2020),(71,105,1,'Operation',2020);
                """);
    }

    private void execute(String sql) throws Exception {
        try (var connection = database.connections().open(); var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    @AfterAll
    void close() throws Exception {
        if (database != null) database.close();
    }

    @Test
    void usesTheLineAndCompleteStationOrderAndNormalizesNames() {
        assertEquals(new NeighborPair("A", "C"), neighbors.get(" B ", " Line 5 ", 1));
        assertEquals(new NeighborPair(null, "B"), neighbors.get("A", "Line 5", 1));
        assertEquals(new NeighborPair("A", null), neighbors.get("C", "Line 5", 2));
        assertEquals(new NeighborPair(null, null), neighbors.get("B", "Line 6", 1));
    }

    @Test
    void zeroReturnsTheCurrentStationOnBothSidesAndLargeCountsPreserveBothNulls() {
        assertEquals(new NeighborPair("B", "B"), neighbors.get("B", "Line 5", 0));
        assertEquals(new NeighborPair(null, null), neighbors.get("B", "Line 5", 5));
        assertEquals(new NeighborPair(null, null), neighbors.get("B", "Line 5", Integer.MAX_VALUE));
    }

    @Test
    void missingLineStationAndSqlLookingNamesGiveNotFoundAndAllowTheNextQuery() {
        assertEquals(ErrorCode.NOT_FOUND, assertThrows(AppException.class,
                () -> neighbors.get("B", "Missing", 1)).code());
        assertEquals(ErrorCode.NOT_FOUND, assertThrows(AppException.class,
                () -> neighbors.get("Missing", "Line 5", 1)).code());
        assertEquals(ErrorCode.NOT_FOUND, assertThrows(AppException.class,
                () -> neighbors.get("B' OR '1'='1", "Line 5", 1)).code());
        assertEquals(new NeighborPair("A", "C"), neighbors.get("B", "Line 5", 1));
    }

    @Test
    void duplicateEnglishNamesWithinTheLineAreAmbiguous() throws Exception {
        execute("""
                INSERT INTO public.line_station (line_id,station_id,position,status,opening_year)
                VALUES (42,105,4,'Operation',2020)
                """);
        assertEquals(ErrorCode.AMBIGUOUS_NAME, assertThrows(AppException.class,
                () -> neighbors.get("B", "Line 5", 1)).code());
    }

    @Test
    void functionRejectsInvalidCountsWithTheSharedSqlState() throws Exception {
        for (Integer n : new Integer[]{null, -1}) {
            try (var connection = database.connections().open();
                 var statement = connection.prepareStatement(
                         "SELECT public.get_station_n_neighbor('B','Line 5',?)")) {
                statement.setObject(1, n, java.sql.Types.INTEGER);
                SQLException failure = assertThrows(SQLException.class, statement::executeQuery);
                assertEquals("22023", failure.getSQLState());
            }
        }
    }

    @Test
    void relationshipChangesAreVisibleOnTheNextQuery() {
        var lines = AppBootstrap.create(database.connections()).lines();
        lines.insertStops(42, 2, List.of(new StopDraft(104, "Operation", 2026, null, null, null, null)));
        assertEquals(new NeighborPair("D", "C"), neighbors.get("B", "Line 5", 1));
        lines.removeStop(42, 104);
        assertEquals(new NeighborPair("A", "C"), neighbors.get("B", "Line 5", 1));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3})
    void unexpectedFunctionRowCountsAreReportedAsDatabaseErrors(int count) throws Exception {
        execute("""
                CREATE OR REPLACE FUNCTION public.get_station_n_neighbor(
                    station_name text, line_name text, n integer)
                RETURNS SETOF text LANGUAGE sql AS 'SELECT NULL::text FROM generate_series(1,%d)'
                """.formatted(count));
        assertEquals(ErrorCode.DB_ERROR, assertThrows(AppException.class,
                () -> neighbors.get("B", "Line 5", 1)).code());
    }

    @Test
    void cliUsesTheRealFunctionAndRecoversFromMissingTargets() {
        var bytes = new ByteArrayOutputStream();
        try (var input = new Scanner("4\nMissing\nLine 5\n1\n4\nA\nLine 5\n1\n0\n");
             var output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            new CliApp(AppBootstrap.create(database.connections())).run(input, output);
        }
        String output = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("[NOT_FOUND]"));
        assertTrue(output.contains("向前第 1 站（站序减小）：无此站（超出线路范围）"));
        assertTrue(output.contains("向后第 1 站（站序增大）：B"));
    }

    @Test
    void officialExamplesWorkAfterImportingAndReimportingTheSharedData() throws Exception {
        String sql = Files.readString(Path.of("../Task3/src/import.sql"));
        for (int attempt = 0; attempt < 2; attempt++) {
            execute(sql);
            assertEquals(new NeighborPair("Liwan", "Qianwan"), neighbors.get("Mawan", "Line 5", 2));
            assertEquals(new NeighborPair(null, "Linhai"), neighbors.get("Mawan", "Line 5", 5));
        }
    }
}
