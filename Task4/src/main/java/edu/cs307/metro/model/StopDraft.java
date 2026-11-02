package edu.cs307.metro.model;

import java.time.LocalTime;
import java.math.BigDecimal;

/** 公共 DTO：字段与团队约定一致。业务校验由 Service 负责。 */
public record StopDraft(long stationId, String status, int openingYear,
                        LocalTime startTime, LocalTime endTime,
                        BigDecimal sourceLatitude, BigDecimal sourceLongitude) {
}
