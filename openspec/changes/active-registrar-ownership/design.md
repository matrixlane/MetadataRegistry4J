## Context

条目归属链路的现状：

- `ItemAdministrationService` 在创建/更新管理项时，仅当请求体 `registrationAuthorityId` 非空才设置 `AdministeredItem.registrationAuthority`，否则保持为空；归属完全由调用方决定。
- 注册员身份已具备完整数据模型：`Registrar extends Contact`，`ContactRepository.findByAccount` 可按账号解析联系人，`NamespacePermissionEvaluator`、`AuthService`、`RegistrationLifecycleService` 均已在用这一解析模式。
- ISO/IEC 11179-3:2023 规定 Registrar 是注册机构的代表，"执行注册管理项之行政步骤的人"（9.4.7），注册机构通过 `registration` 关联对条目负责（9.5.x）；条目归属应当由执行操作的 Registrar 身份自然推导，而非由调用方声明。
- 版本语义（既有行为，本设计引用不修改）：个人版 Admin 自动成为所创建注册机构的 Registrar；团队版 Admin 是否成为 Registrar 可选（初始化向导中自指派或由后续指派）。
- 多 Registrar 身份是真实需求：云部署（CLOUD，留待未来实现）为多租户模式，一个租户可授权另一租户的用户成为本租户注册机构的 Registrar，因此一个用户可同时持有多个注册机构的 Registrar 身份；登录后同时只能激活一个 Registrar 身份进行注册工作。本设计不引入租户概念，仅使身份机制与该场景兼容。
- 联邦合并（`registry-federation`/`merge-request`）会把外部实例条目并入本地，条目跨实例身份为「来源 RAI + 来源标识符」；合并目标端不得把并入条目据为己有。

约束：数据库可移植性规则（UUID 主键、不绑定 schema、双方言迁移）；PERSONAL/TEAM 两版本行为一致。

## Goals / Non-Goals

**Goals:**

- 注册 Administered_Item 必须持有活动 Registrar 身份；非 Registrar 无注册权限。
- 所注册条目的归属在服务端由活动 Registrar 身份自动推导，一定关联该 Registrar 所在的 Registration_Authority，客户端无法指定或篡改。
- 用户同时只能激活一个 Registrar 身份；多身份用户登录后选择一个，可随时切换，选择持久化。
- 联邦并入条目保留来源归属，本地任何操作不得改写其 `registrationAuthority` 为本地注册机构。

**Non-Goals:**

- 不在 `AdministeredItem` 上新增"经办注册员"属性（ISO/IEC 11179-3:2023 未定义 Administered_Item 与 Registrar 的直接关联；Stewardship_Record/Submission_Record 已承载相关角色）。
- 不改变读取权限（条目浏览仍对全部已认证用户开放）。
- 不实现 CLOUD 多租户本身，也不引入租户概念；仅保证 Registrar 身份机制与跨租户授权场景兼容。
- 不实现注册表级合并策略本身（属 `registry-merge-strategy` 范围）；本变更仅规定合并不得改写归属的约束。
- 不改变管理员对治理对象（注册机构、注册规范、命名空间）的维护能力。

## Decisions

### D1：活动 Registrar 身份由服务端解析，颗粒度为 Registrar 而非注册机构

新增 `ActiveRegistrarService`：从 `SecurityContextHolder` 取当前账号 → `ContactRepository.findByAccount` → 过滤 `Registrar` 实例 → 得到 Registrar 身份列表。唯一身份时自动激活；多个身份时读取账号上持久化的"活动 Registrar 身份选择"，无效（如身份被撤销）时回退到首个有效身份。活动注册机构由活动 Registrar 身份派生。

以 Registrar（而非注册机构）为选择颗粒度：同一账号理论上是多个 Registrar 记录的持有者，跨租户授权场景下身份本身才是授权凭证；机构只是身份的派生属性。

备选方案：由前端在请求头携带活动身份 ID。否决——客户端可伪造，回到当前 `registrationAuthorityId` 的老问题。

### D2：注册权限与归属推导

注册（创建）Administered_Item 时：

- 非 Registrar（无任何 Registrar 身份）一律拒绝。团队版未自指派 Registrar 的 Admin 同属此类，提示先取得 Registrar 身份。
- 目标命名空间为注册机构自有命名空间 → `registrationAuthority` MUST 设为活动 Registrar 所在注册机构；命名空间所属机构与活动身份所在机构不一致时拒绝（防止跨机构写入）。
- 目标命名空间为外部（无注册机构、非 system-default）→ 同样要求活动 Registrar 身份，条目 MUST 归属活动身份所在注册机构。外部性由命名空间自身承载：Namespace 不关联任何注册机构，其 `namingAuthority`（ISO/IEC 11179-3:2023 Table 13，实体已具备该字段）记录外部标准的真实来源机构；条目归属与标准来源是两个维度，互不冲突。
- 请求体移除 `registrationAuthorityId` 字段；旧客户端传入未知字段时按忽略处理（Jackson 的 FAIL_ON_UNKNOWN_PROPERTIES 行为需在实现时确认并显式配置为忽略，避免 400）。

### D3：变更权限以"活动 Registrar 身份所属机构"为准

更新/删除管理项时：条目有归属机构 → 当前用户 MUST 正以该机构的 Registrar 身份为活动身份；持有绑定但未激活时需先切换活动身份。条目无归属 → 仅可能为历史遗留数据（新注册条目必有归属），维持既有校验。超级管理员不豁免；其可通过既有的 Registrar 指派能力先获得身份（与初始化向导"管理员自指派注册员"语义一致）。

（初稿曾采用"持有有效绑定即可修改、不要求激活"的宽松方案；按需求澄清改为以活动身份为准——同时只能激活一个 Registrar 身份，所有注册工作均以该身份进行，修改归属机构条目同属注册工作。）

### D4：活动 Registrar 身份选择的持久化

在账号实体新增可空 `active_registrar_id`（UUID/VARCHAR(36)）：Hibernate `ddl-auto` 自动建列，无需 Flyway 结构迁移；遵守可移植性规则（无方言专有 SQL、不绑定 schema）。未设置时按 D1 回退规则解析。

### D5：本地路径归属冻结，合并重关联由合并策略规约

本地创建/更新/删除路径一律不修改既有条目的 `registrationAuthority`。归属变更仅通过注册表合并发生：RAI 一致时迁移条目重新关联到目的方注册机构，双命名空间双标识、版本化重迁移与冲突裁决等语义由 `2026-08-02-registry-merge-strategy` 规约。现状代码已核对：`MergeExecutor.applyUpdate` 对已链接本地条目仅更新内容、不触碰归属（符合）；`GenericItemCreator` 用 `findFirst()` 随意取本地机构作为并入条目归属（不符合，但将在合并策略实现时按"RAI 匹配的目的方机构"语义重写，本变更不单独修补）。

### D6：版本语义对齐

个人版 Admin 自动成为所创建注册机构的 Registrar、团队版可选（既有 `deployment-edition` 与 `initialization-wizard` 规约）保持不变；本变更仅叠加其权限后果：团队版未成为 Registrar 的 Admin 在取得身份前无注册权限。

## Risks / Trade-offs

- [旧客户端/脚本仍传 `registrationAuthorityId`，升级后出现行为差异] → API 文档与变更日志标注 BREAKING；Jackson 配置为忽略未知字段，避免硬失败；前端同步移除该字段。
- [团队版未自指派 Registrar 的 Admin 失去注册能力，可能感到功能"倒退"] → 这是权限语义的明确化而非缺陷；界面在拒绝时给出"先指派 Registrar"的引导；初始化向导保留自指派选项。
- [多身份用户修改另一机构条目需先切换活动身份，操作多一步] → 这是"同时只能激活一个 Registrar 身份"的直接推论；切换入口放在全局可见位置降低成本。
- [活动身份持久化引入账号表结构变化] → 仅新增可空列，Hibernate 自动 DDL，三方言无差异；无数据迁移。
- [历史遗留的无归属条目与新规则并存] → 仅存量数据可能存在无归属条目，维持既有校验即可；新注册条目一律有归属。外部标准的来源真实性由 Namespace.namingAuthority 表达，而非条目无归属。

## Open Questions

（无。原联邦归属核对项已核对完毕并并入 D5 结论；合并侧重关联语义见 `2026-08-02-registry-merge-strategy`。）
