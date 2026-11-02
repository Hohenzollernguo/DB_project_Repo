package edu.cs307.metro.common;

import java.util.Objects;

/** message 可向用户展示；cause 仅用于本地调试，避免把原始数据库详情显示到界面。 */
public final class AppException extends RuntimeException {
    private final ErrorCode code;

    public AppException(ErrorCode code, String message) {
        this(code, message, null);
    }

    public AppException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code);
    }

    public ErrorCode code() {
        return code;
    }
}
