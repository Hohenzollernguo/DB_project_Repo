package edu.cs307.metro.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/** Service 调用的事务边界：每次请求独立连接，提交失败也执行回滚。 */
public final class Transactions {
    private final ConnectionFactory connections;

    public Transactions(ConnectionFactory connections) {
        this.connections = Objects.requireNonNull(connections);
    }

    public <T> T write(SqlWork<T> work) {
        return execute(SqlOperation.WRITE, false, work);
    }

    public <T> T delete(SqlWork<T> work) {
        return execute(SqlOperation.DELETE, false, work);
    }

    /** 路径与多候选地标路线在同一只读快照内加载拓扑。 */
    public <T> T readSnapshot(SqlWork<T> work) {
        return execute(SqlOperation.READ, true, work);
    }

    private <T> T execute(SqlOperation operation, boolean snapshot, SqlWork<T> work) {
        try (Connection connection = connections.open()) {
            if (snapshot) {
                connection.setReadOnly(true);
                connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            }
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException | Error failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            }
        } catch (SQLException failure) {
            throw SqlErrors.translate(failure, operation);
        }
    }
}
