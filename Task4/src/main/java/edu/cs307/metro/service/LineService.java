package edu.cs307.metro.service;

import edu.cs307.metro.model.*;
import java.util.List;

/** 成功返回 DTO，业务失败抛 AppException；查询列表无匹配时返回空列表。 */
public interface LineService {
    LineView create(LineDraft input);
    LineView update(long lineId, LineDraft input);
    void delete(long lineId);
    List<LineView> search(String keyword);
    List<LineStopView> getStops(long lineId);
    void insertStops(long lineId, int position, List<StopDraft> stops);
    void updateStop(long lineId, StopDraft stop);
    void removeStop(long lineId, long stationId);
}
