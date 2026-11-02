package edu.cs307.metro.model;

/** 公共 DTO：字段与团队约定一致。业务校验由 Service 负责。 */
public record LandmarkHit(long landmarkId, String landmarkName, StationView station,
                          long exitId, String exitCode) {
}
