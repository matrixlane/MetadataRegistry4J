# 2026-10-23 baseline-data-init-rebuild（基准数据初始化体系重建）

## 任务

用户指令重建基准数据初始化脚本体系（六条需求）：结构由代码内建、基准数据由脚本生成；第一步只产 SQL Server 脚本；三层数据（通用/WS363-364/衍生）；`db-baseline` 更名 `data/scripts`；SQLite 默认库迁用户目录 `~/.metadata-registry/data/`；`data/` 提供一键批处理。存量库全部清空重建为前提。全程走 superpowers-openspec-superpowers-workflow（auto 模式不暂停）。

## 交付（全部完成，未 git commit）

1. **管线与新金库**：34 份最新重印版 PDF 重解析（CSV 逐字节一致）；`scratch/baseline-rebuild/transform_golden_v3.py` 把旧金库升级进当前 schema 新金库（`golden-new.db`）；同码两义组 1–4 按 proposal 更正断言（组 5 标准已修复）；衍生数据齐（受管链 10,714 闭环、34 refdoc URI 真实路径、唯一来源 10,391、source_id 19,054/2,804 超活库口径、配对 2,212）。
2. **脚本**：`gen_scripts_sqlserver.py` 直出（无适配器）`data/scripts/sqlserver/{00-common,10-ws363-364,20-ws363-364-derived}.sql` + sqlite/dm 各 00-common；全幂等（NOT EXISTS 守卫）、事务包裹、头注裁决清单。
3. **SQL Server 端到端**：scratch 纯空库 → 建表（112）→ 三脚本 exit=0 → 计数精确命中 → 幂等重跑两轮逐字节一致 → 应用重启零错误。
4. **批处理**：`data/init-baseline.bat` 单文件（内嵌 PS，CRLF，PS 5.1 兼容）；沙箱三场景过（配置缺失退出 / sqlite 重种 / sqlserver 幂等）。
5. **代码**：
   - yml×4 + DatasourceSettingsPage 默认路径迁 `~/.metadata-registry/data/`（嵌套 `${user.home}` 实测展开）；
   - `BaselineScriptPresetService` 接入 presetAll（`app.baseline-scripts-dir` 可配、缺失 WARN 跳过、UTF-8 强制），`LanguagePresetSeeder` 退役（含 db/preset 三活副本删除）；
   - **平台缺陷修复**：`FreshDatabaseInitializer` 扩展 sqlserver/dm 空库识别（裸 JDBC 预建 schema + 动态最大迁移版本基线化），fresh SQL Server 库 9 秒启动成功（原链断裂：schema-init 被 defer 推迟 + 中段迁移不可重放）；
   - 向导清理保留基准（内容表整表保留 + 治理表基准常量排除式清空）、`getWizardState` 排除基准常量（预置库不再误报 IN_PROGRESS）。
6. **预置库**：`data/metadata-registry.db` = 新金库（含时间戳归一）；沙箱拷贝即用终验：READY → setup/commit → 基准零损失 → DE/VD 列表 200。
7. **回归**：web 1571 例 9F+4E 与既有基线逐项一致（新增=0，净增 10 例全绿）；vitest 371/371 + build；openspec validate 72/72；detect-changes 无 partial/truncated。

## 关键技术发现（写入 LEARNING_BACKLOG 候选）

- **LocalConfigBootstrapInitializer 无条件加载 `~/.metadata-registry/application-local.yml`（字面量 url 压过 profile 占位符的 DB_URL 环境变量）**；旁路实例隔离正确姿势：`JAVA_TOOL_OPTIONS=-Dmetadata.registry.config.file=<side-local.yml>` + side yml 自带完整 sqlite 配置（含 `flyway.schemas: main`/`default-schema: main`，否则既有库 EXISTING 态无 FRESH 注入会在空 schemas 上炸 flyway）。
- **sqlite-jdbc 3.49 时间戳**：`setTimestamp` 落库 epoch 毫秒 INTEGER；TEXT `'yyyy-MM-dd HH:mm:ss'` 可读，**T 分隔 ISO 不可读**（"Error parsing time stamp"）。旧裸 SQL 脚本写入的 TEXT 值是历史残留，新基准一律 INTEGER。
- **sqlcmd 用 `host,port`（逗号），JDBC 用 `host:port`**；批处理须转换。
- **bat 必须 CRLF**；cmd 把 UTF-8 中文 rem 注释按 GBK 误读可能产生 `&` 碎片被执行——bat 头部注释保持纯 ASCII；PS 5.1 不支持 `??`；PS 5.1 调原生程序会吞参数内双引号（python -c 内嵌 SQL 引号惨案）→ 改临时 .py 助手文件。
- **单文件 bat+PS 混合**：split 标记不得在提取命令行自身出现（用 `'#::PS'+'1_BEGIN'` 拼接规避）。
- 旧金库发现 4 行孤儿 slot（ws.import.* 溯源残留，item 不存在）——基准导出前必须做全量 FK 子集断言（H7 81 组）。
- TaskStop 杀不掉 spring-boot:run fork 的 java 进程，须按端口杀 PID。

## 与既有裁决的一致性

- 315 空壳不进基线、`CV04.30.002` 整域移除、KNOWN_GAP 保留、冲突 SI 保留 NULL（金库改名后实际零冲突组）、origin 两种值保留不抹平——全部沿用。
- 标识符命名金库→活库终态对齐（7,884 行改名，UUID 全保留）；WST 364.13 标题按封面修正为「卫生健康费用」。

## 待办/交接

- **未 git commit**（等用户确认；注意工作树含并行会话 administered-item-reference-documents-ui 的归档改动，与本变更文件不重叠，提交时按变更分组）。
- 下一步：`continue-review`（代码评审）→ `continue-archive`（OpenSpec 归档，主 spec 合并：新增 baseline-data-scripts + datasource-configuration/system-initialization delta）。
- sqlite/dm 的 10/20 WS 层脚本转换器（后续迭代，以 sqlserver 版为基准）；达梦 00-common 未真库实测（本机无实例）。
- 开放问题：计量单位通用基准无权威目录来源未预置（留扩展位）。
- 活库 metadata_registry 被旁路实例误应用了 V20261023001（HEAD 既有迁移，本就该落，成功无副作用）——用户已被告知。
- scratch/baseline-rebuild/ 产物流程：golden-old-20261023.db（旧金库归档）、golden-container.db（容器模板）、golden-new.db（新金库）、bat-test/、preseed-test/、e2e-*.log。

## 追加（同日晚间，用户追加指令）：标准文档随包分发 + URI 仓库相对化

- 34 份标准 PDF 收编仓库 `docs/samples/data-standards/WS363|WS364/`（9.4MB，git 跟踪，与权威源目录逐份 sha256 一致）。
- `reference_document.uri` 全部改为仓库根相对 POSIX 路径（transform D2 + 生成器重跑；00/10 脚本字节级不变，仅 20-derived 34 行 URI 变）；SQL Server 纯空库全流程复验逐字节一致。
- 打开链路打通：`ReferenceDocumentService.resolveLocalPath` 扩展相对路径（相对 user.dir 解析 + 禁 `..` 逃逸，+2 单测）；前端 URI 分类抽 `utils/refdocUri.ts`（相对路径走后端代理，+2 vitest）。
- 终验：预置库（相对 URI 版）启动 → setup/commit → `GET /reference-documents/{id}/content` 返回 200 application/pdf，字节 sha256 与仓库 PDF 一致；DE 列表 200。
- 教训：CWD=仓库根启动时根 application.yml 的 spring.config.import 会引入真实用户配置（orm-team.xml）压过 side yml 局部项——side 配置必须自包含（含 mapping-resources）。
- 前端全量 373/373 + build 过；detect-changes 无 partial/truncated。

## 追加②（同日，用户要求活库 vs 脚本差异比对）

- 方法：脚本落地库 `metadata_registry_baseline_test`（纯空库重建）与活库跨库只读比对，脚本 `scratch/baseline-rebuild/diff_live_vs_baseline.py`，报告 `diff-live-vs-scripts.md`。
- 结论：内容高度一致（名称/定义/码值/槽值全等）；差异全部可归因分类（315 空壳/运行时行/勘误漂移/裁决项/URI 口径/注册状态裁决）。
- 比对驱动修复 4 项：① fresh schema VARCHAR 发散（`DataType.annotation`/`PermissibleValue.permittedValue` 加 @Nationalized，data-specification 模块 pom 补 hibernate-core provided）；② CV05.01.002 三条 VM 定义删「卫生部制订的」过期文本（现行重印版无、旧版印刷有）；③ CV08.50.003 残留注记 L/M 删除；④ CV02.01.103.03 补「在家中发生的死亡，未送医院」注记。
- 平台级发现入 backlog BL-2026-10-23-04：fresh 库缺 ext.identifier_sequence_registry、approver 集合表落 mdr 而非 ext、16 列可空性 fresh 更严。
- 活库注册状态 STANDARD 10403 vs 基准 PREFERRED_STANDARD 10713 的语义分歧已溯源（ws-errata 报告第 176 行自认不一致；基准按文档化裁决统一 PREFERRED_STANDARD）——待用户裁定活库是否后续统一。
