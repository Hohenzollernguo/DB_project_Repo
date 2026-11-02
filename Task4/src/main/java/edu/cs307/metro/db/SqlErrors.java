package edu.cs307.metro.db;

import edu.cs307.metro.common.AppException;
import edu.cs307.metro.common.ErrorCode;
import java.sql.SQLException;

public final class SqlErrors {
    private SqlErrors() {}

    public static AppException translate(SQLException exception, SqlOperation operation) {
        SQLException detail = exception;
        while (detail.getNextException() != null) {
            detail = detail.getNextException();
        }
        String state = detail.getSQLState();
        ErrorCode code = switch (state == null ? "" : state) {
            case "23505" -> ErrorCode.DUPLICATE;
            case "23503" -> operation == SqlOperation.DELETE ? ErrorCode.IN_USE : ErrorCode.NOT_FOUND;
            case "23502", "23514", "22023" -> ErrorCode.INVALID_INPUT;
            case "P0002" -> ErrorCode.NOT_FOUND;
            case "P0003" -> ErrorCode.AMBIGUOUS_NAME;
            default -> ErrorCode.DB_ERROR;
        };
        String message = switch (code) {
            case DUPLICATE -> "名称或关联记录已存在。";
            case IN_USE -> "该记录仍被使用，请先移除相关关联。";
            case NOT_FOUND -> "目标记录或引用的记录不存在。";
            case INVALID_INPUT -> "输入不符合数据要求。";
            case AMBIGUOUS_NAME -> "名称对应多个对象，请进一步指定。";
            default -> "数据库操作失败，请检查连接与本地调试信息。";
        };
        return new AppException(code, message, exception);
    }
}
