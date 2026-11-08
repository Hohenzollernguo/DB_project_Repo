# Task 3：正式数据导入（学生 A）

## 文件与职责

- `tools/ImportSqlGenerator.java`：Java 17 生成工具，无第三方依赖，不连接数据库。
- `import.sql`：已生成、可独立执行的最终导入 SQL。运行它不需要 Java、CSV 或其他 SQL 文件。
- `Task3_1356.sql`：A 负责的 Q1、Q3、Q5、Q6；修改示例线路名/地标模式后单独执行相应查询。
- `verify.sql`：导入后的只读检查。
- `pom.xml`：IDEA/Maven 工程配置；也可以直接使用 JDK 运行生成器。

## 在 DataGrip 使用最终 SQL

1. 打开 `import.sql`，绑定自己的 `prj1` 数据源，确认没有连到实验课或队友数据库。
2. **此脚本会删除并重建七张项目表和 Q7 函数，覆盖其中的已有数据。** 不删除数据库或清空整个 public schema。
3. 选择并执行完整文件（包含 BEGIN 和 COMMIT），不要只执行当前光标所在语句。
4. 遇到错误时停止，执行 ROLLBACK，再查看第一条错误；不要跳过错误继续提交。
5. 成功后运行 `verify.sql`、`Task3_1356.sql`。再次完整执行 `import.sql` 应仍成功。

七表预期数量：district=10、station=439、metro_line=29、line_station=567、station_exit=1276、landmark=2872、exit_landmark=3203。

## 重新生成 import.sql

在 IDEA 运行 `ImportSqlGenerator.main`；程序实参填写自己的仓库根目录，带空格时加双引号。
例如 `"D:\我的笔记\Sustech CS\数据库\prj\DB_project_Repo"`。
也可以在仓库根目录执行（Java 17 或更新版本）：

```powershell
java --source 17 -Dfile.encoding=UTF-8 Task3/tools/ImportSqlGenerator.java .
```

PowerShell 若对 `-D` 参数分词有问题，将它写成 `'-Dfile.encoding=UTF-8'`。

生成器从当前仓库读取两份 CSV、`create_table.sql` 和 `Task3_247.sql`，写出 `Task3/import.sql`。
不要手工修改生成的 INSERT；修改生成器或共享源文件后重新生成。

## 复用队友的内容与需同步的改动

- 七表 DDL 和索引直接来自根目录 `create_table.sql`，没有另建第二套表结构。
- Q7 从根目录 `Task3_247.sql` 提取，排除 Q2/Q4、DataGrip 参数和演示 SELECT。
- 导出的 Q7 适配团队已约定的 SQLSTATE：无效 n 为 22023，缺失目标为 P0002，英文名歧义为 P0003；固定函数 search_path，并使用 bigint 运算避免极大 n 导致整数溢出。**原 `Task3_247.sql` 未改；需 B 核对并同步这些改进。** 源函数结构变化时，应一起检查生成器适配代码。

## 数据处理与导入速度

- 仅针对当前两份经检查的课程 CSV：没有引号或字段内英文逗号，按行和英文逗号读取，保留空字段；接受 UTF-8 BOM。检查列名、列数，出现引号时明确失败。此简化读取方式替代旧协作 PDF 中的通用 CSV 解析器建议，需双方知悉；更换数据格式时应改用完整解析器。
- 名称只 trim，不改大小写或内部标点；车站以中文名+行政区识别，英文名不全局唯一。
- 地标仅按中文逗号拆分，去空并去重；无地标的出口仍保留。
- 梨园出口明确归 Longgang，这是团队根据数据作出的推断，报告应披露。
- 坐标保留原 CSV 字段和值；空时间/坐标写 NULL，不作里程或票价推断。
- 校验站序、同键名称、数值、时间及关系；不一致时停止，不静默丢数据。
- 每批最多 500 行 INSERT，全部属于一个事务；显式 ID 导入后推进五个 IDENTITY 序列。

## 已执行验证

在独立的本地 PostgreSQL 18 临时实例验证，未连接或修改用户的 prj1：

- 按 Java 17 目标编译成功，生成器执行成功。
- 同一测试库连续完整执行 import.sql 两次均成功。
- 七表数量及每条线路站序连续性正确。
- 用 PostgreSQL CSV 解析独立读取原始文件，按业务键对七类完整逻辑数据双向 EXCEPT ALL 比较，包含属性、NULL 和关系，全部一致。
- Q1=439；Q3 分区数量符合团队基线；Q5 Line 1 返回 Chegongmiao 和 Shenzhen University，各10个出口；Q6 塘朗% 返回6条。
- Q7 Mawan / Line 5 / 2 返回 Liwan、Qianwan；n=0、极大 n、负数、缺失目标验证通过。
- 五个带 IDENTITY 的表使用默认 ID 新增成功，测试新增事务回滚。

上述验证不是 Task4 CRUD 或界面的功能验收。

## Task 3.1 报告材料

如实列出本生成器、共享 DDL、最终 import.sql、队友临时导入工具以及实际用过的 AI。作者填写实际组员姓名，描述用途、运行方式和 AI 辅助范围，不虚构作者或模型名称。
