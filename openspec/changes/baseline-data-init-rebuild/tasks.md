# Tasks: baseline-data-init-rebuild

## 1. 生成管线与新金库构建

- [x] 1.1 建立 `scratch/baseline-rebuild/` 工作区并复用既有抽取器，对 34 份最新重印版 PDF 重跑解析；验证：新旧 CSV **逐字节一致**（ws363/ws364 各一份，diff 为零），内容出处核验通过
- [x] 1.2 当前 schema 新金库容器：`scratch/baseline-rebuild/golden-container.db`（应用 8082 旁路在空 SQLite 上建立，112 表 + Flyway 基线 20261009012 + 语言 22 条预置）
- [x] 1.3 金库升级变换 `transform_golden_v3.py`（旧金库只读 → 新金库；单一 ws- 命名空间 + SI 归属、机构 2 + WJW RA、slot_value 拆分、identified→registered 合并）；验证：行数闸口全过（DE 2212 / VD 2160 / PV 4171 / DT 8 / RI=AI=RS=SI=10714），transform-report.md 落档
- [x] 1.4 同码两义裁决核验（变换内断言）：组 1 4004、组 2 BP0M 五码、组 3 BQ2F10ZZZ、组 4 1702、组 5 断言 0205、CV04.30.002 整域移除、KNOWN_GAP 5 壳、CV08.50.102 OBSOLETE 全部通过
- [x] 1.5 衍生数据：受管链闭环（PREFERRED_STANDARD / 2024-04-01 / origin 标记）、34 份 reference_document（URI 全部指向权威目录实际文件并存在性断言）、唯一来源关联 10,391、source_id（19,054/2,804，超活库口径——活库勘误后未回填）、配对 2,212；验证：缺环=0、唯一来源违例=0
- [x] 1.6 三重核验出报告：PDF 重解析一致性 + 活库标识符对账零差异（剔 315 空壳 + 7 已裁决项）+ 值级零失真 diff（19k/2.8k/9.4k/4.2k 行）→ `verification-report.md` 全绿；E2E 期间追加修复：孤儿 slot 4 行剔除（ws.import 残留）、时间戳归一化为 epoch 毫秒 INTEGER（34,006 值，T 分隔 TEXT 致 sqlite-jdbc 500）

## 2. SQL Server 脚本生成与端到端验证

- [x] 2.1 `gen_scripts_sqlserver.py` 从当前 schema 金库直接导出三层脚本（无适配器环节），头注含生成日期/标准版本/裁决清单；自检①行数守恒②引号配对③抽样回验 + FK 拓扑全过
- [x] 2.2 SQL Server 空库端到端：scratch 纯空库 → 应用建表（112 表）→ 三脚本顺序执行全 exit=0 → 应用带数据重启零错误；计数精确命中、闭环 0、唯一来源 0、裁决抽查全过（日志 `e2e-*.log`、验收 `e2e-acceptance-run1.log`）
- [x] 2.3 幂等验证：三脚本全量重跑两轮（含批处理路径一轮），验收输出与首轮逐字节一致；`00-common.sql` 在 sqlite 容器双跑 22+22 不变
- [x] 2.4 参考文档 URI 仓库相对化 + 34 份标准 PDF 收编 `data/docs/data-standards/ws/WS363|WS364/`（sha256 逐份一致）+ 后端相对路径解析与前端分类（含测试）+ 纯空库全流程复验逐字节一致
- [x] 2.5 内容核对比对（活库 vs 脚本，`scratch/baseline-rebuild/diff-live-vs-scripts.md`）及其暴露缺陷的处置：① fresh schema 列类型发散修复——`DataType.annotation`/`PermissibleValue.permittedValue` 加 `@Nationalized`（既有库经迁移为 NVARCHAR，fresh ddl-auto 原为 VARCHAR 致 U+301C 等 GBK 码页外字符丢失）；② 3 条 VM 定义按现行重印版标准删除「卫生部制订的」过期文本（勘误清单取源为旧版印刷），另复核修正 CV08.50.003 残留注记删除、CV02.01.103 新增注记补建；③ 定义值级比对归零
- [x] 2.4 参考文档 URI 仓库相对化 + 标准文档收编：34 份标准 PDF 收编入 `data/docs/data-standards/ws/WS363|WS364/`（与权威源 sha256 逐份一致，transform D2 内嵌核验）；transform D2 URI 改为仓库根相对 POSIX 形态（确定性：仅 reference_document 表变化，其余 111 表字节一致）；脚本重生成（sqlserver 00/10 与 sqlite/dm 00 的 md5 逐字节不变，仅 20-derived 的 34 条 URI 行变化）；SQL Server 纯空库全量复验（DROP/CREATE → 8082 旁路建 schema → 00/10/20 全 exit=0 → 验收计数与此前轮次一致 + URI 抽查 3 条 EXACT + 全 34 条无盘符/无反斜杠/`data/docs/data-standards/ws/` 前缀且文件存在）；预置库 `data/metadata-registry.db` 刷新（日志 `e2e-reuri-*.log`）

## 3. 目录重构、git 跟踪与一键批处理

- [x] 3.1 主题化布局就位：`data/scripts/common/` 含三方言 `00-common.sql` 与通用说明；`data/scripts/data-standards/ws/` 含主题说明及 SQL Server 的 `10-main.sql`、`20-derived.sql`；WS 的 sqlite/dm 支持状态集中在主题 README（后续由 SQL Server 版转换）
- [x] 3.2 `.gitignore` 收窄（`data/*.db*` 忽略、脚本/README/批处理入库）+ 删除 `db-baseline/`；验证：`git check-ignore` 双向断言通过（脚本无命中、db 命中）
- [x] 3.3 `data/init-baseline.bat`：单文件批处理（内嵌 PowerShell，CRLF，PS 5.1 兼容），沙箱三场景实测：配置缺失提示退出（exit 1）/ sqlite 清空后重种 22+22（zh-CN=简体中文/中国）/ sqlserver 对已有数据库三脚本幂等重跑 exit=0

## 4. SQLite 默认路径迁移

- [x] 4.1 默认 URL 改为用户目录：web `application-personal.yml`、`application.yml`、仓库根 `application.yml`、`application-local.yml` 四处同步；验证：无用户配置全新启动后库文件落在 `~/.metadata-registry/data/metadata-registry.db`（嵌套占位符 `${user.home}` 实测展开，不展开则在 `DatasourceConfigService` 层兜底）——`SqliteDefaultUrlResolutionTest` 实测嵌套占位符正确展开为绝对路径
- [x] 4.2 `DatasourceSettingsPage.vue` 默认文件路径与 placeholder 改为新默认；验证：vitest 相关套件 17/17 通过

## 5. 通用基准自动导入机制

- [x] 5.1 新增 `BaselineScriptPresetService`（执行 `data/scripts/common/<方言>/00-common.sql`，脚本根目录经 `app.baseline-scripts-dir` 可配，缺失 WARN 跳过）并接入 `StaticDataPresetService.presetAll()`；`LanguagePresetSeeder` 退役（TDD：先写执行器单测红→绿）；验证：目标测试类全绿——含 `BaselineScriptPresetServiceTest` 7 例、`StaticDataPresetServiceTest`、`FactoryResetLanguagePresetIntegrationTest`、`LegacyLanguageSchemaRepairerTest`；修复 ScriptUtils 平台字符集乱码缺陷（强制 UTF-8）
- [x] 5.3 修复 SQL Server/达梦全新空库启动链断裂（platform 缺陷，实证于 scratch 库）：`FreshDatabaseInitializer` 扩展服务端方言空库识别（sqlserver 查 mdr/ext/dbo 全域、dm 查用户模式），fresh 时 EMF 前裸 JDBC 预建 flyway schemas（仅 sqlserver），注入 `ddl-auto=update` + `baseline-on-migrate` + 动态扫描 `db/migration/<方言>/` 最大版本号为基线版本；验证：`FreshDatabaseInitializerTest` 13 例全绿 + scratch 纯空库 `metadata_registry_baseline_test` 启动实测（112 表、基线 20261023001）
- [x] 5.4 初始化向导清理保留基准数据（预置库拷贝即用不被摧毁）：`clearBusinessDataOnly()` 保留集扩大为基准承载的内容/受管链/衍生表整表保留 + 治理表（organization/registration_authority/namespace 系）按基准常量（ws- 命名空间、WJW/统计信息中心机构、WJW RA）排除式清空（含 Namespace→Item JOINED 父行连带删除与 RA↔namespace FK 环顺序）；恢复出厂行为不变（data/README.md 注明出厂后基准经批处理重导）；验证：`InitWizardServiceIntegrationTest` 新增 3 例（setup/abort 基准完整 + 重跑幂等）全绿
- [x] 5.5 向导状态判定排除基准常量：`getWizardState()` 的 RA/namespace 计数排除 ws- 命名空间与 WJW 注册机构基准常量 id（复用 5.4 引入的类级共享常量），预置基准库下未初始化系统不再误报 IN_PROGRESS；验证：新增「仅基准治理行 → READY」「基准+脏行 → IN_PROGRESS」两例全绿
- [x] 5.2 三路径幂等验证：全新库启动（容器建库即获 22 语言）/ 向导 setup+abort（集成测试 3 例 + 预置库真实 setup 后语言 22 条在位）/ 恢复出厂（FactoryResetLanguagePresetIntegrationTest 绿）；三条路径语言完整且不重复

## 6. 预置库、文档与全量回归

- [x] 6.1 预置库产出：`data/metadata-registry.db` = 修复后新金库（当前 schema + 全量基准 + 时间戳归一）；沙箱拷贝即用终验：启动 → 向导 READY → setup/commit 成功 → 基准全量在位（闭环 0）→ 数据元/值域列表 API 200（修复前 500 路径复验通过）
- [x] 6.2 文档：`data/README.md`（分层/批处理/预置库/默认路径/再生成）+ 根 `README.md` 数据初始化章节同步（默认路径、批处理导入、动态基线版本）；`docs/architecture/ARCHITECTURE.md` 不涉及（模块结构未变，种子服务非模块视图内容）
- [x] 6.3 全量回归：web 1571 例 9F+4E 与既有基线逐项一致（新增失败=0，净增 10 例全绿）、前端 vitest 371/371 + build 过、`detect_changes` 无 partial/truncated、`openspec validate --all` 72/72
