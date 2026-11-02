package edu.cs307.metro.model;

import java.math.BigDecimal;

/** 公共 DTO：字段与团队约定一致。业务校验由 Service 负责。 */
public record FareQuote(String status, BigDecimal amount, String currency,
                        String ruleVersion, String message) {
}
