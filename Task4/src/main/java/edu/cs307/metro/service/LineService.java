package edu.cs307.metro.service;

import edu.cs307.metro.model.*;
import java.util.List;

/** 成功返回 DTO，业务失败抛 AppException；查询列表无匹配时返回空列表。 */
public interface LineService {
    LineView create(LineDraft input);
    /** lineId 为交互层按线路编号查询并选定的记录的内部 ID。 */
    LineView update(long lineId, LineDraft input);
    /** lineId 为交互层按线路编号查询并选定的记录的内部 ID。 */
    void delete(long lineId);
    /**
     * 基础 2 线路查询，keyword 为线路名称中的编号，例如 "13" 或 "1"。
     * 去除首尾空白后接受无前导零的 ASCII 正整数，否则抛 INVALID_INPUT。
     * 按完整编号匹配，返回同编号的全部线路，包含分期、延伸段和支线。
     * 例如 "13" 匹配 Line 13 及其 Phase 2 延伸段，"1" 仅匹配编号为 1 的线路。
     * 结果中的 lineId 取数据库内部 ID。无匹配时返回空列表。
     */
    List<LineView> search(String keyword);
    /** 按站序返回全部站点及站线属性；空线返回空列表，线路不存在返回 NOT_FOUND。 */
    List<LineStopView> getStops(long lineId);
    void insertStops(long lineId, int position, List<StopDraft> stops);
    void updateStop(long lineId, StopDraft stop);
    void removeStop(long lineId, long stationId);
}
