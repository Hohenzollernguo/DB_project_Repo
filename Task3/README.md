# Task3：数据导入和查询

## 完成内容

- 用 Java 读取老师提供的两份 CSV，整理车站、线路、出口和地标等数据，生成包含建表和批量插入语句的 [src/import.sql](src/import.sql)。生成器的开发使用了 AI 辅助。
- 合并 Q1～Q7 到 [src/queries.sql](src/queries.sql)：A 负责 Q1/Q3/Q5/Q6，B 负责 Q2/Q4/Q7。
- Task 3.1 的脚本和 AI 工具记录见 [src/Task3_1_Import_Tools.pdf](src/Task3_1_Import_Tools.pdf)。

## 对原有文件的整理

- 队友的建表文件 `create_table.sql` 移到了 `Task3/tools/create_table.sql`，建表内容未改；生成器从新位置读取。
- 原 `Task3_247.sql` 与 A 的查询文件合并到 `src/queries.sql`。Q4 保留原来的 `line_name` 参数占位符。
- Q7 函数定义放在 `src/queries.sql`。2026-11-10 接入 Task4 命令行时，对齐了函数错误码并使用 bigint 计算站序偏移，保留前后两行、越界返回 null 和 n=0 返回本站的行为。更新旧数据库时重新执行完整函数定义即可。
- 梨园出口归属龙岗区是根据数据作出的推断，请队友确认；原始坐标字段和值保持不变。

## 使用和修改

1. 在 DataGrip 连接自己的 PostgreSQL 数据库，完整执行 `src/import.sql`。**脚本会删除并重建七张项目表，覆盖其中已有数据。**
2. 按题执行 `src/queries.sql`；Q4 运行时输入线路名，Q5 的线路名和 Q6 的地标模式可以修改。Q7 先执行完整函数定义，再执行调用。
3. 要重新生成 `src/import.sql`，在 IDEA 中运行 `tools/ImportSqlGenerator.java`，程序实参填写仓库根目录（路径含空格时加双引号）。也可在仓库根目录使用 Java 17 或更新版本运行：

   ```powershell
   java --source 17 '-Dfile.encoding=UTF-8' Task3/tools/ImportSqlGenerator.java .
   ```

修改表结构请改 `tools/create_table.sql`，修改数据处理请改生成器，然后重新生成 `src/import.sql`；生成器不会连接数据库。
