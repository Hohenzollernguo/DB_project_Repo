package edu.cs307.metro.routing;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import edu.cs307.metro.model.StationKey;
import edu.cs307.metro.model.StationView;
import edu.cs307.metro.model.LineView;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 首次使用时流式加载 JSON，站点和线路共用字典，随后通过有向业务键查询。 */
public final class PrecomputedRouteCache {
    private record Pair(StationKey from, StationKey to) {}
    private record Step(StationKey from, StationKey to, String line) {}

    public static final class Data {
        private final RouteNetwork network;
        private final Map<Pair, RoutePlanner.Route> routes;
        private final Map<StationKey, String> stationNames;
        private final Map<String, String> lineNames;

        private Data(CsvTopologyLoader.Source source, Map<Pair, RoutePlanner.Route> routes) {
            this.network = source.network();
            this.stationNames = source.stationNames();
            this.lineNames = source.lineNames();
            this.routes = Map.copyOf(routes);
        }

        public RouteNetwork network() { return network; }
        public int size() { return routes.size(); }
        public RoutePlanner.Route find(StationKey from, StationKey to) {
            return routes.get(new Pair(from, to));
        }
        /** ID 0 表示该缓存展示对象在当前库已不存在，不可用作 CRUD 目标。 */
        public StationView station(StationKey key, long currentId) {
            return new StationView(currentId, key, stationNames.get(key),
                    network.statesAt(key).isEmpty() ? "Construction" : "Operation");
        }
        public LineView line(String name, long currentId) {
            return new LineView(currentId, name, lineNames.get(name));
        }
    }

    private final Path source;
    private final Path json;
    private Data loaded;

    public PrecomputedRouteCache(Path source, Path json) {
        this.source = source;
        this.json = json;
    }

    public synchronized Data data() throws IOException {
        if (loaded == null) loaded = load();
        return loaded;
    }

    private Data load() throws IOException {
        CsvTopologyLoader.Source sourceData = CsvTopologyLoader.read(source);
        RouteNetwork network = sourceData.network();
        var stations = new HashMap<StationKey, StationKey>();
        network.stations().forEach(key -> stations.put(key, key));
        var lines = new HashMap<String, String>();
        var steps = new HashSet<Step>();
        for (int i = 0; i < network.states().size(); i++) {
            var state = network.states().get(i);
            lines.put(state.line(), state.line());
            for (var edge : network.edgesFrom(i)) {
                if (edge.stops() == 1) {
                    steps.add(new Step(state.station(), network.states().get(edge.to()).station(), state.line()));
                }
            }
        }
        var routes = new HashMap<Pair, RoutePlanner.Route>();
        boolean version = false;
        boolean rule = false;
        boolean entries = false;
        JsonFactory factory = new JsonFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        try (JsonParser parser = factory.createParser(json.toFile())) {
            require(parser.nextToken() == JsonToken.START_OBJECT, "缓存顶层必须为对象");
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                require(parser.currentToken() == JsonToken.FIELD_NAME, "缓存字段不完整");
                String field = parser.currentName();
                parser.nextToken();
                switch (field) {
                    case "formatVersion" -> { require(number(parser) == 1, "缓存版本不受支持"); version = true; }
                    case "routingRule" -> {
                        require(RouteCacheGenerator.ROUTING_RULE.equals(text(parser)), "缓存路线规则不一致");
                        rule = true;
                    }
                    case "routes" -> {
                        require(parser.currentToken() == JsonToken.START_ARRAY, "routes 必须为数组");
                        while (parser.nextToken() != JsonToken.END_ARRAY) {
                            RoutePlanner.Route route = readRoute(parser, stations, lines);
                            validate(route, steps);
                            require(routes.putIfAbsent(new Pair(route.from(), route.to()), route) == null,
                                    "缓存存在重复起终点");
                        }
                        entries = true;
                    }
                    default -> throw new IOException("缓存存在未知字段：" + field);
                }
            }
            require(parser.nextToken() == null, "缓存末尾存在额外内容");
        }
        require(version && rule && entries, "缓存缺少必填字段");
        require(routes.size() == reachablePairs(network), "缓存未覆盖全部可达起终点");
        return new Data(sourceData, routes);
    }

    private static RoutePlanner.Route readRoute(JsonParser parser, Map<StationKey, StationKey> stations,
                                                Map<String, String> lines) throws IOException {
        require(parser.currentToken() == JsonToken.START_OBJECT, "路线必须为对象");
        StationKey from = null;
        StationKey to = null;
        Integer stops = null;
        Integer transfers = null;
        List<RoutePlanner.Leg> legs = null;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            require(parser.currentToken() == JsonToken.FIELD_NAME, "路线字段不完整");
            String field = parser.currentName();
            parser.nextToken();
            switch (field) {
                case "from" -> from = readStation(parser, stations);
                case "to" -> to = readStation(parser, stations);
                case "stopCount" -> stops = number(parser);
                case "transferCount" -> transfers = number(parser);
                case "legs" -> {
                    require(parser.currentToken() == JsonToken.START_ARRAY, "legs 必须为数组");
                    legs = new ArrayList<>();
                    while (parser.nextToken() != JsonToken.END_ARRAY) legs.add(readLeg(parser, stations, lines));
                }
                default -> throw new IOException("路线存在未知字段：" + field);
            }
        }
        require(from != null && to != null && stops != null && transfers != null && legs != null,
                "路线缺少必填字段");
        return new RoutePlanner.Route(from, to, stops, transfers, legs);
    }

    private static RoutePlanner.Leg readLeg(JsonParser parser, Map<StationKey, StationKey> stations,
                                            Map<String, String> lines) throws IOException {
        require(parser.currentToken() == JsonToken.START_OBJECT, "路段必须为对象");
        String line = null;
        List<StationKey> keys = null;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            require(parser.currentToken() == JsonToken.FIELD_NAME, "路段字段不完整");
            String field = parser.currentName();
            parser.nextToken();
            switch (field) {
                case "line" -> { line = lines.get(text(parser)); require(line != null, "缓存线路不存在"); }
                case "stations" -> {
                    require(parser.currentToken() == JsonToken.START_ARRAY, "stations 必须为数组");
                    keys = new ArrayList<>();
                    while (parser.nextToken() != JsonToken.END_ARRAY) keys.add(readStation(parser, stations));
                }
                default -> throw new IOException("路段存在未知字段：" + field);
            }
        }
        require(line != null && keys != null, "路段缺少必填字段");
        return new RoutePlanner.Leg(line, keys);
    }

    private static StationKey readStation(JsonParser parser, Map<StationKey, StationKey> stations) throws IOException {
        require(parser.currentToken() == JsonToken.START_OBJECT, "站点必须为对象");
        String name = null;
        String district = null;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            require(parser.currentToken() == JsonToken.FIELD_NAME, "站点字段不完整");
            String field = parser.currentName();
            parser.nextToken();
            switch (field) {
                case "name" -> name = text(parser);
                case "district" -> district = text(parser);
                default -> throw new IOException("站点存在未知字段：" + field);
            }
        }
        require(name != null && district != null, "站点缺少必填字段");
        StationKey key = stations.get(new StationKey(name, district));
        require(key != null, "缓存站点不存在");
        return key;
    }

    private static void validate(RoutePlanner.Route route, Set<Step> steps) throws IOException {
        require(!route.from().equals(route.to()) && !route.legs().isEmpty(), "缓存不能包含同站或空路线");
        StationKey previous = route.from();
        String previousLine = null;
        int stopCount = 0;
        for (var leg : route.legs()) {
            require(leg.stations().size() >= 2 && leg.stations().get(0).equals(previous), "缓存路段不连续");
            require(!leg.line().equals(previousLine), "连续同线路路段需要合并");
            for (int i = 1; i < leg.stations().size(); i++) {
                require(steps.contains(new Step(leg.stations().get(i - 1), leg.stations().get(i), leg.line())),
                        "缓存包含非运营连接");
            }
            previous = leg.stations().get(leg.stations().size() - 1);
            previousLine = leg.line();
            stopCount += leg.stations().size() - 1;
        }
        require(previous.equals(route.to()) && stopCount == route.stopCount()
                && route.legs().size() - 1 == route.transferCount(), "缓存路线统计不一致");
    }

    private static long reachablePairs(RouteNetwork network) {
        boolean[] visited = new boolean[network.states().size()];
        long count = 0;
        for (int state = 0; state < visited.length; state++) {
            if (visited[state]) continue;
            var stations = new HashSet<StationKey>();
            var queue = new ArrayDeque<Integer>();
            queue.add(state);
            visited[state] = true;
            while (!queue.isEmpty()) {
                int current = queue.remove();
                stations.add(network.states().get(current).station());
                for (var edge : network.edgesFrom(current)) {
                    if (!visited[edge.to()]) { visited[edge.to()] = true; queue.add(edge.to()); }
                }
            }
            count += (long) stations.size() * (stations.size() - 1);
        }
        return count;
    }

    private static int number(JsonParser parser) throws IOException {
        require(parser.currentToken() == JsonToken.VALUE_NUMBER_INT, "缓存计数必须为整数");
        int value = parser.getIntValue();
        require(value >= 0, "缓存计数不能为负数");
        return value;
    }

    private static String text(JsonParser parser) throws IOException {
        require(parser.currentToken() == JsonToken.VALUE_STRING, "缓存文本字段必须为字符串");
        return parser.getText();
    }

    private static void require(boolean condition, String message) throws IOException {
        if (!condition) throw new IOException(message);
    }
}
