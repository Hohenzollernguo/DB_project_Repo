# Task4 开发骨架

Java + JDBC 地铁管理程序，供 A、B 按统一接口协作开发。共享时建议将本模块放在仓库根目录下的 `Task4/`，保留内部结构。

- [架构与目录说明](Task4架构与目录说明.md)：职责、接口、分工、规则及开发顺序。
- [架构审阅与验证记录](docs/架构审阅与验证记录.md)：要求覆盖、验证结果与待完成事项。
- [线路更新实现与验证](docs/线路更新实现与验证.md)：线路修改的调用链、补充工作、验收和数据库测试方式。
- [线路添加删除验收](docs/线路添加删除验收.md) 记录添加和删除的实现、入口与实库验收结果。
- [路线预计算](docs/路线预计算.md)：使用 `--precompute-routes <CSV路径> <JSON路径>` 生成每对可达站点的一条最优路线，包含算法、格式和验证方式。
- `pom.xml`：在 IDEA 中以 Maven 项目打开。
- `src/main/java/edu/cs307/metro/Main.java`：统一入口；默认 `--cli`，也支持 `--gui`、`--check-db`、`--help`。

## Git 同步范围

| 应同步 | 用途 |
|---|---|
| `pom.xml` | 统一依赖和编译配置 |
| `src/main/` | 双方共用的程序代码 |
| `src/test/` | 可重复运行的自动测试 |
| `README.md`、`Task4架构与目录说明.md`、`docs/` | 团队开发说明与验证依据 |
| `config/db.env.example` | 无真实凭据的配置模板 |
| `scripts/mvn-local.ps1` | 不依赖固定安装位置的构建辅助脚本 |
| `.gitignore` | 排除工具产物和真实配置 |

以下内容不提交：`target/`、`.maven-cache/`、`.idea/`、`*.iml`、真实 `.env` 或 `config/db.env`。这些文件由各自环境生成或配置，已加入忽略规则。个人进度、笔记和机器专用脚本应放在仓库之外。

## 当前能做什么

当前提供线路添加、名称修改、空线路删除和数据库连接诊断。CLI 选择 `9` 添加线路、`2` 修改名称、`10` 删除线路。Swing 提供“添加线路”“修改线路名称”“删除线路”三个按钮。

2026-11-10 已接通 CLI 主菜单 `5` 两站路径查询。按站名搜索并选择起终点 ID 后，Java 使用预计算 JSON 索引返回最优路线，显示乘坐线路、途经站点、换乘位置和统计。老师明确允许该功能始终按固定缓存查询，前面的数据库修改不影响路线结果。共享 `RouteService` 同时支持多候选站点最优查询，供地标功能复用。

添加时由数据库生成 ID，中英文名分别唯一，名称自动去除首尾空白。修改和删除先输入线路编号，例如 `13`，程序查询该编号对应的线路。只有一条时自动选定，多条时列出每条线路的 ID 和中英文名，由用户选择具体线路。CLI 删除还需输入 `YES` 确认，Swing 删除还需勾选确认。有关联站点的线路返回 `IN_USE`，已选目标不存在时返回 `NOT_FOUND`。数据库请求统一经过 Service 和 Transactions，Swing 在后台线程执行请求。

CLI 多候选时输入列表中的 ID，留空取消本次操作。未查到线路、编号格式无效或选择了列表外的 ID 时，程序给出提示并返回菜单。Swing 先点击“查询线路”，多候选时在显示 ID 和名称的列表中选择，再修改名称或确认删除。更改编号后需要重新查询并选择目标。

## 基础 2 线路查询约定

2026-11-09 确认的团队开发规则，供后续实现和验收使用。

- 执行线路查询时输入线路编号，例如 `13` 或 `1`。按线路名称中的完整编号匹配，返回该编号对应的全部线路记录，包括同编号的分期、延伸段或支线记录。
- 输入 `13` 时，查询 `Line 13`、`Line 13 Phase 2 North Extension`、`Line 13 Phase 2 South Extension` 等编号为 13 的线路。
- 输入 `1` 时，只查询编号为 1 的 `Line 1` 对应记录。`Line 10`、`Line 11`、`Line 12`、`Line 13` 等线路不属于查询结果。
- 查询编号取自线路名称中的编号。修改、删除入口使用该编号查询候选，选定具体记录后将其 `lineId` 传给 Service。返回记录中的 `lineId` 取数据库的 `metro_line.line_id`。
- `LineService.search(String keyword)` 的 `keyword` 表示上述线路编号，返回全部匹配的 `LineView`。无匹配时返回空列表。

## IDEA 启动

1. 打开本模块的 `pom.xml`，选择作为项目打开。
2. Maven home 选择 IDEA 的 **Bundled (Maven 3)**；项目语言级别与编译目标为 17。
3. Maven 同步完成后运行 `edu.cs307.metro.Main`；无需数据库配置即可查看开发菜单。
4. 要连接数据库，在该运行配置的 Environment variables 设置 `DB_URL`、`DB_USER`、`DB_PASSWORD`，参考 `config/db.env.example`。这三个变量都需要配置；程序不默认选择某位成员的数据库。模板文件不会自动加载。
5. 程序参数填 `--check-db` 检查当前数据库和 schema，或填 `--gui` 打开窗口。

团队基线是 Java 17。可用较新 JDK 按 `--release 17` 编译，但这不替代最终在 Java 17 运行时上的验证。

## PowerShell 启动与验证

在本模块目录执行：

```powershell
# 从 PATH 或 MAVEN_HOME 查找 Maven
.\scripts\mvn-local.ps1 -Goals verify

# 启动命令行；读取当前 PowerShell 的环境变量
.\scripts\mvn-local.ps1 -Goals @('exec:java', '-Dexec.args=--cli')

# 只读连接检查
.\scripts\mvn-local.ps1 -Goals @('exec:java', '-Dexec.args=--check-db')

# 使用其他 Maven：先在个人环境变量 MAVEN_EXECUTABLE 中保存实际命令路径
.\scripts\mvn-local.ps1 -MavenCommand $env:MAVEN_EXECUTABLE -Goals verify
```

只有 IDEA 内置 Maven 时，也可直接从 IDEA 的 Maven 工具窗口执行 `verify`，无需为终端额外安装 Maven 或把个人安装路径写进共享文件。

脚本把 Maven 缓存存入本模块 `.maven-cache`，构建产物位于 `target`，两者都不提交。也可以通过标准 `mvn verify` 使用个人默认缓存。

`mvn package` 生成普通 jar，不包含 pgJDBC；优先使用 IDEA 或 Maven exec 启动，它们会带上运行时依赖。最终交付的可分发打包方式尚待确定。

默认 `verify` 运行不依赖数据库的自动测试。真实 PostgreSQL 的线路添加、修改、删除及 CLI 完整流程测试需要显式设置 `METRO_TEST_DB_URL`、`METRO_TEST_DB_USER`，可选 `METRO_TEST_DB_PASSWORD`。每个实库测试类创建独立临时数据库，使用仓库的 `Task3/tools/create_table.sql`，结束后删除临时数据库。账号需要有创建数据库的权限，完整命令和验收范围见线路添加删除验收文档。

2026-11-09 的编号选线整体验证已通过，`verify` 共运行 161 项测试，0 失败、0 错误、0 跳过，其中 24 项连接独立临时 PostgreSQL 12.1 实例。CLI 实库用例验证编号与内部 ID 不同、13 号线多候选分别修改和删除，以及查询编号 1 时保留 10、13 号线。临时实例已在验证结束后关闭并清理。Swing 的本轮验证范围为自动交互测试。
