package edu.cs307.metro.model;

/** 公共 DTO：字段与团队约定一致。业务校验由 Service 负责。 */
public record LineView(long lineId, String chineseName, String englishName) {
    public String number() {
        return LineNumber.of(englishName);
    }
}
