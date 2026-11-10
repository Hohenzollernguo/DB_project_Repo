package edu.cs307.metro.service.impl;

import edu.cs307.metro.common.AppException;
import edu.cs307.metro.common.ErrorCode;
import edu.cs307.metro.dao.NeighborDao;
import edu.cs307.metro.db.Transactions;
import edu.cs307.metro.model.NeighborPair;
import edu.cs307.metro.service.NeighborService;
import java.util.Objects;

/** 邻站查询在同一只读快照内调用数据库 Q7 函数。 */
public final class JdbcNeighborService implements NeighborService {
    private final Transactions transactions;
    private final NeighborDao neighborDao = new NeighborDao();

    public JdbcNeighborService(Transactions transactions) {
        this.transactions = Objects.requireNonNull(transactions);
    }

    @Override
    public NeighborPair get(String stationEnglishName, String lineEnglishName, int n) {
        String station = normalizeName(stationEnglishName, "车站英文名");
        String line = normalizeName(lineEnglishName, "线路英文名");
        if (n < 0) {
            throw new AppException(ErrorCode.INVALID_INPUT, "n 必须是非负整数。");
        }
        return transactions.readSnapshot(connection -> neighborDao.get(connection, station, line, n));
    }

    private static String normalizeName(String name, String label) {
        if (name == null || name.strip().isEmpty() || name.indexOf('\0') >= 0) {
            throw new AppException(ErrorCode.INVALID_INPUT, label + "不能为空白或包含空字符。");
        }
        return name.strip();
    }
}
