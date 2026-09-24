# Session Journal — add-registry-context-bootstrap（2026-09-23）

## 需求
按 ISO/IEC 11179-3:2023 初始化引导 Registry Context（注册系统默认上下文，附录 C Context1）：初始化向导完成、注册机构自有命名空间标识/版本生成规则配置之后创建；标识/版本由命名空间策略产出（MANUAL_ONLY 固定字符串 `registry-context`/`1.0`）；中英文指称（`Registry Context`/`注册系统上下文`）与定义（忠实 8.2.2.2 原文）语境自指；回填 `RegistrySpecification.context`（Table 28）；既有已初始化库启动补建。用户三次关键约束：①晚于规则配置创建、②命名空间=RA 自有命名空间（签名层强制）、③标识符随策略不硬编码（用户追问 11179-3 指称要求后确认方案 1：默认正则允许空格）。

## 方案（已确认并落地）
- `RegistryContextBootstrapService.ensureRegistryContext(RegistrationAuthority)`：命名空间内部解析 `ra.getNamespace()`（null 抛 IllegalStateException）；固定主键 `...0002`（JDBC 骨架插入绕过 @GeneratedValue；仅幂等键/系统引用，非标识符）；4 条自指 TextLine（`SELF_CONTEXT_REFERENCE` 哨兵 + PREFERRED）；复用 `applyDesignations/applyDefinitions/ensureAdministeredItem(entity, req, ra)/recordSubmission`；spec.context 空则回填；`NamespaceIdentifierPolicyResolver` 判 MANUAL_ONLY 回退固定字符串。`backfillIfInitialized`：仅已初始化系统；ObjectProvider 自注入代理保证原子性。
- `RegistryContextBackfillRunner` @Order(101)，不挂 app.seed.enabled。
- 向导接线：`InitWizardController.commit` 先 `commit()` 再 `createRegistryContextAfterInitialization()`（独立事务，失败仅 warn）。
- `ContextTestDataInitializer` 补 @Order(100)；`IdentifierStrategyLoader` 补 @Order(90)。

## 教训（重要，均冒烟暴露，单测全绿而真实库爆炸）
- **存量库 acceptability CHECK 与落库格式矛盾（既有平台缺陷）**：`AcceptabilityConverter` 按 11179-3 表 20 落库**小写**，基线 CHECK 仅允许**大写**——任何设置可接受性的写入在存量库必失败。三方言迁移 `V20260923002` 重建 5 张表 CHECK 为 `lower() in (标准小写)`。SQL Server/达梦带守卫未实测。
- **SQLite 单连接池（total=1）**：REQUIRES_NEW/嵌套事务必然死锁（连接等待超时）。跨事务操作必须在调用方事务完全提交后组合调用（Controller 两层调用）。
- **向导事务内 catch 挡不住事务污染**：内层持久化失败把外层 TX 标 rollback-only → "Transaction silently rolled back"。
- **Runner 顺序**：消费标识策略的 Runner 必须排在 `IdentifierStrategyLoader` 之后，否则策略注册表为空、标识生成回退 UUID。
- **8081 常驻实例持有 target jar 句柄**：repackage 无法重命名 jar；冒烟改用 classpath 模式（`dependency:build-classpath` + `*/target/classes`）。终止了一个 10MB 卡死的 javaw 僵尸（PID 52780）。
- **全新库全量 Flyway 重放被既有 V20260919003 阻断**（language_identification.language_id NOT NULL，CURRENT_STATE 遗留⑤）；冒烟改用真实库副本重置（`system_setting` 是 key-value 表：`UPDATE system_setting SET setting_value='false' WHERE setting_key='system.initialized'`；admin 重置 NO_PASSWORD 后任意密码可登录）。
- 子代理未经评审改全局默认正则（允许空格）——恰好是用户后续确认的方案 1，但流程上应先请示。

## 验证
- web 全量 1249 tests 0 failures；`RegistryContextBootstrapServiceTest` 8/8（固定 UUID、自指、MANUAL 回退、幂等、spec 回填、backfill 三分支）；`InitWizardServiceIntegrationTest` 9/9（setup 后不存在、commit+create 后存在、CTX.00001/1.0.0、spec.context）。
- 冒烟 `scratch/registry-context-smoke/smoke.sh` 30/30 PASS（真实库副本基座，classpath 模式）：向导路径 14 断言、重启幂等 2、补建路径 14（含 CTX 段模板标识、自指 relation、RA 命名空间归属、spec 回填）。
- detect_changes 无 partial/truncated（risk=critical 系工作区 193 个未提交文件聚合）。
- openspec validate --strict valid。

## 归档后修正（2026-09-23，用户反馈：系统预置数据不应出现在工作空间草稿）
- 缺陷：`ensureAdministeredItem` 统一 markDraft（管理状态=DRAFT）+ recordSubmission 使管理员成为提交人 → Registry Context 出现在工作空间草稿视图（徽标 1）。
- 修复（纯 bug 修复，豁免 OpenSpec 流程，主 spec 直接补需求"系统预置不进入提交流程"）：创建时管理状态置空、不再生成 SubmissionRecord；`backfillIfInitialized` 对存量 DRAFT 状态的 Registry Context 自动归一（重启自愈）。
- 注意：用户在 UI 中"修订"Registry Context 会再次触发 markDraft（与所有条目一致的编辑语义）；如需禁止编辑需另行需求。
- **追加修正（同日）**：注册状态由 Incomplete 改为 **Recorded**（用户要求；系统预置数据已由注册机构完整登记，11179-6 Recorded 语义；Incomplete 面向用户提交工作流）——创建时显式置 RECORDED + 管理状态空；存量归一同时覆盖 Incomplete→Recorded。主 spec 需求更新为"注册状态 MUST 为 Recorded"。验证：web 1291 tests 0 failures、冒烟 36 断言两轮 PASS。
- 验证：web 1290 tests 0 failures（新增 3 用例：管理状态为空/无提交记录/存量归一）；冒烟含"管理状态为空"断言 3 场景全 PASS；openspec validate valid。

## 待办
- ~~待 `continue-review` → `continue-archive`~~ 已按用户指令直接归档（2026-09-23，跳过评审；验证证据：1249 tests、冒烟 30/30、detect_changes 干净）。
- SQL Server/达梦 V20260923002 未实测（带守卫，随实例重启自动应用）。
- ARCHITECTURE.md 属 GitNexus 生成文档，提交前评估是否重生成。
- 8081 实例仍在跑旧代码；用户重启后真实库将触发补建 + CHECK 迁移自动应用。
