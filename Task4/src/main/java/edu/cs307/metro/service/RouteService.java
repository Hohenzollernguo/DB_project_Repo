package edu.cs307.metro.service;

import edu.cs307.metro.model.*;
import java.util.List;

/** 成功返回 DTO，业务失败抛 AppException；查询列表无匹配时返回空列表。 */
public interface RouteService {
    RouteResult findRoute(long fromStationId, long toStationId);
    RouteResult findBestRoute(List<Long> fromStationIds, List<Long> toStationIds);
}
