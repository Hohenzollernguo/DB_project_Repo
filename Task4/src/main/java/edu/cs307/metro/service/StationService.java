package edu.cs307.metro.service;

import edu.cs307.metro.model.*;
import java.util.List;

/** 成功返回 DTO，业务失败抛 AppException；查询列表无匹配时返回空列表。 */
public interface StationService {
    StationView create(StationDraft input);
    StationView update(long stationId, StationDraft input);
    void delete(long stationId);
    List<StationView> search(String keyword);
}
