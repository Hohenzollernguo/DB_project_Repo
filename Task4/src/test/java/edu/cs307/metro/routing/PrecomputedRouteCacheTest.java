package edu.cs307.metro.routing;

import edu.cs307.metro.model.StationKey;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PrecomputedRouteCacheTest {
    @TempDir Path temporary;

    private Path source() throws Exception {
        Path source = temporary.resolve("stations.csv");
        Files.writeString(source, """
                Line Chineses,Line En,Station Name,Station Name EN,Num,District,Status,Opening,Start Time,End Time,latitude,longitude
                甲线,Line 1,甲,A,1,Test,Operation,2020,,,,
                甲线,Line 1,乙,B,2,Test,Operation,2020,,,,
                甲线,Line 1,丙,C,3,Test,Operation,2020,,,,
                """, StandardCharsets.UTF_8);
        return source;
    }

    @Test
    void loadsGeneratedJsonOnceAndSharesItsImmutableDataAcrossThreads() throws Exception {
        Path source = source();
        Path json = temporary.resolve("routes.json");
        RouteCacheGenerator.generate(source, json, new PrintStream(OutputStream.nullOutputStream()));
        var cache = new PrecomputedRouteCache(source, json);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var a = executor.submit(cache::data);
            var b = executor.submit(cache::data);
            var data = a.get();
            assertSame(data, b.get());
            assertEquals(6, data.size());
            var route = data.find(new StationKey("甲", "Test"), new StationKey("丙", "Test"));
            assertEquals(2, route.stopCount());
            assertEquals(0, route.transferCount());
            assertThrows(UnsupportedOperationException.class, () -> route.legs().clear());
            Files.delete(json);
            assertSame(data, cache.data());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsTruncatedDuplicateAndIncompleteCacheThenAllowsRetry() throws Exception {
        Path source = source();
        Path json = temporary.resolve("routes.json");
        RouteCacheGenerator.generate(source, json, new PrintStream(OutputStream.nullOutputStream()));
        String original = Files.readString(json);
        var cache = new PrecomputedRouteCache(source, json);
        Files.writeString(json, original.substring(0, original.length() / 2));
        assertThrows(IOException.class, cache::data);
        Files.writeString(json, original.replaceFirst("\"formatVersion\": 1", "\"formatVersion\":1,\"formatVersion\":1"));
        assertThrows(IOException.class, cache::data);
        String first = original.lines().filter(line -> line.startsWith("    {")).findFirst().orElseThrow();
        Files.writeString(json, original.replace(first + "\n", ""));
        assertThrows(IOException.class, cache::data);
        Files.writeString(json, original.replace("\"stopCount\":2", "\"stopCount\":99"));
        assertThrows(IOException.class, cache::data);
        Files.writeString(json, original);
        assertEquals(6, cache.data().size());
    }
}
