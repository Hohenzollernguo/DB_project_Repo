package edu.cs307.metro.service.impl;

import edu.cs307.metro.db.Transactions;
import edu.cs307.metro.model.*;
import edu.cs307.metro.service.*;
import java.util.List;
import java.util.Objects;

/** 开发骨架：实现规则见架构说明。完成业务前明确失败，不返回伪造成功结果。 */
public final class JdbcLineService implements LineService {
    protected final Transactions transactions;

    public JdbcLineService(Transactions transactions) {
        this.transactions = Objects.requireNonNull(transactions);
    }

    @Override
    public LineView create(LineDraft input) {
        throw new UnsupportedOperationException("LineService 的 create 功能待实现");
    }

    @Override
    public LineView update(long lineId, LineDraft input) {
        throw new UnsupportedOperationException("LineService 的 update 功能待实现");
    }

    @Override
    public void delete(long lineId) {
        throw new UnsupportedOperationException("LineService 的 delete 功能待实现");
    }

    @Override
    public List<LineView> search(String keyword) {
        throw new UnsupportedOperationException("LineService 的 search 功能待实现");
    }

    @Override
    public List<LineStopView> getStops(long lineId) {
        throw new UnsupportedOperationException("LineService 的 getStops 功能待实现");
    }

    @Override
    public void insertStops(long lineId, int position, List<StopDraft> stops) {
        throw new UnsupportedOperationException("LineService 的 insertStops 功能待实现");
    }

    @Override
    public void updateStop(long lineId, StopDraft stop) {
        throw new UnsupportedOperationException("LineService 的 updateStop 功能待实现");
    }

    @Override
    public void removeStop(long lineId, long stationId) {
        throw new UnsupportedOperationException("LineService 的 removeStop 功能待实现");
    }
}
