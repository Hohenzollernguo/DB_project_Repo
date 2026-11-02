package edu.cs307.metro.model;

import java.util.List;

/** 公共 DTO：字段与团队约定一致。业务校验由 Service 负责。 */
public record RouteResult(List<StationView> stations, List<RouteLeg> legs,
                          int stopCount, int transferCount) {
    public RouteResult {
        stations = List.copyOf(stations);
        legs = List.copyOf(legs);
    }

}
