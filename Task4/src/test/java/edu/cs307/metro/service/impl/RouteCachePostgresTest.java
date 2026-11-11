package edu.cs307.metro.service.impl;

import edu.cs307.metro.bootstrap.AppBootstrap;
import edu.cs307.metro.bootstrap.AppServices;
import edu.cs307.metro.cli.CliApp;
import edu.cs307.metro.common.AppException;
import edu.cs307.metro.common.ErrorCode;
import edu.cs307.metro.db.Transactions;
import edu.cs307.metro.model.StationKey;
import edu.cs307.metro.routing.PrecomputedRouteCache;
import edu.cs307.metro.routing.RouteCacheGenerator;
import edu.cs307.metro.service.RouteService;
import edu.cs307.metro.support.PostgresTestDatabase;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Scanner;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "METRO_TEST_DB_URL", matches = "jdbc:postgresql://.+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RouteCachePostgresTest {
    @TempDir static Path temporary;
    private PostgresTestDatabase database;
    private RouteService routes;

    @BeforeAll
    void open() throws Exception {
        database = PostgresTestDatabase.create();
        Path source = temporary.resolve("stations.csv");
        Files.writeString(source, """
                Line Chineses,Line En,Station Name,Station Name EN,Num,District,Status,Opening,Start Time,End Time,latitude,longitude
                甲线,Line 1,甲,A,1,Test,Operation,2020,,,,
                甲线,Line 1,乙,B,2,Test,Operation,2020,,,,
                甲线,Line 1,丙,C,3,Test,Operation,2020,,,,
                """, StandardCharsets.UTF_8);
        Path json = temporary.resolve("routes.json");
        RouteCacheGenerator.generate(source, json, new PrintStream(OutputStream.nullOutputStream()));
        routes = new JdbcRouteService(new Transactions(database.connections()), new PrecomputedRouteCache(source, json));
    }

    @BeforeEach
    void seed() throws Exception {
        database.reset();
        execute("""
                INSERT INTO public.district (district_id,name_en) VALUES (91,'Test');
                INSERT INTO public.station (station_id,chinese_name,english_name,district_id)
                VALUES (101,'甲','A',91),(202,'乙','B',91),(303,'丙','C',91),(404,'孤站','Isolated',91);
                INSERT INTO public.metro_line (line_id,chinese_name,english_name) VALUES (808,'甲线','Line 1');
                INSERT INTO public.line_station (line_id,station_id,position,status,opening_year)
                VALUES (808,101,1,'Operation',2020),(808,202,2,'Operation',2020),(808,303,3,'Operation',2020);
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
    void cachedNamesMapToCurrentDatabaseIdsAndMultiCandidateRoutesWork() {
        var route = routes.findRoute(101,303);
        assertEquals(List.of(101L,202L,303L), route.stations().stream().map(value -> value.stationId()).toList());
        assertEquals(808, route.legs().get(0).line().lineId());
        assertEquals(2, route.stopCount());
        assertEquals(1, routes.findBestRoute(List.of(404L,101L,202L), List.of(303L)).stopCount());
        assertEquals(303, routes.findBestRoute(List.of(303L,202L), List.of(303L,202L)).stations().get(0).stationId());
    }

    @Test
    void invalidMissingIsolatedAndSameStationResultsRemainDistinct() {
        assertEquals(ErrorCode.INVALID_INPUT, assertThrows(AppException.class, () -> routes.findRoute(0,101)).code());
        assertEquals(ErrorCode.INVALID_INPUT, assertThrows(AppException.class,
                () -> routes.findBestRoute(List.of(), List.of(101L))).code());
        assertEquals(ErrorCode.NOT_FOUND, assertThrows(AppException.class, () -> routes.findRoute(101,999)).code());
        assertEquals(ErrorCode.NO_ROUTE, assertThrows(AppException.class, () -> routes.findRoute(101,404)).code());
        assertEquals(0, routes.findRoute(404,404).stopCount());
    }

    @Test
    void changedStatusAndNewShortcutKeepUsingTheFixedCache() throws Exception {
        assertEquals(2, routes.findRoute(101,303).stopCount());
        execute("UPDATE public.line_station SET status='Construction' WHERE station_id=202");
        assertEquals(2, routes.findRoute(101,303).stopCount());
        execute("""
                INSERT INTO public.metro_line (line_id,chinese_name,english_name) VALUES (909,'捷径线','Line 2');
                INSERT INTO public.line_station (line_id,station_id,position,status,opening_year)
                VALUES (909,101,1,'Operation',2020),(909,303,2,'Operation',2020);
                """);
        var changed = routes.findRoute(101,303);
        assertEquals(2, changed.stopCount());
        assertEquals(808, changed.legs().get(0).line().lineId());
        assertEquals(List.of("甲", "乙", "丙"), changed.stations().stream()
                .map(station -> station.key().chineseName()).toList());
    }

    @Test
    void deletedIntermediateStationAndLineStillRenderTheCachedRouteOnFirstQuery() throws Exception {
        execute("""
                DELETE FROM public.line_station;
                DELETE FROM public.station WHERE station_id=202;
                DELETE FROM public.metro_line;
                """);
        var result = routes.findRoute(101,303);
        assertEquals(2, result.stopCount());
        assertEquals(List.of("甲", "乙", "丙"), result.stations().stream()
                .map(station -> station.key().chineseName()).toList());
        assertEquals(0, result.stations().get(1).stationId());
        assertEquals("B", result.stations().get(1).englishName());
        assertEquals("甲线", result.legs().get(0).line().chineseName());
        assertEquals("Line 1", result.legs().get(0).line().englishName());
        assertEquals(0, result.legs().get(0).line().lineId());
    }

    @Test
    void cliQueriesCacheAndPrintsRouteThenReturnsToMenu() {
        var services = AppBootstrap.create(database.connections());
        var combined = new AppServices(services.stations(), services.lines(), services.neighbors(), routes,
                services.landmarks(), services.fares(), services.database());
        var bytes = new ByteArrayOutputStream();
        new CliApp(combined).run(new Scanner("5\n甲\n101\n丙\n303\n0\n"),
                new PrintStream(bytes, true, StandardCharsets.UTF_8));
        String output = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("甲 → 乙 → 丙"));
        assertTrue(output.contains("共 2 个站间段，换乘 0 次"));
        assertTrue(output.lastIndexOf("请选择操作") > output.indexOf("共 2 个站间段"));
    }

    @Test
    void officialImportUsesCompleteCacheAndSupportsReimportInSameProcess() throws Exception {
        String sql = Files.readString(Path.of("../Task3/src/import.sql"));
        execute(sql);
        var cache = new PrecomputedRouteCache(Path.of("../官方描述及数据/shenzhen_stations.csv"),
                Path.of("cache/routes-cache.json"));
        long start = System.nanoTime();
        var data = cache.data();
        System.out.printf("Official Java cache loaded: routes=%d, seconds=%.3f%n", data.size(),
                (System.nanoTime()-start)/1_000_000_000.0);
        assertEquals(114692, data.size());
        var service = new JdbcRouteService(new Transactions(database.connections()), cache);
        for (int attempt = 0; attempt < 2; attempt++) {
            var stations = AppBootstrap.create(database.connections()).stations();
            long from = stations.search("罗湖").stream().filter(value -> value.key().equals(new StationKey("罗湖","Luohu")))
                    .findFirst().orElseThrow().stationId();
            long to = stations.search("机场").stream().filter(value -> value.key().equals(new StationKey("机场","Bao'an")))
                    .findFirst().orElseThrow().stationId();
            var result = service.findRoute(from,to);
            assertEquals(15,result.stopCount());
            assertEquals(2,result.transferCount());
            if (attempt == 0) execute(sql);
        }
    }
}
