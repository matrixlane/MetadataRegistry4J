# 验证记录（2026-10-20）

> 全部数据库运行验证使用临时目录（%TEMP%\mdr-verify\*）与隔离 user.home；未连接或写入本机真实 SQL Server 库。

## 1. 数据源配置重启生效（任务 1.1–1.3）

- 关键发现：profile 特定文档内声明的 `spring.config.import` **不生效**（第二轮启动仍连接旧默认路径，app2.log 实证）。最终方案：
  - 移除 application.yml / application-personal.yml / application-team.yml 三处 import 声明；
  - 新增 `LocalConfigBootstrapInitializer`（main() 注册），把本地配置属性源插入 systemEnvironment 之后、一切 config data 之前；
  - 保存值以 `${DB_URL:值}` 占位形式落盘，环境变量仍可覆盖（语义保持）。
- 实测：经真实保存 API（PUT /api/v1/datasource/config）写入含空格的新路径 `chosen dir with space\chosen.db` → 重启 → Flyway 日志 `Database: jdbc:sqlite:C:\...\chosen dir with space\chosen.db`：新路径生效。
- 既有失败修复：`saveConfigSqliteExpandsHomePrefix`（主干此前即红）经 `~` 展开 + 绝对路径规整后转绿。

## 2. 全新空库引导（任务 2.1–2.5）

- 实现：FreshDatabaseInitializer（ApplicationContextInitializer，上下文刷新前执行；**不走 META-INF/spring/*.imports** —— Boot 3.5 repackage 的 Packager.isTransformable 仅放行 META-INF/services，应用自身 META-INF/spring 目录留在 jar 根、可执行 jar 类路径不可见，第一轮验证曾因此失败）。
- 空库识别仅对“零用户表”库生效；有表无 Flyway 历史的旧库维持既有 baseline-on-migrate 兼容链（基线 20260918002 + 幂等守卫迁移，行为不变）并输出专属 WARN（单测锁定“不静默标新基线”）。
- 实测（全新环境）：`./data/metadata-registry.db` 自动创建（父目录自动建）、112 张用户表（含 ext_ 前缀卫星表 4 张）、flyway 历史恰 1 行 `BASELINE(20261009012, success)`、静态预置完成、init-wizard/status 返回 initialized=false。

## 3. 迁移序列（任务 3.1–3.4）

- 版本清单（静态清点）：sqlserver 77 个 SQL 迁移、sqlite 52 个、dm 57 个；三目录最大版本均为 `20261009012`（共同基线锚点）。
- `CommonBaselineGuardTest` 静态守卫：任何方言目录最大版本与 `FreshDatabaseInitializer.FRESH_BASELINE_VERSION` 漂移即构建失败；后续新增迁移 MUST 三方言同版号。
- 空库不依赖历史迁移产出的业务行：ORM 建表 + StaticDataPresetService 预置 + 向导 setup/commit 服务建数据，全链路应用服务完成。
- 已知限制：SQL Server/达梦侧空库运行时验收需隔离实例，本机不具备（design §7）；SQLite 侧已完整验收。

## 4. 验收证据（任务 5.1–5.4，临时目录实测）

| 场景 | 结果 |
|---|---|
| 5.1 无库文件首次启动 | 文件自动创建；112 表；BASELINE(20261009012)；预置完成；status 端点 initialized=false |
| 5.2 保存新路径重启 | 连接新路径（含空格目录）；新库同样建表 + 基线 + 预置 |
| 5.3 初始化 + 语义闭环 | setup/commit code=0；initialized=true；受管链闭环违反查询 **0 行**；Administered/Attached 互斥成立（scoped_identifier=1, administered_item=1, 互斥违反=0；spec=1/ra=1/ns=1/registrar=1） |
| 5.4 既有库重启 | 零 fresh 覆盖日志、零基线/迁移重放；flyway 历史仍恰 1 行；治理数据与语言预置完好 |

## 5. 回归与风险（任务 5.5）

- GitNexus 工具链本机不可用：run.cjs→pnpm dlx 在 @ladybugdb/core 原生构建（spawn cmd.exe ENOENT）失败；全局 gitnexus.ps1 安装损坏；无 bun。无法执行 impact/detect-changes。
- 按 AGENTS.md 对 UNKNOWN 风险的要求完成人工核实：`saveConfig`/`testConnection` 唯一生产调用方为 `DatasourceConfigController`（文本检索）；全量 `git diff` 自审。
- 新增/变更单测 19 项全绿（DatasourceConfigServiceTest 9、FreshDatabaseInitializerTest 7、LocalConfigBootstrapInitializerTest 2、CommonBaselineGuardTest 1）。
- 全量 web 套件存在 **4 个 master 既有失败类**（PersonalEditionAuthenticationFilterTest 2F/4E、RegistrySpecificationServiceTest 1F、WorkspaceRollbackServiceIntegrationTest 2F、WorkspaceServiceIntegrationTest 6F/3E），经干净 HEAD 对比确认与本次变更无关；另 metadata-registry-ext 的 IdentifierMetadataTest 在 HEAD 提交中期望 3 个枚举值而 VersioningPolicy 已有 4 个（FREE_TEXT），工作区中另有一处未提交的单行修复（非本次变更产物，未纳入本次提交）。
