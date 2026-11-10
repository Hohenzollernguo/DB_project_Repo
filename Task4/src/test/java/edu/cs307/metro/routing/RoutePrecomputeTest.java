package edu.cs307.metro.routing;

import edu.cs307.metro.Main;
import edu.cs307.metro.model.StationKey;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RoutePrecomputeTest {
    @TempDir Path temporary;
    private static final PrintStream SINK = new PrintStream(OutputStream.nullOutputStream());
    private static final String HEADER = "Line Chineses,Line En,Station Name,Station Name EN,"
            + "Num,District,Status,Opening,Start Time,End Time,latitude,longitude\n";

    private static StationKey station(String name) { return new StationKey(name, "District"); }

    private static List<RouteNetwork.Stop> line(String line, String... names) {
        var stops = new ArrayList<RouteNetwork.Stop>();
        for (int i = 0; i < names.length; i++) {
            stops.add(new RouteNetwork.Stop(station(names[i]), line, i + 1, true));
        }
        return stops;
    }

    private static RouteNetwork network(List<RouteNetwork.Stop> first, List<RouteNetwork.Stop> second) {
        var stops = new ArrayList<>(first);
        stops.addAll(second);
        return new RouteNetwork(stops);
    }

    @Test
    void fewerStopsTakePriorityOverFewerTransfers() {
        var stops = new ArrayList<>(line("L1", "A", "B", "C", "D"));
        stops.addAll(line("L2", "A", "X"));
        stops.addAll(line("L3", "X", "D"));
        var route = new RoutePlanner(new RouteNetwork(stops)).from(station("A")).get(station("D"));
        assertEquals(2, route.stopCount());
        assertEquals(1, route.transferCount());
        assertEquals(List.of("L2", "L3"), route.legs().stream().map(RoutePlanner.Leg::line).toList());
        assertEquals(station("X"), route.legs().get(0).stations().get(1));
        assertEquals(station("X"), route.legs().get(1).stations().get(0));
    }

    @Test
    void incomingLineIsRetainedToMinimizeTransfers() {
        var graph = network(line("L1", "A", "B", "X"), line("L2", "A", "D", "X", "T"));
        var route = new RoutePlanner(graph).from(station("A")).get(station("T"));
        assertEquals(3, route.stopCount());
        assertEquals(0, route.transferCount());
        assertEquals("L2", route.legs().get(0).line());
    }

    @Test
    void constructionBlocksBothTravelAndTransfers() {
        var stops = new ArrayList<>(line("L1", "A", "B", "C"));
        stops.set(1, new RouteNetwork.Stop(station("B"), "L1", 2, false));
        stops.addAll(line("L2", "B", "D"));
        var routes = new RoutePlanner(new RouteNetwork(stops)).from(station("A"));
        assertFalse(routes.get(station("C")).reachable());
        assertFalse(routes.get(station("D")).reachable());
        assertEquals(0, routes.get(station("A")).stopCount());
    }

    @Test
    void sameNamesInDifferentDistrictsDoNotCreateTransfers() {
        var stops = new ArrayList<>(line("L1", "A", "X"));
        stops.add(new RouteNetwork.Stop(new StationKey("X", "Other"), "L2", 1, true));
        stops.add(new RouteNetwork.Stop(station("B"), "L2", 2, true));
        assertFalse(new RoutePlanner(new RouteNetwork(stops)).from(station("A")).get(station("B")).reachable());
    }

    @Test
    void tieBreakUsesBusinessKeysAndIgnoresInputOrder() {
        var stops = new ArrayList<>(line("L2", "A", "B", "D"));
        stops.addAll(line("L1", "A", "C", "D"));
        var expected = new RoutePlanner(new RouteNetwork(stops)).from(station("A")).get(station("D"));
        assertEquals("L1", expected.legs().get(0).line());
        Collections.shuffle(stops, new Random(307));
        assertEquals(expected, new RoutePlanner(new RouteNetwork(stops)).from(station("A")).get(station("D")));
    }

    @Test
    void sameIsolatedStationIsZeroAndUnknownStationIsRejected() {
        var graph = new RouteNetwork(List.of(new RouteNetwork.Stop(station("A"), "L1", 1, false)));
        var planner = new RoutePlanner(graph);
        var route = planner.from(station("A")).get(station("A"));
        assertEquals(0, route.stopCount());
        assertEquals(0, route.transferCount());
        assertTrue(route.legs().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> planner.from(station("missing")));
    }

    @Test
    void invalidLineOrderAndDuplicateStationsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RouteNetwork(List.of(
                new RouteNetwork.Stop(station("A"), "L1", 1, true),
                new RouteNetwork.Stop(station("B"), "L1", 3, true))));
        assertThrows(IllegalArgumentException.class, () -> new RouteNetwork(line("L1", "A", "A")));
    }

    @Test
    void jsonEscapesTextAndRejectsUnreachableRecords() throws Exception {
        StationKey source = new StationKey("甲\"站\\\n", "区\t\u0001");
        var writer = new StringWriter();
        RouteJsonWriter.string(writer, source.chineseName());
        assertEquals("\"甲\\\"站\\\\\\n\"", writer.toString());
        var districtWriter = new StringWriter();
        RouteJsonWriter.string(districtWriter, source.districtName());
        assertEquals("\"区\\t\\u0001\"", districtWriter.toString());
        var routeWriter = new StringWriter();
        assertThrows(IllegalArgumentException.class, () -> RouteJsonWriter.route(routeWriter,
                new RoutePlanner.Route(source, station("B"), null, null, List.of())));
        assertEquals("", routeWriter.toString());
    }

    @Test
    void mainGeneratesDeterministicJsonAndInvalidInputPreservesExistingCache() throws Exception {
        Path csv = temporary.resolve("stations.csv");
        Path json = temporary.resolve("routes.json");
        Files.writeString(csv, HEADER
                + "L1,Line 1,A,A,1,District,Operation,2004,,,,\n"
                + "L1,Line 1,B,B,2,District,Operation,2004,,,,\n"
                + "L2,Line 2,C,C,1,District,Construction,2027,,,,\n", StandardCharsets.UTF_8);
        String[] args = {"--precompute-routes", csv.toString(), json.toString()};
        assertEquals(0, Main.run(args, InputStream.nullInputStream(), SINK, SINK));
        String original = Files.readString(json);
        assertEquals(2, original.lines().filter(row -> row.startsWith("    {\"from\"")).count());
        assertFalse(original.contains("NO_ROUTE"));
        assertEquals(0, Main.run(args, InputStream.nullInputStream(), SINK, SINK));
        assertEquals(original, Files.readString(json));
        assertThrows(IllegalArgumentException.class, () -> RouteCacheGenerator.generate(csv, csv, SINK));
        Files.writeString(csv, "invalid");
        assertEquals(1, Main.run(args, InputStream.nullInputStream(), SINK, SINK));
        assertEquals(original, Files.readString(json));
        assertEquals(2, Main.run(new String[]{"--precompute-routes"}, InputStream.nullInputStream(), SINK, SINK));
        try (var files = Files.list(temporary)) {
            assertEquals(2, files.count());
        }
    }

    @Test
    void allOfficialPairsMatchIndependentFloydWarshallCosts() throws Exception {
        Path csv = Path.of("..", "官方描述及数据", "shenzhen_stations.csv");
        var graph = CsvTopologyLoader.load(csv);
        assertEquals(439, graph.stations().size());
        assertEquals(29, graph.lineCount());
        int size = graph.states().size();
        int weight = size + 1;
        int infinity = 1_000_000_000;
        int[][] distance = new int[size][size];
        for (int from = 0; from < size; from++) {
            Arrays.fill(distance[from], infinity);
            distance[from][from] = 0;
            for (var edge : graph.edgesFrom(from)) {
                distance[from][edge.to()] = edge.stops() * weight + edge.transfers();
            }
        }
        // 简单最优状态路径的换乘数小于状态数，weight 保证站数严格优先。
        for (int via = 0; via < size; via++) {
            for (int from = 0; from < size; from++) {
                if (distance[from][via] == infinity) continue;
                for (int to = 0; to < size; to++) {
                    distance[from][to] = Math.min(distance[from][to], distance[from][via] + distance[via][to]);
                }
            }
        }
        var planner = new RoutePlanner(graph);
        long checked = 0;
        for (var source : graph.stations()) {
            var routes = planner.from(source);
            for (var destination : graph.stations()) {
                if (source.equals(destination)) continue;
                int expected = infinity;
                for (int from : graph.statesAt(source)) {
                    for (int to : graph.statesAt(destination)) expected = Math.min(expected, distance[from][to]);
                }
                var route = routes.get(destination);
                assertEquals(expected != infinity, route.reachable());
                if (route.reachable()) {
                    assertEquals(expected / weight, route.stopCount());
                    assertEquals(expected % weight, route.transferCount());
                    assertEquals(route.stopCount(), route.legs().stream().mapToInt(leg -> leg.stations().size() - 1).sum());
                    assertEquals(route.transferCount(), route.legs().size() - 1);
                    assertEquals(source, route.legs().get(0).stations().get(0));
                    var last = route.legs().get(route.legs().size() - 1).stations();
                    assertEquals(destination, last.get(last.size() - 1));
                }
                checked++;
            }
        }
        assertEquals(192282, checked);
    }
}
