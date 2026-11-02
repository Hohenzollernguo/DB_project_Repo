package edu.cs307.metro.service.impl;

import edu.cs307.metro.db.Transactions;
import edu.cs307.metro.model.*;
import edu.cs307.metro.service.*;
import java.util.List;
import java.util.Objects;

/** 开发骨架：实现规则见架构说明。完成业务前明确失败，不返回伪造成功结果。 */
public final class JdbcStationService implements StationService {
    protected final Transactions transactions;

    public JdbcStationService(Transactions transactions) {
        this.transactions = Objects.requireNonNull(transactions);
    }

    @Override
    public StationView create(StationDraft input) {
        throw new UnsupportedOperationException("StationService 的 create 功能待实现");
    }

    @Override
    public StationView update(long stationId, StationDraft input) {
        throw new UnsupportedOperationException("StationService 的 update 功能待实现");
    }

    @Override
    public void delete(long stationId) {
        throw new UnsupportedOperationException("StationService 的 delete 功能待实现");
    }

    @Override
    public List<StationView> search(String keyword) {
        throw new UnsupportedOperationException("StationService 的 search 功能待实现");
    }
}
