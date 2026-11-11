package edu.cs307.metro.service.impl;

import edu.cs307.metro.common.AppException;
import edu.cs307.metro.common.ErrorCode;
import edu.cs307.metro.dao.LineDao;
import edu.cs307.metro.dao.LineStationDao;
import edu.cs307.metro.db.Transactions;
import edu.cs307.metro.model.*;
import edu.cs307.metro.service.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

/** 线路管理和站线增删共用 JDBC 事务与错误处理。 */
public final class JdbcLineService implements LineService {
    private final Transactions transactions;
    private final LineDao lineDao;
    private final LineStationDao lineStationDao;

    public JdbcLineService(Transactions transactions) {
        this(transactions, new LineDao());
    }

    public JdbcLineService(Transactions transactions, LineDao lineDao) {
        this(transactions, lineDao, new LineStationDao());
    }

    public JdbcLineService(Transactions transactions, LineDao lineDao, LineStationDao lineStationDao) {
        this.transactions = Objects.requireNonNull(transactions);
        this.lineDao = Objects.requireNonNull(lineDao);
        this.lineStationDao = Objects.requireNonNull(lineStationDao);
    }

    @Override
    public LineView create(LineDraft input) {
        if (input == null) {
            throw new AppException(ErrorCode.INVALID_INPUT, "请提供线路的中英文名。");
        }
        LineDraft normalized = new LineDraft(
                normalizeName(input.chineseName(), "中文名"),
                normalizeName(input.englishName(), "英文名"));
        return transactions.write(connection -> lineDao.insert(connection, normalized));
    }

    @Override
    public LineView update(long lineId, LineDraft input) {
        if (lineId <= 0) {
            throw new AppException(ErrorCode.INVALID_INPUT, "线路 ID 必须是正整数。");
        }
        if (input == null) {
            throw new AppException(ErrorCode.INVALID_INPUT, "请提供线路的中英文名。");
        }
        LineDraft normalized = new LineDraft(
                normalizeName(input.chineseName(), "中文名"),
                normalizeName(input.englishName(), "英文名"));

        return transactions.write(connection -> {
            int affected = lineDao.updateName(connection, lineId, normalized);
            if (affected == 0) {
                throw new AppException(ErrorCode.NOT_FOUND, "指定线路不存在。");
            }
            if (affected != 1) {
                throw new AppException(ErrorCode.DB_ERROR, "线路更新结果异常。");
            }
            return new LineView(lineId, normalized.chineseName(), normalized.englishName());
        });
    }

    private static String normalizeName(String name, String label) {
        if (name == null || name.strip().isEmpty() || name.indexOf('\0') >= 0) {
            throw new AppException(ErrorCode.INVALID_INPUT, "线路" + label + "不能为空白或包含空字符。");
        }
        return name.strip();
    }

    @Override
    public void delete(long lineId) {
        if (lineId <= 0) {
            throw new AppException(ErrorCode.INVALID_INPUT, "线路 ID 必须是正整数。");
        }
        transactions.delete(connection -> {
            int affected = lineDao.delete(connection, lineId);
            if (affected == 0) {
                throw new AppException(ErrorCode.NOT_FOUND, "指定线路不存在。");
            }
            if (affected != 1) {
                throw new AppException(ErrorCode.DB_ERROR, "线路删除结果异常。");
            }
            return null;
        });
    }

    @Override
    public List<LineView> search(String keyword) {
        String number = keyword == null ? "" : keyword.strip();
        if (!number.matches("[1-9][0-9]*")) {
            throw new AppException(ErrorCode.INVALID_INPUT, "线路编号必须是正整数。");
        }
        return transactions.readSnapshot(connection -> lineDao.search(connection, number));
    }

    @Override
    public List<LineStopView> getStops(long lineId) {
        if (lineId <= 0) {
            throw new AppException(ErrorCode.INVALID_INPUT, "线路 ID 必须是正整数。");
        }
        return transactions.readSnapshot(connection -> {
            if (!lineDao.exists(connection, lineId)) {
                throw new AppException(ErrorCode.NOT_FOUND, "指定线路不存在。");
            }
            return lineStationDao.findByLine(connection, lineId);
        });
    }

    @Override
    public void insertStops(long lineId, int position, List<StopDraft> stops) {
        if (lineId <= 0 || position <= 0) {
            throw new AppException(ErrorCode.INVALID_INPUT, "线路 ID 和插入位置必须是正整数。");
        }
        if (stops == null || stops.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_INPUT, "请提供至少一个待插入的车站。");
        }
        List<StopDraft> normalizedStops = new ArrayList<>();
        Set<Long> stationIds = new HashSet<>();
        for (StopDraft stop : stops) {
            if (stop == null || stop.stationId() <= 0) {
                throw new AppException(ErrorCode.INVALID_INPUT, "请提供有效的车站 ID 和站线资料。");
            }
            String status = stop.status() == null ? "" : stop.status().strip();
            if (!status.equals("Operation") && !status.equals("Construction")) {
                throw new AppException(ErrorCode.INVALID_INPUT, "运营状态必须是 Operation 或 Construction。");
            }
            if (stop.openingYear() <= 0 || stop.openingYear() > Short.MAX_VALUE) {
                throw new AppException(ErrorCode.INVALID_INPUT, "开通年份必须在 1 到 32767 之间。");
            }
            validateSourceCoordinate(stop.sourceLatitude());
            validateSourceCoordinate(stop.sourceLongitude());
            if (!stationIds.add(stop.stationId())) {
                throw new AppException(ErrorCode.DUPLICATE, "待插入列表中包含重复车站。");
            }
            normalizedStops.add(new StopDraft(stop.stationId(), status, stop.openingYear(),
                    stop.startTime(), stop.endTime(), stop.sourceLatitude(), stop.sourceLongitude()));
        }

        transactions.write(connection -> {
            if (!lineDao.lockById(connection, lineId)) {
                throw new AppException(ErrorCode.NOT_FOUND, "指定线路不存在。");
            }
            long count = lineStationDao.countStops(connection, lineId);
            if (position > count + 1) {
                throw new AppException(ErrorCode.INVALID_INPUT, "插入位置必须在 1 到 " + (count + 1) + " 之间。");
            }
            if (count > Integer.MAX_VALUE - normalizedStops.size()) {
                throw new AppException(ErrorCode.INVALID_INPUT, "插入后的站点数量超出支持范围。");
            }
            if (!lineStationDao.isPositionContinuous(connection, lineId)) {
                throw new AppException(ErrorCode.DB_ERROR, "线路现有站序异常。");
            }
            for (StopDraft stop : normalizedStops) {
                if (lineStationDao.containsStation(connection, lineId, stop.stationId())) {
                    throw new AppException(ErrorCode.DUPLICATE, "车站已属于该线路。");
                }
            }
            lineStationDao.shiftForInsertion(connection, lineId, position, normalizedStops.size());
            for (int index = 0; index < normalizedStops.size(); index++) {
                // 外键约束检查车站存在，任一插入失败由整个事务回滚。
                int affected = lineStationDao.insertStop(
                        connection, lineId, position + index, normalizedStops.get(index));
                if (affected != 1) {
                    throw new AppException(ErrorCode.DB_ERROR, "站线关系插入结果异常。");
                }
            }
            if (!lineStationDao.isPositionContinuous(connection, lineId)) {
                throw new AppException(ErrorCode.DB_ERROR, "插入后的线路站序异常。");
            }
            return null;
        });
    }

    private static void validateSourceCoordinate(BigDecimal coordinate) {
        if (coordinate != null && coordinate.setScale(6, RoundingMode.HALF_UP).precision() > 9) {
            throw new AppException(ErrorCode.INVALID_INPUT, "源坐标超出 NUMERIC(9,6) 的存储范围。");
        }
    }

    @Override
    public void updateStop(long lineId, StopDraft stop) {
        throw new UnsupportedOperationException("LineService 的 updateStop 功能待实现");
    }

    @Override
    public void removeStop(long lineId, long stationId) {
       if (lineId<=0||stationId<=0){
           throw new AppException(
                   ErrorCode.INVALID_INPUT,          // 错误码，表示输入不合法
                   "线路 ID 和车站 ID 必须是正整数。"  // 提示信息，解释具体原因
           );
       }else {
           transactions.delete(connection -> {
               // 1. 锁定线路
               boolean exists = lineDao.lockById(connection, lineId);
               if (!exists) {
                   throw new AppException(ErrorCode.NOT_FOUND, "指定线路不存在。");
               }
               // 2. 删除站线关系，取得被删除站点的原位置
               OptionalInt position = lineStationDao.deleteReturningPosition(
                       connection, lineId, stationId);
               if (position.isEmpty()) {
                   throw new AppException(ErrorCode.NOT_FOUND, "该站点未关联此线路。");
               }
               // 3. 后续站点向前补位
               lineStationDao.shiftAfterRemoval(
                       connection, lineId, position.getAsInt());
               // 4. 检查剩余站序连续，空线也应通过
               if (!lineStationDao.isPositionContinuous(connection, lineId)) {
                   throw new AppException(ErrorCode.DB_ERROR, "线路站序异常。");
               }
               return null;
           });

       }
    }
}
