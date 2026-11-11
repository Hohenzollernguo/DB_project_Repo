package edu.cs307.metro.ui;

import edu.cs307.metro.config.DbConfig;
import edu.cs307.metro.db.DriverManagerConnectionFactory;
import edu.cs307.metro.db.Transactions;
import edu.cs307.metro.model.LineView;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/** 供界面展示完整线路列表，不改变线路服务按编号查询的约定。 */
final class LineCatalog {
    private LineCatalog() {
    }

    static List<LineView> listAll() {
        Transactions transactions = new Transactions(new DriverManagerConnectionFactory(DbConfig::fromEnvironment));
        return transactions.readSnapshot(connection -> {
            String sql = """
                    select line_id, chinese_name, english_name
                    from public.metro_line
                    order by line_id
                    """;
            List<LineView> lines = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    lines.add(new LineView(rows.getLong("line_id"), rows.getString("chinese_name"),
                            rows.getString("english_name")));
                }
            }
            return lines;
        });
    }
}
