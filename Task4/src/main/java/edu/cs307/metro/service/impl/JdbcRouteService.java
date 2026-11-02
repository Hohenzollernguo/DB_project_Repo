package edu.cs307.metro.service.impl;

import edu.cs307.metro.db.Transactions;
import edu.cs307.metro.model.*;
import edu.cs307.metro.service.*;
import java.util.List;
import java.util.Objects;

/** 开发骨架：实现规则见架构说明。完成业务前明确失败，不返回伪造成功结果。 */
public final class JdbcRouteService implements RouteService {
    protected final Transactions transactions;

    public JdbcRouteService(Transactions transactions) {
        this.transactions = Objects.requireNonNull(transactions);
    }

    @Override
    public RouteResult findRoute(long fromStationId, long toStationId) {
        throw new UnsupportedOperationException("RouteService 的 findRoute 功能待实现");
    }

    @Override
    public RouteResult findBestRoute(List<Long> fromStationIds, List<Long> toStationIds) {
        throw new UnsupportedOperationException("RouteService 的 findBestRoute 功能待实现");
    }
}
