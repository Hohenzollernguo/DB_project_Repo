package edu.cs307.metro.service.impl;

import edu.cs307.metro.common.AppException;
import edu.cs307.metro.common.ErrorCode;
import edu.cs307.metro.dao.StationDao;
import edu.cs307.metro.db.Transactions;
import edu.cs307.metro.model.*;
import edu.cs307.metro.service.*;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** 开发骨架：实现规则见架构说明。完成业务前明确失败，不返回伪造成功结果。 */
public final class JdbcStationService implements StationService {
    protected final Transactions transactions;
    private final StationDao stationDao = new StationDao();

    public JdbcStationService(Transactions transactions) {
        this.transactions = Objects.requireNonNull(transactions);
    }

    @Override
    public StationView create(StationDraft input) {

        StationDraft draft = validateAndNormalize(input);

        return transactions.write(connection -> {
            String districtName = draft.districtName();
            OptionalLong districtIdResult = stationDao.findDistrictId(connection, districtName);

            if (districtIdResult.isEmpty()) {
                throw new AppException(ErrorCode.NOT_FOUND, "未找到行政区：" + districtName);
            }

            long districtId = districtIdResult.getAsLong();

            long stationId = stationDao.insert(connection, draft, districtId);

            StationKey stationKey = new StationKey(draft.chineseName(), districtName);
            String englishName = draft.englishName();
            String state = "UNASSIGNED";

            StationView createdStation = new StationView(stationId, stationKey, englishName, state);
            return createdStation;
        });
    }

    @Override
    public StationView update(long stationId, StationDraft input) {
        if (stationId <= 0) {
            throw new AppException(ErrorCode.INVALID_INPUT, "车站 ID 必须大于 0。");
        }
        StationDraft draft = validateAndNormalize(input);
        return transactions.write(connection -> {
            String districtName = draft.districtName();
            OptionalLong districtIdResult = stationDao.findDistrictId(connection, districtName);

            if (districtIdResult.isEmpty()) {
                throw new AppException(ErrorCode.NOT_FOUND, "未找到行政区：" + districtName);
            }

            long districtId = districtIdResult.getAsLong();
            int updatedRows = stationDao.update(connection, stationId, draft, districtId);

            if (updatedRows == 0) {
                throw new AppException(ErrorCode.NOT_FOUND, "未找到车站，ID：" + stationId);
            }

            Optional<StationView> updatedStationResult = stationDao.findById(connection, stationId);

            if (updatedStationResult.isEmpty()) {
                throw new AppException(ErrorCode.DB_ERROR, "修改后无法读取车站，ID：" + stationId);
            }

            StationView updatedStation = updatedStationResult.get();
            return updatedStation;
        });
    }

    @Override
    public void delete(long stationId) {
        if (stationId <= 0) {
            throw new AppException(ErrorCode.INVALID_INPUT, "车站 ID 必须大于 0。");
        }

        transactions.delete(connection -> {
            boolean hasLineAssociation = stationDao.hasLineAssociation(connection, stationId);

            if (hasLineAssociation) {
                throw new AppException(ErrorCode.IN_USE, "车站仍关联线路，请先从线路中移除。");
            }

            stationDao.deleteExitLandmarkLinks(connection, stationId);
            stationDao.deleteExits(connection, stationId);

            int deletedRows = stationDao.deleteStation(connection, stationId);
            if (deletedRows == 0) {
                throw new AppException(ErrorCode.NOT_FOUND, "未找到车站，ID：" + stationId);
            }

            return null;
        });
    }

    @Override
    public List<StationView> search(String keyword) {
        if (keyword == null) {
            throw new AppException(ErrorCode.INVALID_INPUT, "请提供查询关键词。");
        }

        String searchKeyword = keyword.strip();

        return transactions.readSnapshot(connection -> {
            List<StationView> stations = stationDao.search(connection, searchKeyword);
            return stations;
        });
    }

    private StationDraft validateAndNormalize(StationDraft input) {
        if (input == null) {
            throw new AppException(ErrorCode.INVALID_INPUT, "请填写车站资料。");
        }

        if (input.chineseName() == null || input.chineseName().isBlank()) {
            throw new AppException(ErrorCode.INVALID_INPUT, "车站中文名不能为空。");
        }
        if (input.englishName() == null || input.englishName().isBlank()) {
            throw new AppException(ErrorCode.INVALID_INPUT, "车站英文名不能为空。");
        }

        if (input.districtName() == null || input.districtName().isBlank()) {
            throw new AppException(ErrorCode.INVALID_INPUT, "行政区不能为空。");
        }

        return new StationDraft(
                input.chineseName().strip(),
                input.englishName().strip(),
                input.districtName().strip()
        );
    }
}
