package edu.cs307.metro.bootstrap;

import edu.cs307.metro.service.*;

/** 统一装配后的服务集合；CLI 与 Swing 共用此对象，不各自创建业务逻辑。 */
public record AppServices(
        StationService stations,
        LineService lines,
        NeighborService neighbors,
        RouteService routes,
        LandmarkService landmarks,
        FareService fares,
        DatabaseDiagnostics database) {}
