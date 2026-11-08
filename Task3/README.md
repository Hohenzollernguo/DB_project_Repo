# Task3：数据导入与 SQL 查询

## 文件职责

- `schema.sql`：队友写好的七张表、约束和索引，原根目录 create_table.sql 移到这里，内容不变。
- `tools/ImportSqlGenerator.java`：AI 辅助编写的 Java 17 工具，读取两份 CSV 和 schema.sql，生成 import.sql；不连接数据库。
- `import.sql`：自动生成的完整导入脚本，包含清理、建表、全部 INSERT 和序列更新。运行它不需要 Java、CSV 或其他 SQL 文件。
- `queries.sql`：合并原 Task3_1356.sql 和 Task3_247.sql，按 Q1～Q7 排列。A 负责 Q1/Q3/Q5/Q6，B 负责 Q2/Q4/Q7；文件内标注来源。
- `verify.sql`：导入后的只读检查，不依赖 Q7。
- `pom.xml`：IDEA/Maven 编译配置；target 为编译输出。

## 使用顺序

1. 在 DataGrip 打开 import.sql，绑定自己的 prj1 数据库，执行完整文件（BEGIN 到 COMMIT）。
2. 注意：import.sql 会删除并重建七张项目表，覆盖其中已有数据；不删除数据库，不清空整个 public schema。
3. 遇到错误停止，执行 ROLLBACK，查看第一条错误。
4. 执行 verify.sql 检查数据。
5. 在 queries.sql 中按题执行；Q4/Q5 使用示例线路 Line 1，Q6 使用地标模式 塘朗%，可以修改。
6. Q7 先完整执行 CREATE OR REPLACE FUNCTION 定义（到 $$;），再执行 SELECT 调用。Task4 使用 Q7 前也需要在对应数据库创建它。

import.sql 只重建表和数据，不创建、更新或主动删除 Q7 函数。每次准备 Q7 时执行 queries.sql 中的函数定义即可。

预期数量：district=10、station=439、metro_line=29、line_station=567、station_exit=1276、landmark=2872、exit_landmark=3203。

## 重新生成 import.sql

在 IDEA 运行 ImportSqlGenerator.main，程序实参填仓库根目录（包含 Task3 和 官方描述及数据 的目录），路径有空格时加双引号。

或者在仓库根目录用 PowerShell 执行（Java 17 或更新版本）：

```powershell
java --source 17 '-Dfile.encoding=UTF-8' Task3/tools/ImportSqlGenerator.java .
```

输入为两份课程 CSV 和 Task3/schema.sql，输出为 Task3/import.sql。生成器不再读取查询文件，也不提取或修改 Q7。
修改表结构请改 schema.sql；修改数据整理逻辑请改 Java；之后重新生成。不要手工修改生成的 INSERT。

## 队友代码与协作

- 原 create_table.sql 已移至 Task3/schema.sql。
- 原 Task3_247.sql 和 Task3/Task3_1356.sql 已合并到 Task3/queries.sql，后续在新位置修改。
- Q4 将 DataGrip 参数占位符改为可直接执行的示例线路名。
- Q7 基于队友代码，保留之前 AI 辅助适配：SQLSTATE 22023/P0002/P0003、固定 search_path、bigint 站序运算。请 B 核对这些适配；现在直接写在 queries.sql 中，不再由 Java 替换函数文本。

## 数据处理约定

- 固定课程 CSV 没有引号或字段内英文逗号，按英文逗号拆分并保留空字段，检查表头和列数。新格式出现引号时明确报错；更换格式后需完整 CSV 解析器。
- 名称 trim；车站以中文名和行政区共同识别，英文名不全局唯一。
- 地标按中文逗号拆分，保留斜杠，去空并去重；没有地标的出口仍保留。
- 梨园出口归 Longgang 是团队推断，报告需披露。
- 坐标保持原始字段和值；空时间和坐标写 NULL。
- 检查站序、重复关系、数值、时间和数量基线。
- 每批最多 500 行 INSERT，全文件在一个事务中执行；显式 ID 导入后更新五个自增序列。

## 验证记录

整理前曾在独立 PostgreSQL 18 临时实例验证：连续导入两次成功；七表数据与 CSV 双向比较一致；五个自增序列新增成功；Q1/Q3/Q5/Q6 及 Q7 正常和异常输入通过。未修改用户 prj1。
本次整理已验证：Java 17 目标编译通过、重新生成成功、七表数量基线通过；与整理前逐字比较全部 INSERT 批次、建表语句和 Q7 定义一致；Q1～Q7 均已收录；git diff --check 通过。本次没有连接数据库或重新执行数据库测试。

## 报告材料

如实列出生成器、schema.sql、queries.sql、最终 import.sql、实际使用的工具和 AI，填写实际组员姓名、用途、运行方式及 AI 辅助范围。
