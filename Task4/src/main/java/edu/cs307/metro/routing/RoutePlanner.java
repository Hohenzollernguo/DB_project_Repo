package edu.cs307.metro.routing;

import edu.cs307.metro.model.StationKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/** 非负二元代价 Dijkstra：站间段、换乘、完整状态业务键序列依次比较。 */
public final class RoutePlanner {
    public record Leg(String line, List<StationKey> stations) {
        public Leg { stations = List.copyOf(stations); }
    }

    public record Route(StationKey from, StationKey to, Integer stopCount,
                        Integer transferCount, List<Leg> legs) {
        public Route { legs = List.copyOf(legs); }
        public boolean reachable() { return stopCount != null; }
    }

    private record Label(int state, int stops, int transfers, List<Integer> path) {}
    private static final Comparator<Label> LABEL_ORDER = (left, right) -> {
        int comparison = Integer.compare(left.stops(), right.stops());
        if (comparison != 0) return comparison;
        comparison = Integer.compare(left.transfers(), right.transfers());
        if (comparison != 0) return comparison;
        // 状态编号按 (中文站名, 行政区, 完整中文线路名) 排序，与输入顺序无关。
        int size = Math.min(left.path().size(), right.path().size());
        for (int i = 0; i < size; i++) {
            comparison = Integer.compare(left.path().get(i), right.path().get(i));
            if (comparison != 0) return comparison;
        }
        return Integer.compare(left.path().size(), right.path().size());
    };

    private final RouteNetwork network;

    public RoutePlanner(RouteNetwork network) {
        this.network = network;
    }

    /** 一次搜索得到一个起点到全部站点的结果，包含自身与不可达站点。 */
    public Map<StationKey, Route> from(StationKey source) {
        if (!network.stations().contains(source)) {
            throw new IllegalArgumentException("起点不存在：" + source);
        }
        Label[] best = new Label[network.states().size()];
        var queue = new PriorityQueue<>(LABEL_ORDER);
        for (int state : network.statesAt(source)) {
            Label initial = new Label(state, 0, 0, List.of(state));
            best[state] = initial;
            queue.add(initial);
        }
        while (!queue.isEmpty()) {
            Label current = queue.remove();
            if (best[current.state()] != current) continue;
            for (RouteNetwork.Edge edge : network.edgesFrom(current.state())) {
                int stops = current.stops() + edge.stops();
                int transfers = current.transfers() + edge.transfers();
                Label previous = best[edge.to()];
                if (previous != null && (stops > previous.stops()
                        || (stops == previous.stops() && transfers > previous.transfers()))) {
                    continue;
                }
                var path = new ArrayList<>(current.path());
                path.add(edge.to());
                Label candidate = new Label(edge.to(), stops, transfers, List.copyOf(path));
                if (previous == null || LABEL_ORDER.compare(candidate, previous) < 0) {
                    best[edge.to()] = candidate;
                    queue.add(candidate);
                }
            }
        }
        var routes = new LinkedHashMap<StationKey, Route>();
        for (StationKey destination : network.stations()) {
            if (source.equals(destination)) {
                routes.put(destination, new Route(source, destination, 0, 0, List.of()));
                continue;
            }
            Label chosen = null;
            for (int state : network.statesAt(destination)) {
                Label candidate = best[state];
                if (candidate != null && (chosen == null || LABEL_ORDER.compare(candidate, chosen) < 0)) {
                    chosen = candidate;
                }
            }
            Route route = chosen == null
                    ? new Route(source, destination, null, null, List.of())
                    : new Route(source, destination, chosen.stops(), chosen.transfers(), legs(chosen.path()));
            routes.put(destination, route);
        }
        return routes;
    }

    private List<Leg> legs(List<Integer> path) {
        var legs = new ArrayList<Leg>();
        RouteNetwork.State first = network.states().get(path.get(0));
        String line = first.line();
        var stations = new ArrayList<StationKey>();
        stations.add(first.station());
        for (int i = 1; i < path.size(); i++) {
            RouteNetwork.State state = network.states().get(path.get(i));
            if (!line.equals(state.line())) {
                legs.add(new Leg(line, stations));
                line = state.line();
                stations = new ArrayList<>();
            }
            stations.add(state.station());
        }
        legs.add(new Leg(line, stations));
        return legs;
    }
}
