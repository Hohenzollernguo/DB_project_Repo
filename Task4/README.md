# Task4 开发骨架

Java + JDBC 地铁管理程序，供 A、B 按统一接口协作开发。共享时建议将本模块放在仓库根目录下的 `Task4/`，保留内部结构。

- [架构与目录说明](Task4架构与目录说明.md)：职责、接口、分工、规则及开发顺序。
- [架构审阅与验证记录](docs/架构审阅与验证记录.md)：要求覆盖、验证结果与待完成事项。
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

启动命令行菜单和 Swing 外壳；配置后通过 JDBC 检查目标数据库；共用 DTO、业务接口、连接工厂、事务边界、错误转换已经建立。

车站/线路 CRUD、站序操作、Q7 接入、路径和地标功能仍是明确失败的开发占位；票价返回约定的 RULE_NOT_CONFIGURED。菜单与窗口能够启动不代表 Task4 业务完成。

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
