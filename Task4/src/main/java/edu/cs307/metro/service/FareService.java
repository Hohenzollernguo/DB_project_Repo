package edu.cs307.metro.service;

import edu.cs307.metro.model.*;

/** 成功返回 DTO，业务失败抛 AppException；查询列表无匹配时返回空列表。 */
public interface FareService {
    FareQuote quote(long fromStationId, long toStationId);
}
