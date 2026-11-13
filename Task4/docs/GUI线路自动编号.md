# GUI 线路自动编号

GUI 原先允许新增任意英文名，但后续站线管理按英文名中的 `Line N` 编号查询。新增 `Demo` 后只有内部 ID，无法通过编号找到。

## 保存和显示

- GUI 新增线路使用 `LineService.createNumbered(LineDraft)`，取当前所有可识别线路的最大编号加 1。没有可识别编号时从 1 开始。
- 编号按数字比较，包含分期、延伸段和支线的主编号。例如 `Line 13 Phase 2` 的编号为 13，忽略 `Phase 2` 的 2；不使用 `line_id` 或线路总数分配编号。
- 中文名保留用户输入，英文名保存为 `Line N 用户输入的英文名称`。如果输入已带 `Line N` 前缀，替换该前缀，避免重复添加。例如当前最大编号 32，输入 `Demo` 或 `Line 99 Demo` 都保存为 `Line 33 Demo`；输入 `Line 99` 则保存为 `Line 33`。
- 线路管理表新增“编号”列，成功提示同时显示编号和内部 ID。用户在站线管理中输入新编号并选择线路候选，后续操作使用该记录的内部 ID。
- 当前主 GUI 修改已有编号线路的名称时保留原编号前缀。历史上无编号的英文名在列表中显示“—”。

## 接口与兼容性

本说明是原 JDBC 开发约定的接口增补。`LineService.create(LineDraft)` 保持按输入名称创建的语义，CLI 继续调用它。两个 Swing 新增入口（`MetroUiPreview` 与旧 `LineCreatePanel`）调用新增的 `createNumbered`，仍由 Service 校验名称、管理事务并调用 DAO。

`LineView` 保持原有三个 record 字段和构造参数，增加派生访问方法 `number()`。没有匹配 `Line 正整数` 前缀时返回空字符串；它不是数据库新列。`LineNumber` 统一提取和写入前缀，识别规则与现有 `LineDao.search` 的完整编号查询一致。

七张表的结构、`Task3/tools/create_table.sql`、`Task3/src/import.sql` 和 Q7 函数均不需要变更。已有数据库升级代码后可直接使用，无需重新导入。

## 事务

分配编号与插入在同一个 `Transactions.write` 中执行。`LineDao.nextNumber` 先取得 `metro_line` 的 `SHARE ROW EXCLUSIVE` 表锁，再读取现有编号；锁保持到提交或回滚，阻止另一事务同时写入。普通查询仍可进行。连续并发新增会获得不同编号，名称冲突回滚后不会消耗线路编号。数据库身份序列仍可能因回滚产生 ID 空缺。

## 验证

- `NumberedLineCreateTest`：数字最大值、分期、忽略无关数字和内部 ID、空库、替换已有前缀、大编号、输入校验及失败回滚。
- `LineCreatePanelTest`：后台提交、防止重复点击、显示编号与 ID、错误后保留输入。
- `GuiNumberedLinePostgresTest`：在独立数据库完整运行实际导入 SQL，通过真实线路管理页面新增 33 和 34、检查编号列和成功提示、按 33 查询、插入和移除车站；另验证重新装配服务后仍能查询、六路并发编号互不重复及重名回滚。

实库测试使用现有 `METRO_TEST_DB_*` 配置和 `PostgresTestDatabase` 隔离机制，不修改配置指向的管理数据库中的项目表。

2026-11-13 在独立 PostgreSQL 12.1 实例完成 Maven `verify`：308 项测试，0 失败、0 错误、0 跳过。使用 JDK 21 按 Java 17 目标编译；本次未单独在 Java 17 运行时复测。
