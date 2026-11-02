package edu.cs307.metro.service.impl;

import edu.cs307.metro.db.Transactions;
import edu.cs307.metro.model.*;
import edu.cs307.metro.service.*;
import java.util.List;
import java.util.Objects;

/** 开发骨架：实现规则见架构说明。完成业务前明确失败，不返回伪造成功结果。 */
public final class JdbcNeighborService implements NeighborService {
    protected final Transactions transactions;

    public JdbcNeighborService(Transactions transactions) {
        this.transactions = Objects.requireNonNull(transactions);
    }

    @Override
    public NeighborPair get(String stationEnglishName, String lineEnglishName, int n) {
        throw new UnsupportedOperationException("NeighborService 的 get 功能待实现");
    }
}
