package edu.cs307.metro.service.impl;

import edu.cs307.metro.db.Transactions;
import edu.cs307.metro.common.AppException;
import edu.cs307.metro.common.ErrorCode;
import edu.cs307.metro.dao.RouteDirectoryDao;
import edu.cs307.metro.model.*;
import edu.cs307.metro.routing.PrecomputedRouteCache;
import edu.cs307.metro.routing.RouteNetwork;
import edu.cs307.metro.routing.RoutePlanner;
import edu.cs307.metro.service.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 按老师确认的固定数据规则查询缓存，数据库仅提供业务键与 ID 的对应关系。 */
public final class JdbcRouteService implements RouteService {
    protected final Transactions transactions;
    private final PrecomputedRouteCache cache;
    private final RouteDirectoryDao dao = new RouteDirectoryDao();

    public JdbcRouteService(Transactions transactions) {
        this(transactions, defaultCache());
    }

    public JdbcRouteService(Transactions transactions, PrecomputedRouteCache cache) {
        this.transactions = Objects.requireNonNull(transactions);
        this.cache = Objects.requireNonNull(cache);
    }

    private static PrecomputedRouteCache defaultCache() {
        Path module = Path.of(".");
        for (Path candidate : List.of(Path.of("."), Path.of("Task4"), Path.of("DB_project_Repo", "Task4"))) {
            if (Files.isRegularFile(candidate.resolve("pom.xml")) && Files.isDirectory(candidate.resolve("cache"))) {
                module = candidate;
                break;
            }
        }
        Path source = Path.of(System.getProperty("metro.routeSource",
                module.resolve("../官方描述及数据/shenzhen_stations.csv").toString()));
        Path json = Path.of(System.getProperty("metro.routeCache", module.resolve("cache/routes-cache.json").toString()));
        return new PrecomputedRouteCache(source, json);
    }

    @Override
    public RouteResult findRoute(long fromStationId, long toStationId) {
        return findBestRoute(List.of(fromStationId), List.of(toStationId));
    }

    @Override
    public RouteResult findBestRoute(List<Long> fromStationIds, List<Long> toStationIds) {
        List<Long> fromIds = validateIds(fromStationIds);
        List<Long> toIds = validateIds(toStationIds);
        return transactions.readSnapshot(connection -> {
            var topology = dao.load(connection);
            for (long id : fromIds) requireStation(topology, id);
            for (long id : toIds) requireStation(topology, id);
            RoutePlanner.Route chosen = null;
            for (long from : fromIds) {
                for (long to : toIds) {
                    if (from == to) {
                        StationKey key = topology.stations().get(from).key();
                        var route = new RoutePlanner.Route(key, key, 0, 0, List.of());
                        if (chosen == null || compare(route, chosen) < 0) chosen = route;
                    }
                }
            }
            if (chosen != null) return toResult(chosen, topology, null);
            PrecomputedRouteCache.Data data = loadCache();
            for (long from : fromIds) {
                StationKey source = topology.stations().get(from).key();
                for (long to : toIds) {
                    StationKey destination = topology.stations().get(to).key();
                    RoutePlanner.Route route = data.find(source, destination);
                    if (route != null && route.reachable() && (chosen == null || compare(route, chosen) < 0)) chosen = route;
                }
            }
            if (chosen == null) throw new AppException(ErrorCode.NO_ROUTE, "所选车站之间没有可用运营路线。");
            return toResult(chosen, topology, data);
        });
    }

    private PrecomputedRouteCache.Data loadCache() {
        try {
            return cache.data();
        } catch (IOException | IllegalArgumentException failure) {
            throw new AppException(ErrorCode.DB_ERROR, "路线缓存读取失败，请检查文件或重新生成。", failure);
        }
    }

    private static List<Long> validateIds(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new AppException(ErrorCode.INVALID_INPUT, "请提供非空的正整数车站 ID 列表。");
        }
        return ids.stream().distinct().toList();
    }

    private static void requireStation(RouteDirectoryDao.Directory topology, long id) {
        if (!topology.stations().containsKey(id)) throw new AppException(ErrorCode.NOT_FOUND, "未找到车站，ID=" + id);
    }

    private static RouteResult toResult(RoutePlanner.Route route, RouteDirectoryDao.Directory topology,
                                        PrecomputedRouteCache.Data data) {
        if (route.legs().isEmpty()) {
            return new RouteResult(List.of(topology.stationsByKey().get(route.from())), List.of(), 0, 0);
        }
        var legs = new ArrayList<RouteLeg>();
        var stations = new ArrayList<StationView>();
        for (var leg : route.legs()) {
            List<StationView> segment = leg.stations().stream().map(key -> {
                StationView current = topology.stationsByKey().get(key);
                return data.station(key, current == null ? 0 : current.stationId());
            }).toList();
            LineView current = topology.lines().get(leg.line());
            legs.add(new RouteLeg(data.line(leg.line(), current == null ? 0 : current.lineId()), segment));
            stations.addAll(stations.isEmpty() ? segment : segment.subList(1, segment.size()));
        }
        return new RouteResult(stations, legs, route.stopCount(), route.transferCount());
    }

    private static int compare(RoutePlanner.Route left, RoutePlanner.Route right) {
        int result = Integer.compare(left.stopCount(), right.stopCount());
        if (result != 0) return result;
        result = Integer.compare(left.transferCount(), right.transferCount());
        if (result != 0) return result;
        var a = signature(left);
        var b = signature(right);
        for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
            result = RouteNetwork.STATION_ORDER.compare(a.get(i).station(), b.get(i).station());
            if (result == 0) result = a.get(i).line().compareTo(b.get(i).line());
            if (result != 0) return result;
        }
        return Integer.compare(a.size(), b.size());
    }

    private static List<RouteNetwork.State> signature(RoutePlanner.Route route) {
        if (route.legs().isEmpty()) return List.of(new RouteNetwork.State(route.from(), ""));
        var states = new ArrayList<RouteNetwork.State>();
        for (var leg : route.legs()) {
            for (StationKey station : leg.stations()) states.add(new RouteNetwork.State(station, leg.line()));
        }
        return states;
    }
}
