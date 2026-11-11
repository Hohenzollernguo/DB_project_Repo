package edu.cs307.metro.routing;

import edu.cs307.metro.model.StationKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/** 预计算使用的不可变运营网络。编号仅用于内存索引，与数据库 ID 无关。 */
public final class RouteNetwork {
    public static final Comparator<StationKey> STATION_ORDER = Comparator
            .comparing(StationKey::chineseName).thenComparing(StationKey::districtName);
    private static final Comparator<State> STATE_ORDER = Comparator
            .comparing(State::station, STATION_ORDER).thenComparing(State::line);

    public record Stop(StationKey station, String line, int position, boolean operational) {}
    public record State(StationKey station, String line) {}
    public record Edge(int to, int stops, int transfers) {}

    private final List<StationKey> stations;
    private final List<State> states;
    private final List<List<Edge>> edges;
    private final Map<StationKey, List<Integer>> statesByStation;
    private final int lineCount;

    public RouteNetwork(List<Stop> stops) {
        if (stops.isEmpty()) {
            throw new IllegalArgumentException("站线数据不能为空");
        }
        var stationSet = new TreeSet<>(STATION_ORDER);
        var stateSet = new TreeSet<>(STATE_ORDER);
        var lines = new TreeMap<String, List<Stop>>();
        for (Stop stop : stops) {
            if (stop.station() == null || blank(stop.station().chineseName())
                    || blank(stop.station().districtName()) || blank(stop.line()) || stop.position() < 1) {
                throw new IllegalArgumentException("站点、行政区、线路不能为空，站序必须为正整数");
            }
            stationSet.add(stop.station());
            lines.computeIfAbsent(stop.line(), ignored -> new ArrayList<>()).add(stop);
            if (stop.operational()) {
                stateSet.add(new State(stop.station(), stop.line()));
            }
        }
        stations = List.copyOf(stationSet);
        states = List.copyOf(stateSet);
        lineCount = lines.size();
        var indexes = new HashMap<State, Integer>();
        var stationStates = new HashMap<StationKey, List<Integer>>();
        var adjacency = new ArrayList<List<Edge>>();
        for (int i = 0; i < states.size(); i++) {
            State state = states.get(i);
            indexes.put(state, i);
            stationStates.computeIfAbsent(state.station(), ignored -> new ArrayList<>()).add(i);
            adjacency.add(new ArrayList<>());
        }
        for (List<Stop> line : lines.values()) {
            line.sort(Comparator.comparingInt(Stop::position));
            var seen = new HashSet<StationKey>();
            for (int i = 0; i < line.size(); i++) {
                Stop current = line.get(i);
                if (current.position() != i + 1 || !seen.add(current.station())) {
                    throw new IllegalArgumentException("线路站序必须连续且站点不能重复：" + current.line());
                }
                // 在完整站序上判断邻接，绝不跨过 Construction 站连边。
                if (i > 0 && current.operational() && line.get(i - 1).operational()) {
                    Stop previous = line.get(i - 1);
                    int from = indexes.get(new State(previous.station(), previous.line()));
                    int to = indexes.get(new State(current.station(), current.line()));
                    connect(adjacency, from, to, 1, 0);
                }
            }
        }
        for (List<Integer> atStation : stationStates.values()) {
            for (int i = 0; i < atStation.size(); i++) {
                for (int j = i + 1; j < atStation.size(); j++) {
                    connect(adjacency, atStation.get(i), atStation.get(j), 0, 1);
                }
            }
        }
        edges = adjacency.stream().map(List::copyOf).toList();
        var immutableStationStates = new HashMap<StationKey, List<Integer>>();
        stationStates.forEach((key, value) -> immutableStationStates.put(key, List.copyOf(value)));
        statesByStation = Map.copyOf(immutableStationStates);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static void connect(List<List<Edge>> edges, int from, int to, int stops, int transfers) {
        edges.get(from).add(new Edge(to, stops, transfers));
        edges.get(to).add(new Edge(from, stops, transfers));
    }

    public List<StationKey> stations() { return stations; }
    public List<State> states() { return states; }
    public List<Edge> edgesFrom(int state) { return edges.get(state); }
    public List<Integer> statesAt(StationKey station) {
        return statesByStation.getOrDefault(station, List.of());
    }
    public int lineCount() { return lineCount; }
}
