package edu.cs307.metro.model;

import java.util.regex.Pattern;

/** 与按编号查询一致：编号来自英文名开头的 Line N，分期和延伸段共用编号。 */
public final class LineNumber {
    private static final Pattern PREFIX = Pattern.compile("^Line ([1-9][0-9]*)(?: |$)");

    private LineNumber() {}

    /** 没有符合查询约定的编号时返回空字符串。 */
    public static String of(String englishName) {
        var match = PREFIX.matcher(englishName);
        return match.find() ? match.group(1) : "";
    }

    /** 已填写 Line N 时替换旧前缀，保留其后的英文名称。 */
    public static String apply(String number, String englishName) {
        var match = PREFIX.matcher(englishName);
        String name = match.find() ? englishName.substring(match.end()).strip() : englishName;
        return "Line " + number + (name.isEmpty() ? "" : " " + name);
    }
}
