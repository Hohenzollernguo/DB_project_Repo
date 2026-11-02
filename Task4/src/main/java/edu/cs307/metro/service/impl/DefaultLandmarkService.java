package edu.cs307.metro.service.impl;

import edu.cs307.metro.db.Transactions;
import edu.cs307.metro.model.*;
import edu.cs307.metro.service.*;
import java.util.List;
import java.util.Objects;

/** 开发骨架：实现规则见架构说明。完成业务前明确失败，不返回伪造成功结果。 */
public final class DefaultLandmarkService implements LandmarkService {
    protected final Transactions transactions;
    protected final RouteService routes;

    public DefaultLandmarkService(Transactions transactions, RouteService routes) {
        this.transactions = Objects.requireNonNull(transactions);
        this.routes = Objects.requireNonNull(routes);
    }

    @Override
    public List<LandmarkHit> searchPrefix(String literalPrefix) {
        throw new UnsupportedOperationException("LandmarkService 的 searchPrefix 功能待实现");
    }

    @Override
    public LandmarkRoute findRoute(String fromName, String toName) {
        throw new UnsupportedOperationException("LandmarkService 的 findRoute 功能待实现");
    }
}
