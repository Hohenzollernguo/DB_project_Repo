package edu.cs307.metro.service.impl;

import edu.cs307.metro.common.AppException;
import edu.cs307.metro.common.ErrorCode;
import edu.cs307.metro.dao.LineDao;
import edu.cs307.metro.db.Transactions;
import edu.cs307.metro.model.*;
import edu.cs307.metro.service.*;
import java.util.List;
import java.util.Objects;

/** 线路添加、名称修改和删除共用 JDBC 事务与错误处理。 */
public final class JdbcLineService implements LineService {
    private final Transactions transactions;
    private final LineDao lineDao;

    public JdbcLineService(Transactions transactions) {
        this(transactions, new LineDao());
    }

    public JdbcLineService(Transactions transactions, LineDao lineDao) {
        this.transactions = Objects.requireNonNull(transactions);
        this.lineDao = Objects.requireNonNull(lineDao);
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
        throw new UnsupportedOperationException("LineService 的 getStops 功能待实现");
    }

    @Override
    public void insertStops(long lineId, int position, List<StopDraft> stops) {
        throw new UnsupportedOperationException("LineService 的 insertStops 功能待实现");
    }

    @Override
    public void updateStop(long lineId, StopDraft stop) {
        throw new UnsupportedOperationException("LineService 的 updateStop 功能待实现");
    }

    @Override
    public void removeStop(long lineId, long stationId) {
        throw new UnsupportedOperationException("LineService 的 removeStop 功能待实现");
    }
}
