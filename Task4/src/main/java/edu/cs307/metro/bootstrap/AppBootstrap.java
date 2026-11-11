package edu.cs307.metro.bootstrap;

import edu.cs307.metro.config.DbConfig;
import edu.cs307.metro.dao.DatabaseInfoDao;
import edu.cs307.metro.dao.LineDao;
import edu.cs307.metro.db.*;
import edu.cs307.metro.service.*;
import edu.cs307.metro.service.impl.*;

public final class AppBootstrap {
    private AppBootstrap() {}

    public static AppServices create() {
        // 启动时不要求数据库在线；用户执行数据库操作时才读取配置和建立连接。
        return create(new DriverManagerConnectionFactory(DbConfig::fromEnvironment));
    }

    public static AppServices create(ConnectionFactory connections) {
        Transactions transactions = new Transactions(connections);
        RouteService routes = new JdbcRouteService(transactions);
        return new AppServices(
                new JdbcStationService(transactions),
                new JdbcLineService(transactions, new LineDao()),
                new JdbcNeighborService(transactions),
                routes,
                new DefaultLandmarkService(transactions, routes),
                new StationFareService(routes),
                new DatabaseDiagnostics(transactions, new DatabaseInfoDao()));
    }
}
