# 2026-10-05 authority-contact-governance（注册机构治理与提交联系人约束）

## 需求

个人版/团队版差异化治理：初始化机构带隐式 Primary 标志（个人版禁删、团队版暂不发挥作用）；个人版 Admin 可新建机构并自动成为其注册人员（该身份禁删）；团队版任何用户可删全部自身身份、非 Admin 不能自建注册人员身份、Admin 可删所有机构（级联删除全部 Administered_Item + 警告提示）；初始化先创建注册规范（系统全局）再建机构/注册人员；提交组织可选、提交联系人必须是本人在所选机构的身份（否则入口提示+后端拒绝）。

## 交付（worktree `.worktrees/authority-contact-governance`，分支 feat/authority-contact-governance，基线 master 3ee1822）

提交序列：03c3d59（Primary 标志：实体列+三方言迁移 V20261005001+启动调和回填）→ 016510c（机构治理后端）+ cd672dc（初始化顺序，并行 worktree 后合并）→ c418937（身份增删）+ c9b372b（提交约束+机构页）→ 4d9d413（合并+resources.ts 冲突解决）→ 413f501（MyProfile 测试更新）→ a849d42（评审修复）→ 5025029（openapi）→ 190688a（dist→static）。

- **Primary 标志**：`registration_authority.is_primary` 布尔列（NOT NULL DEFAULT false），仅初始化向导置位，零 DTO/前端泄漏；存量库启动调和按 UUID 字典序最小回填（与 MIN(id) 口径一致）。
- **机构治理**：create 版本分支（PERSONAL 不限量+SUPER_ADMIN+自动 `createRegistrarForUser`；TEAM 沿用 SINGLE 限建）；delete 仅按版本判定（PERSONAL+Primary 拒，TEAM 全放行——评审 M1 修正）；`RegistrationAuthorityDeletionService` 全 JDBC 方言感知级联（Item 图→context 子表+spec.context 置空→SR→Stewardship→Registrar（双口径匹配）→命名空间+sys: 置空→流转规则→RA/organization），单事务禁 REQUIRES_NEW；`GET /{id}/deletion-preview` 计数；写端点服务层 SUPER_ADMIN 校验、preview 限 REGISTRAR+；Response 加 `deletable` 间接信号。
- **初始化顺序**：setup 重排 Spec→RA(Primary)→Registrar→Namespace→sys 挂接；向导步骤前移（注册规范=步骤3）；Mockito InOrder 测试锁顺序。
- **身份增删**：kind=REGISTRAR 仅 SUPER_ADMIN（新建 ContactIdentityKind 枚举+MyContactRequest.kind）；createMyContact 对 RA 组织回填 registrationAuthority（评审 M2 修正，防 null-RA 僵尸）；deleteMyContact PERSONAL 拒有关联机构的 Registrar/TEAM 放开全部自删。
- **提交约束**：`validateSubmissionInfo`/`resolveContactAndOrganization` 两处同步——org 给出→账号身份解析（422 引导文案）、contact+org→从属校验、仅 contact→兼容；确定性排序（默认绑定优先）。前端 picker 组织下拉（默认=活动 Registrar 组织，回退链 D5）、WorkspaceView 三入口预检按活动身份组织、无身份提示。
- **Registry Context 随机构删除后自愈**：级联删 context 行+spec.context 置空，backfillIfInitialized 下次启动重建到幸存首个 RA（集成测试锁定）。

## 评审与修复

双评审员（独立只读）：后端不通过（B1 context 未清理/M1 TEAM+SINGLE 禁删/M2 null-RA 僵尸/M3 SR 全库误删/M4 写端点无角色门槛）；前端有条件通过（M1 预检恒真/M2 默认选中未按活动身份/M3 同交叉）。全部修复+回归测试（级联集成 9/9、Service 19/19、Contact 24/24、ItemAdministration 68/68）。

## 验证

- 后端全量 1412 测试：仅 3 个已知环境性失败类（WorkspaceServiceIntegrationTest/SegmentTemplateStrategy/ScopedIdentifierConcurrency，SQLite shared-cache 脆弱性，与 merge-base 基线逐类一致）。
- 前端 293/293 + build 通过。
- 双版本真实应用冒烟 48/48（`scratch/authority-governance-smoke/smoke.sh`，gitignored）：个人版建二机构/自动注册人员/级联删/Primary 拒删/提交 422+成功分支；团队版 SINGLE 限建/非管理员注册人员 422/删 Primary 级联。
- `openspec validate --all` 66/66。

## 教训与备忘

1. **评审盲区模式**：零残留扫描清单即盲区清单——B1（context_id）与 M2（造数未走 createMyContact 路径）都落在断言半径外。级联/清理类功能必须"列清单→对清单造数→对清单断言"三对齐。
2. **spec 语义要钉死否定式**：删除保护"按版本判定、替代按运行模式"必须显式禁止旧分支残留，否则实现会顺手保留旧守卫（M1）。
3. 并行 worktree 实施：mvn target/ 与 npm 目录冲突规避（前后端代理可同 worktree 并行）；多 worktree 分支合并时 JSON/import 块易冲突，预留合并步骤。
4. GitNexus CLI 在 worktree 中 detect_changes 不可用（仓库按注册标签解析，主树工作区被并行会话占用）——**主树集成到 master 后必须补跑 detect_changes**。
5. 主树有并行会话同文件 WIP（ContactIdentityKind/kind 路径），集成时以本分支为准去重。
6. 既有平台限制不变：SQLite 64 表 join 上限阻断 workspace submit 成功路径 E2E（单测覆盖）；全新空库 Flyway 全量重放死于 V20260727001（ddl-auto 等价冒烟替代）。

## 待办

- 主树集成：合并 feat/authority-contact-governance 到 master（注意主树并行 WIP 冲突），集成后重跑 `detect_changes --scope all` 与全量测试。
- OpenSpec 归档（continue-archive）：主 specs 合并——新增 `registration-authority-lifecycle`，修改 `deployment-edition`/`system-initialization`/`registry-specification`/`contact-identity-management`/`submission-record`。
- 遗留边界：管理端 /api/v1/contacts、/api/v1/registrars 无守卫删除端口（既有缺口）；merge_request.target_namespace_id 等极少数指向列不在级联范围；dm 方言迁移待真实库实测。

## 追加：remove-registry-running-mode（2026-10-05 当晚）

用户反馈"单注册表"限制仍存在于 TEAM 创建路径。裁定：应用实例即一个 Registry，彻底取消 SINGLE/MULTI 运行模式概念。OpenSpec 变更 `remove-registry-running-mode`（已归档 2026-10-05-remove-registry-running-mode）：删除 SystemSettingService runningMode 读写与 setRegistryPolicy、SystemController 治理策略端点、向导 commit 的 setRunningMode 写入、DTO runningMode 字段、前端 auth store/向导/注册机构页门控；机构创建两版本均不限数量（SUPER_ADMIN 门槛保留）；ExportMode.SINGLE/MULTI（数据字典导出格式）保留。验证：后端 1429 测试仅既有环境性基线失败、前端 298/298、detect compare 完整、validate 66/66。提交 e49d1cb（**注意：暂存范围过宽裹入并行会话 4 个测试文件——FlywayHistoryRelocator*/DatasourceConfigServiceTest，均通过，已随本次提交入库**）+ 归档提交。

## 追加：move-ra-primary-to-ext（2026-10-07/08）

用户指出 is_primary 落在 mdr.registration_authority 违反"mdr 只装标准列、扩展归 ext"分层原则。修复：RegistrationAuthorityExtension 卫星实体（ext 模块，orm-team.xml schema=ext / orm-sqlite.xml 物理名 ext_registration_authority）+ V20261008001 三方言迁移（建表→搬数据→删列）。真实 SQL Server 库首跑撞 5074（列上 DF__registrat__is_pr__* 默认约束依赖，V20261005001 内联 DEFAULT/Hibernate ddl-auto 都会生成）——修复：删列前先 sys.default_constraints 显式 DROP，搬迁加 NOT EXISTS 守卫。全程测试 1448 仅环境性基线失败。原则固化：AGENTS.md 新增"数据模型分层规则（强制）"，DECISIONS.md decision-2026-10-07-mdr-ext-layering。教训：既有先例（dimensionality.coordinate_indicator 是 11179-31 表 22 标准属性）被错误类比为"标准表可加列"；该原则此前只在 CURRENT_STATE/journal，未进 AGENTS.md 强制入口。
