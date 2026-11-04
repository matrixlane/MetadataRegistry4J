<!-- openspec:start -->

# superpowers-openspec-team-skills 流程强制启用规则

本项目默认采用 Superpowers + OpenSpec + Superpowers 端到端方式推进功能开发。当任务涉及以下内容时，**必须**先使用 `superpowers-openspec-superpowers-workflow` 完成需求澄清、OpenSpec 提案、设计、规范与任务拆分，经确认后再进入实现，完成后归档：

1. 新增业务功能或新 API / 新模块
2. 数据模型、元模型、注册项结构变更
3. 与 ISO/IEC 11179 标准对应关系有关的需求澄清或改动
4. 跨模块/跨版本的 federation、merge request、导入导出机制变更
5. 涉及 SQLite / SQL Server / 达梦 多种方言的 schema 或迁移脚本变更

## 豁免场景（无需 OpenSpec）

- 纯 bug 修复、测试补充、日志/注释/文案调整
- 不影响业务行为的代码重构（如方法重命名、提取工具类）
- 配置项默认值、环境变量、依赖版本号的微调
- 用户明确说“先直接实现，不用走 OpenSpec”

## 执行要求

- 若任务命中强制范围，先调用 `superpowers-openspec-superpowers-workflow` 技能；各 Agent 按自身支持的方式激活（Kimi Code CLI / Codex 按技能名调用，Claude Code 使用对应 slash command）

- 在启动实现前，向用户确认 OpenSpec 产出（proposal / design / specs / tasks）已完成

- 如用户对范围有异议，以用户当次明确指令为准
  
  <!-- openspec:end -->

> **标准原文核对统一入口**：本文件中凡要求"核对标准原文 / 标准条款依据"处（标准依据表述规则、实体继承规则、正交链完整性规则等），统一调用用户级技能 `mdr-requirements`（Milvus `mdr_iso11179` 集合 BM25+向量双路检索；需精确页码或 Milvus 不可用时回退本地 PDF `ISO/IEC 11179 标准原文目录`）。

# 标准依据表述规则

涉及 ISO/IEC 11179 标准字段的解释性说明（UI placeholder、字段提示、文档描述等），**必须忠实还原标准文档中的原文文字说明**（可翻译为中文，但不得概括、扩写或自行发挥）；不得引用标准章节/条款号作为说明内容。表述前须核对标准原文。

# 数据模型分层规则（强制）

数据库表结构按 schema 分层，**任何涉及表结构的设计与迁移必须先核对此规则**：

- `mdr` schema（标准层）：**只存放 ISO/IEC 11179 系列标准定义的内容**（标准表与标准列）。标准没有定义的字段 MUST NOT 出现在 mdr 表上。
- `ext` schema（扩展层）：**ISO/IEC 11179 标准以外的一切内容统一使用 ext**——包括应用自身的扩展字段（一律放 ext schema 卫星表，与标准表 1:1，按 id 关联，有意不建 JPA 关联、经值键读写），**也包括任何非标准来源的工具/框架表**。
  - 既有判例：`ext.account.avatar`、`ext_language_identification`、`ext.contact`（账号绑定卫星，原 contact_account_binding 迁入）、`ext.registrar`（默认注册人员标志）、`ext.flyway_schema_history`（Flyway 记账表，非 ISO/IEC 11179 内容，依本规则只能落 ext——**Flyway 的 `default-schema` MUST 为 ext**，历史表随之落 ext）；反例教训：2026-10 `mdr.registration_authority.is_primary` 后迁出，见 DECISIONS.md。
- 实现范式：
  - 扩展实体放 `metadata-registry-ext` 模块，`@Table(name=...)` 不写 schema；team profile 经 `metadata-registry-web/src/main/resources/orm/orm-team.xml` 登记 `<table schema="ext"/>`；sqlite 同名冲突时物理表名加 `ext_` 前缀并经 `orm/orm-sqlite.xml` 登记。
  - 迁移三方言同版本号：SQL Server 全限定 `ext.<table>`（schema 兜底 + COL_LENGTH 守卫）；SQLite 裸表名（`ext_` 前缀）；达梦全限定 `EXT.<TABLE>`。
  - **迁移脚本的表引用 MUST 全限定 schema**：Flyway 以 `default-schema`（ext）为会话默认 schema，未限定的表名会解析到 ext 而非 mdr。新增/修订迁移脚本时，凡涉及 mdr 表一律写 `mdr.<table>`（达梦 `MDR.<TABLE>`），ext 表写 `ext.<table>`。
- 判据：字段能在 ISO/IEC 11179 标准条款中找到定义 → 才可进 mdr；否则一律 ext。

# 正交链完整性规则（强制）

ISO/IEC 11179 元模型中，**受管链与内容链是两条正交的链**，靠 §7.4.1 `item_identification`
**关联**连接，不靠继承：

```
内容链：Entity → Item → DataElement / ValueDomain / …      （11179-31 §7.2.2.4.1 等声明 Direct superclass = Item）
受管链：Entity → IdentifiedItem → RegisteredItem → AdministeredItem / AttachedItem
                                        ↑ 沿 item_identification 关联挂回同一个 item
```

任何涉及这两条链的建模与数据导入，MUST 保证以下不变式：

- **§7.4.1**：条目若被标识，`SHALL` 恰好绑定一个 `Identified_Item`；有 `Scoped_Identifier`
  即意味着必有 `Identified_Item`（§7.4.2：每个 Identified_Item 至少关联一个 Scoped_Identifier）。
- **§9.4.1.2**：已标识条目 `SHALL` 沿受管链落到 `Administered_Item` 或 `Attached_Item`
  （"Registered_Item shall be instantiated as either an Administered_Item or an Attached_Item
  but not both."），**不得停在 `Identified_Item` 中间层**。
- **裸 SQL 数据导入 MUST 自建完整关联链。** `administered_item` / `registered_item` /
  `registration_state` 全库仅由 `ItemAdministrationService.ensureAdministeredItem()` 创建，
  绕过该服务的还原脚本、批量导入脚本天然产不出这三张表。
- **验收 MUST 含语义不变式断言**，不得只比对行数或字节。

> 反例教训（2026-10-09）：WS 363/364 基准还原脚本为裸 SQL，未自建受管链，11,177 个已标识
> 条目全部停在 Identified_Item，注册状态恒为空、不出现在注册项列表、永远无法经推进流程获得
> 状态。因验收只比对"行数与基准声明一致"而长期未被发现。守护测试
> `AdministeredItemClosureConformanceTest`；处置见
> `openspec/changes/repair-administered-item-closure/`。
> 
> 基准生成器 `scratch/ws-verify/gen_baseline_sql.py` 已内置前置断言：源库不闭环即拒绝生成。

# 实体继承规则（强制）

数据库表结构按 schema 分层，实体继承关系按标准元模型分层——**任何涉及实体继承（extends）的设计与实现必须先核对标准**：

- 实体的继承（extends）关系 MUST 有 ISO/IEC 11179 相应部分 **"Direct superclass"** 条款依据；标准未声明 Direct superclass 的类 MUST 建模为独立根实体（直接继承仅提供标识符的 mapped-superclass `Entity`，先例：`Contact`/`Organization`/`LanguageIdentification`）。
- 新增或变更实体继承时，MUST 同步更新守护测试 `StandardInheritanceConformanceTest`（metadata-registry-web 测试目录）的白名单并附标准条款引用；未登记的继承边 MUST 使构建失败。
- 依据 MUST 以标准文档原文核对，不得凭印象或二手资料声明继承关系。
- 反例教训（2026-10-09）：`RegistrySpecification` 被错误实现为 `extends Item`（ISO/IEC 11179-3:2023 9.4.10 无 Direct superclass 声明），派生孤儿记录 → FK 约束失败 → 启动兜底清理 → WS 363 数据恢复后启动挂起 13 分钟的生产事故；纠偏见变更 `registry-specification-model-correction`。

# 文档同步规则

每次签入代码前，检查变更是否影响以下文档，如受影响必须同步更新：

- `README.md` — 模块清单、技术栈、页面清单、默认账号等面向使用者的信息
- `docs/architecture/ARCHITECTURE.md` — 模块结构、功能区域、核心执行流程（该文件由 GitNexus 生成，内容大幅变化时应重新生成而非手工改写）

# 多 Agent 项目级上下文

本仓库使用多种 Coding Agent 开发，项目级规则以本文件（`AGENTS.md`）为唯一事实源：

| Agent                                     | 项目级上下文入口                          |
| ----------------------------------------- | --------------------------------- |
| Kimi Code CLI / Codex / Cursor / OpenCode | 直接读取本文件                           |
| Claude Code                               | `CLAUDE.md` 通过 `@AGENTS.md` 导入本文件 |

修改项目级规则时只编辑本文件；`CLAUDE.md` 仅保留导入声明，不复制规则正文。

# Agent 登录系统规则

登录要求按部署版本区分：**个人版（PERSONAL）无账号密码概念**——`PersonalEditionAuthenticationFilter` 对未认证请求自动装载系统唯一 SUPER_ADMIN 隐式主体（无密码、强制改密/498 拦截不触发），Agent 冒烟/E2E 直接请求即可，无需登录；**只有团队版（TEAM）需要账号密码登录**，适用下列规则。

> 本机当前部署（localhost:8081，SQL Server 库）运行在个人版下。Agent 以后测试系统直接调用 API，**不需要账号登录，也不要再创建测试账号**；既有的 `agent-uitest` 测试账号已于 2026-10-13 删除。

Coding Agent 需要登录系统（API 冒烟、E2E、手工验证）时，**必须自己创建一个专用 Admin 测试账户**，严禁使用超级管理员应急码（`emergency-code.txt`）登录。

- 依据：应急码登录会把真实库 `force_password_reset` 置 1（用户下次正常登录被强制改密弹窗阻塞），且旧应急码一次性作废重生（2026-09-21 事故，见 `.superpowers-memory/session-journal/2026-09-21-dimensionality-management.md` 事故记录）。
- 冒烟/验证一律优先用独立临时实例或副本库（`scratch/` 模式）。
- 应急码仅在用户本人遗忘管理员密码、且用户明确知情同意时才能使用；用后立即恢复 `force_password_reset` 原值并告知用户。
  
  

<!-- iso11179-mdr-requirements:end -->
