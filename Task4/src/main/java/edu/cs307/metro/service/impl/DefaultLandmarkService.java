package edu.cs307.metro.service.impl;

import edu.cs307.metro.common.AppException;
import edu.cs307.metro.common.ErrorCode;
import edu.cs307.metro.dao.LandmarkDao;
import edu.cs307.metro.db.Transactions;
import edu.cs307.metro.model.*;
import edu.cs307.metro.service.*;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 开发骨架：实现规则见架构说明。完成业务前明确失败，不返回伪造成功结果。 */
public final class DefaultLandmarkService implements LandmarkService {
    protected final Transactions transactions;
    protected final RouteService routes;
    private final LandmarkDao landmarkDao = new LandmarkDao();

    public DefaultLandmarkService(Transactions transactions, RouteService routes) {
        this.transactions = Objects.requireNonNull(transactions);
        this.routes = Objects.requireNonNull(routes);
    }

    @Override
    public List<LandmarkHit> searchPrefix(String literalPrefix) {
        if (literalPrefix == null) {
            throw new AppException(ErrorCode.INVALID_INPUT, "请输入地标名称。");
        }
        String prefix = literalPrefix.strip();
        return transactions.readSnapshot(connection -> landmarkDao.searchPrefix(connection, prefix));
    }

    @Override
    public LandmarkRoute findRoute(String fromName, String toName) {
        if (fromName == null || fromName.isBlank()) {
            throw new AppException(ErrorCode.INVALID_INPUT, "请输入起点地标全名。");
        }

        if (toName == null || toName.isBlank()) {
            throw new AppException(ErrorCode.INVALID_INPUT, "请输入终点地标全名。");
        }

        String fromLandmarkName = fromName.strip();
        String toLandmarkName = toName.strip();

        return transactions.readSnapshot(connection -> {
            List<LandmarkHit> fromHits = landmarkDao.findByName(connection, fromLandmarkName);
            if (fromHits.isEmpty()) {
                throw new AppException(ErrorCode.NOT_FOUND, "未找到起点地标：" + fromLandmarkName);
            }

            List<LandmarkHit> toHits = landmarkDao.findByName(connection, toLandmarkName);
            if (toHits.isEmpty()) {
                throw new AppException(ErrorCode.NOT_FOUND, "未找到终点地标：" + toLandmarkName);
            }
            List<Long> fromStationIds = collectStationIds(fromHits);
            List<Long> toStationIds = collectStationIds(toHits);
            RouteResult route = routes.findBestRoute(fromStationIds, toStationIds);

            List<StationView> routeStations = route.stations();
            if (routeStations.isEmpty()) {
                throw new AppException(ErrorCode.DB_ERROR, "路线结果中没有车站。");
            }

            StationView firstStation = routeStations.get(0);
            StationView lastStation = routeStations.get(routeStations.size() - 1);

            LandmarkHit fromHit = findHitForStation(fromHits, firstStation.stationId());
            LandmarkHit toHit = findHitForStation(toHits, lastStation.stationId());

            return new LandmarkRoute(fromHit, toHit, route);
        });
    }
    private List<Long> collectStationIds(List<LandmarkHit> hits) {
        Set<Long> stationIds = new LinkedHashSet<>();

        for (LandmarkHit hit : hits) {
            long stationId = hit.station().stationId();
            stationIds.add(stationId);
        }

        return List.copyOf(stationIds);
    }
    private LandmarkHit findHitForStation(List<LandmarkHit> hits, long stationId) {
        for (LandmarkHit hit : hits) {
            if (hit.station().stationId() == stationId) {
                return hit;
            }
        }

        throw new AppException(ErrorCode.DB_ERROR, "找不到路线端点对应的地标出口。");
    }
}
