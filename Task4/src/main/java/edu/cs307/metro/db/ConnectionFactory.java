package edu.cs307.metro.db;

import java.sql.Connection;
import java.sql.SQLException;

/** 每次 open 返回新连接，由调用者关闭；禁止跨请求或跨线程共享 Connection。 */
@FunctionalInterface
public interface ConnectionFactory {
    Connection open() throws SQLException;
}
