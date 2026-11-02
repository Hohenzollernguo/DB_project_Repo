package edu.cs307.metro.service.impl;

import edu.cs307.metro.model.FareQuote;
import edu.cs307.metro.service.FareService;

/** 团队约定的待配置状态；不算票价功能已完成。A 后续替换本实现。 */
public final class UnconfiguredFareService implements FareService {
    @Override
    public FareQuote quote(long fromStationId, long toStationId) {
        return new FareQuote("RULE_NOT_CONFIGURED", null, "CNY", null,
                "尚未配置经确认的计费规则及所需数据，暂时无法计算票价。");
    }
}
