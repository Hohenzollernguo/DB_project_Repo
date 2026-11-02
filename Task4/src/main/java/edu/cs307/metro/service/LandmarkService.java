package edu.cs307.metro.service;

import edu.cs307.metro.model.*;
import java.util.List;

/** 成功返回 DTO，业务失败抛 AppException；查询列表无匹配时返回空列表。 */
public interface LandmarkService {
    List<LandmarkHit> searchPrefix(String literalPrefix);
    LandmarkRoute findRoute(String fromName, String toName);
}
