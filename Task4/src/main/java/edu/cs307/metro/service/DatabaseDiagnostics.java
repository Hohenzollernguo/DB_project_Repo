package edu.cs307.metro.service;

import edu.cs307.metro.dao.DatabaseInfoDao;
import edu.cs307.metro.db.DatabaseStatus;
import edu.cs307.metro.db.Transactions;

/** 运维诊断独立于六个业务接口，不触发建表、导入或业务写入。 */
public final class DatabaseDiagnostics {
    private final Transactions transactions;
    private final DatabaseInfoDao dao;

    public DatabaseDiagnostics(Transactions transactions, DatabaseInfoDao dao) {
        this.transactions = transactions;
        this.dao = dao;
    }

    public DatabaseStatus check() {
        return transactions.readSnapshot(dao::read);
    }
}
