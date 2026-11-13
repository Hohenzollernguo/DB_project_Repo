package edu.cs307.metro.dao;

import edu.cs307.metro.model.LineDraft;
import edu.cs307.metro.model.LineView;
import edu.cs307.metro.model.LineNumber;

import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class LineDao {
    /** 调用方必须开启事务；锁覆盖读取最大编号到插入提交，阻止并发写入抢占编号。 */
    public String nextNumber(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute("LOCK TABLE public.metro_line IN SHARE ROW EXCLUSIVE MODE");
            BigInteger maximum = BigInteger.ZERO;
            try (ResultSet rows = statement.executeQuery("SELECT english_name FROM public.metro_line")) {
                while (rows.next()) {
                    String number = LineNumber.of(rows.getString("english_name"));
                    if (!number.isEmpty()) maximum = maximum.max(new BigInteger(number));
                }
            }
            return maximum.add(BigInteger.ONE).toString();
        }
    }

    public boolean exists(Connection connection, long lineId) throws SQLException {
        String sql = "SELECT 1 FROM public.metro_line WHERE line_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, lineId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    /** 新线路由数据库分配 ID，返回已写入的完整线路资料。 */
    public LineView insert(Connection connection, LineDraft input) throws SQLException {
        String sql = """
                INSERT INTO public.metro_line (chinese_name, english_name)
                VALUES (?, ?)
                RETURNING line_id, chinese_name, english_name
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, input.chineseName());
            statement.setString(2, input.englishName());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException("Line insert returned no row");
                }
                return new LineView(rows.getLong("line_id"),
                        rows.getString("chinese_name"), rows.getString("english_name"));
            }
        }
    }

    /** 返回影响行数；调用方负责判断目标是否存在以及管理事务。 */
    public int updateName(
            Connection connection,
            long lineId,
            LineDraft input
    ) throws SQLException {
        String sql = """
                UPDATE public.metro_line
                SET chinese_name = ?, english_name = ?
                WHERE line_id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, input.chineseName());
            statement.setString(2, input.englishName());
            statement.setLong(3, lineId);

            return statement.executeUpdate();
        }
    }

    /** 外键约束拒绝删除仍关联车站的线路；调用方检查影响行数并管理事务。 */
    public int delete(Connection connection, long lineId) throws SQLException {
        String sql = "DELETE FROM public.metro_line WHERE line_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, lineId);
            return statement.executeUpdate();
        }
    }


    /**
     * 交互层先按编号查询候选，单条自动选定，多条由用户选择 ID，再传入选定记录的内部 lineId。
     * 调用方须已开启事务，并用同一连接完成后续操作；锁持续到事务提交或回滚。
     * 返回是否找到并锁定线路，事务由调用方管理。
     */
    public boolean lockById(Connection connection, long lineId) throws SQLException {
        String sql = """
                SELECT line_id
                FROM public.metro_line
                WHERE line_id = ?
                FOR UPDATE
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, lineId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    public List<LineView> search(Connection connection, String keyword)
            throws SQLException {
        String sql = """
                SELECT * FROM public.metro_line WHERE english_name LIKE 'Line '||?||' %' OR english_name='Line '||?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1,keyword);
            statement.setString(2,keyword);

            try (ResultSet rows = statement.executeQuery()) {
                List<LineView> lines = new ArrayList<>();
                while (rows.next()) {
                    lines.add(new LineView(rows.getLong("line_id"),
                            rows.getString("chinese_name"), rows.getString("english_name")));
                }
                return lines;
            }
        }
    }
}
