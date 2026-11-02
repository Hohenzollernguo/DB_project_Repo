package edu.cs307.metro.db;

import java.sql.Connection;
import java.sql.SQLException;

/** 工作函数可调用多个 DAO，但不可 commit、rollback 或关闭传入的连接。 */
@FunctionalInterface
public interface SqlWork<T> {
    T run(Connection connection) throws SQLException;
}
