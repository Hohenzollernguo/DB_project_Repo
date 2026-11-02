package edu.cs307.metro.model;

import java.util.List;

/** 公共 DTO：字段与团队约定一致。业务校验由 Service 负责。 */
public record RouteLeg(LineView line, List<StationView> stations) {
    public RouteLeg {
        stations = List.copyOf(stations);
    }

}
