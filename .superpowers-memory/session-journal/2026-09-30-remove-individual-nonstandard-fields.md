# 2026-09-30 移除 Individual 非标准字段（identifier 回滚 + avatar 迁至 ext.account）

## 起因

用户裁定项目原则：**mdr schema 的表必须严格按 ISO/IEC 11179 标准设计，标准之外的一律放 ext schema（id 与审计字段除外）**。核查发现 `mdr.individual` 有两个非标准列：`identifier`（2026-09-18 变更 `add-individual-identifier` 加入）与 `avatar`（V20260901016 加入）。ISO/IEC 11179-3:2023 §6.3.2 Individual 仅定义 name/title/mail_address/email_address/phone_number 五属性。用户指令：取消 `add-individual-identifier`，回滚 identifier，avatar 移到 ext.account。

## 关键决策（用户确认）

- D1 头像纯账号级语义，上传头像不再自动创建 Individual（原 `loadOrCreateMyIndividual` 整体删除）
- D2 API 路径 `/auth/me/individual/avatar` → `/auth/me/avatar`（前后端同源发布，无重定向兼容）
- D3 `mdr.registrar.identifier`（同为非标准字段）本次不动，记 backlog
- 方案 A 硬移除 + 数据搬迁（否决：两阶段软废弃、git revert——后者因后续提交叠加不可行）

## 实施要点

- 三方言迁移 `V20260930001`：account 加 avatar 列 → `UPDATE ... SET avatar=(SELECT ... WHERE individual.id=account.individual_id)` 关联子查询搬迁（三方言通用）→ drop individual.avatar → drop individual.identifier。SQL Server 全 schema 限定 + COL_LENGTH 守卫（兼容并行 WIP 的 flyway default-schema=ext 改动）；达梦 ALL_TAB_COLUMNS 守卫 + EXECUTE IMMEDIATE。
- 连带清理：`IndividualService`（搜索谓词/applyRequest/generateIdentifier/toResponse）、`AuthService`（3 处 setIdentifier + 头像三方法）、`RegistrarService`（individual.setIdentifier 一处；Registrar 自身的 identifier 保留）、DTO、openapi、UI（IndividualList 标识符列/表单项、MyProfile 2 处 URL）。
- `add-individual-identifier` 变更目录 `git rm` 作废（从未归档，主 specs 无痕迹）。
- 测试改写：AuthServiceAvatarTest 重写为 Account 语义（含 never-create-Individual 断言）；AuthServiceTest / IndividualServiceIntegrationTest / RegistrarServiceTest / AuthControllerAvatarTest 同步。

## 验证证据

- TDD 红（Account 缺 getAvatar/setAvatar 等 10 处编译错）→ 绿（目标 5 测试类 47 tests 0 failures）
- 前端 build 通过（含 vue-tsc）；vitest 272 过 1 挂为既有无关失败（MyProfile.spec 的 Registrar 删除入口断言与 HEAD 组件行为不符，request mock 与 URL 无关）
- SQLite 副本库 Python 实测：列删除、avatar 按 individual_id 搬迁值一致、孤儿行 avatar 按设计丢失、重复执行报 duplicate column 属预期一次性语义
- 附带修正：openapi.json 再生成同时消除了 OrganizationRequest/Response 的 identifier 陈旧残留（对照 HEAD 源码确认契约本就无该字段，属纠偏）
- 全量后端测试与 SQL Server 真实库应用：见本 journal 末尾"遗留验证"（完成后补记）

## 新发现 / 经验

- `-Dtest='A+B+C'` 静默匹配不到任何测试且 BUILD SUCCESS——多类必须用逗号分隔；判据看 `Running` 行与 `Tests run:` 计数，不看 BUILD 结果
- 并行会话同树工作会出现"编译中间态致 test 编译中断 → surefire 用旧 test-classes 假绿"，同上判据可破
- `metadata_registry.db`（web 目录下）非 Flyway 管理（无 flyway_schema_history，Hibernate ddl-auto 建表），不能作为迁移验证基座
- `static/assets` 153 个跟踪产物含旧 avatar 路径，属发布步骤（dist→static 同步），不在常规构建链路

## Backlog

- `mdr.registrar.identifier` 移除（连带 RegistrarList 名称兜底 `individualName || organizationName || identifier` 链重构）
- mdr 表标准符合性全面审计（候选：Organization/Contact/Registration_Authority 的扩展列逐一核对 §6.3）
- dm 方言 V20260930001 / V20260926001 待实测（无实例，沿用先例）

## 遗留验证（归档前已补齐，2026-10-01 凌晨）

- [x] 后端重启后 SQL Server 真实库：V20260930001 应用（mdr.flyway_schema_history 记录 success=1，应用耗时 10ms）、`mdr.individual` 仅剩 id/mail_address/name/title 四列、`ext.account.avatar` 列存在。顺带确认 V20260926001 此前已应用且 `uq_language_identification_identifier` 索引在真实库存在
- [x] 冒烟（专用账号 agent_smoke01，用后已删）：头像 PUT/GET/DELETE `/auth/me/avatar` 全链路 200 + 落库正确；旧路径 `/auth/me/individual/avatar` 404（符合 spec）；管理端 Individual 列表响应字段为 emailAddress/id/mailAddress/name/phoneNumber/title 无 identifier；关键词搜索 200
- [x] openspec archive 两变更：主 specs 落地 `individual-standard-compliance`（6 需求）+ `language-identification-uniqueness`（3 需求）；detect-changes 无 partial/truncated
- [x] 后端全量 1362 tests：11 个失败/错误全部位于 ScopedIdentifierConcurrencyIntegrationTest / WorkspaceServiceIntegrationTest / SegmentTemplateStrategyIntegrationTest（既有 SQLite 并发竞争 + 并行会话 WIP 影响，与本变更零交集，目标测试类与二阶类全绿）

## 观察（并行会话 WIP，非本变更问题）

- 重启后日志显示 FlywayHistoryRelocator 执行了 `ALTER SCHEMA ext TRANSFER mdr.flyway_schema_history`，但事后核查 `flyway_schema_history` 仍在 mdr（ext 中不存在）——relocate-flyway-history-to-ext 变更（仅 proposal，未跟踪源码 WIP）的生效逻辑存疑，留给该会话排查。本变更两迁移的 V20260926001/V20260930001 均记录在 mdr.flyway_schema_history 且 success=1。
