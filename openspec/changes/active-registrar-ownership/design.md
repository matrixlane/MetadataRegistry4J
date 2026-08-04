## Context

条目归属链路的现状：

- `ItemAdministrationService` 在创建/更新管理项时，仅当请求体 `registrationAuthorityId` 非空才设置 `AdministeredItem.registrationAuthority`，否则保持为空；归属完全由调用方决定。
- 注册员身份已具备完整数据模型：`Registrar extends Contact`，`ContactRepository.findByAccount` 可按账号解析联系人，`NamespacePermissionEvaluator`、`AuthService`、`RegistrationLifecycleService` 均已在用这一解析模式。
- ISO/IEC 11179-3:2023 规定 Registrar 是注册机构的代表，"执行注册管理项之行政步骤的人"（9.4.7），注册机构通过 `registration` 关联对条目负责（9.5.x）；条目归属应当由执行操作的注册员身份自然推导，而非由调用方声明。
- 系统支持单注册表（SINGLE）/多注册表（MULTIPLE）两种运行模式；MULTIPLE 下一个账号理论上可被指派为多个注册机构的注册员。
- 联邦合并（`registry-federation`/`merge-request`）会把外部实例条目并入本地，条目跨实例身份为「来源 RAI + 来源标识符」；合并目标端不得把并入条目据为己有。

约束：数据库可移植性规则（UUID 主键、不绑定 schema、双方言迁移）；PERSONAL/TEAM 两版本行为一致。

## Goals / Non-Goals

**Goals:**

- 管理项的注册机构归属在服务端由当前登录账号的活动注册员身份自动推导，客户端无法指定或篡改。
- 仅条目所属注册机构的注册员可变更该条目；无归属的外部编目条目维持既有行为。
- 多注册机构注册员可显式选择/切换活动注册机构，选择持久化。
- 联邦并入条目保留来源归属，本地任何操作不得改写其 `registrationAuthority` 为本地注册机构。

**Non-Goals:**

- 不在 `AdministeredItem` 上新增"经办注册员"属性（ISO/IEC 11179-3:2023 未定义 Administered_Item 与 Registrar 的直接关联；Stewardship_Record/Submission_Record 已承载相关角色）。
- 不改变读取权限（条目浏览仍对全部已认证用户开放）。
- 不实现注册表级合并策略本身（属 `registry-merge-strategy` 范围）；本变更仅规定合并不得改写归属的约束。
- 不改变管理员对治理对象（注册机构、注册规范、命名空间）的维护能力。

## Decisions

### D1：活动注册员由服务端解析，而非会话/前端传入

新增 `ActiveRegistrarService`：从 `SecurityContextHolder` 取当前账号 → `ContactRepository.findByAccount` → 过滤 `Registrar` 实例 → 得到注册员绑定列表。唯一绑定时直接确定活动注册机构；多个绑定时读取账号上持久化的"活动注册机构选择"，无效（如注册员身份被撤销）时回退到首个绑定。

备选方案：由前端在请求头携带活动注册机构 ID。否决——客户端可伪造，回到当前 `registrationAuthorityId` 的老问题。

### D2：归属推导与命名空间一致性校验

创建管理项时：

- 目标命名空间为注册机构自有命名空间 → `registrationAuthority` MUST 设为该命名空间所属注册机构，且当前用户 MUST 是该机构的注册员；命名空间所属机构与活动注册机构不一致时拒绝（防止跨机构写入）。
- 目标命名空间为外部（无注册机构、非 system-default）→ 保持 `registrationAuthority` 为空，任何已认证用户可编目（维持现状）。
- 请求体移除 `registrationAuthorityId` 字段；旧客户端传入未知字段时按忽略处理（Jackson 默认 FAIL_ON_UNKNOWN_PROPERTIES 行为需在实现时确认并显式配置为忽略，避免 400）。

### D3：变更权限以"条目所属机构的注册员"为准

更新/删除管理项时：条目有归属机构 → 当前用户 MUST 持有该机构的注册员身份（不要求恰好是"活动"选择，只要是有效绑定即可，避免频繁切换）；条目无归属 → 维持既有校验。超级管理员不豁免；其可通过既有的注册员指派能力先获得身份（与初始化向导"管理员自指派注册员"语义一致）。

备选方案：按"活动注册机构 = 条目归属机构"才放行。否决——多机构注册员每改一个条目都要切换活动机构，体验差且无安全收益（绑定关系本身已是授权依据）。

### D4：活动注册机构选择的持久化

在账号实体新增可空 `active_registration_authority_id`（UUID/VARCHAR(36)）：Hibernate `ddl-auto` 自动建列，无需 Flyway 结构迁移；遵守可移植性规则（无方言专有 SQL、不绑定 schema）。未设置时按 D1 回退规则解析。

### D5：联邦并入条目归属冻结

`MergeExecutor`/`GenericItemCreator` 并入条目时：新建条目保留合并包中的来源注册机构表示；对已通过 `item_link` 链接的本地既有条目，禁止用合并负载覆盖其 `registrationAuthority`。本地更新路径（D3）对来源机构在本地无注册员绑定的条目自然形成只读效果。

## Risks / Trade-offs

- [旧客户端/脚本仍传 `registrationAuthorityId`，升级后出现行为差异] → API 文档与变更日志标注 BREAKING；Jackson 配置为忽略未知字段，避免硬失败；前端同步移除该字段。
- [无注册员身份的管理员在 PERSONAL 版被锁死] → 初始化向导与部署版本初始化均已默认将管理员设为注册员；剩余场景由注册员指派界面补齐。
- [活动机构选择持久化引入账号表结构变化] → 仅新增可空列，Hibernate 自动 DDL，三方言无差异；无数据迁移。
- [外部编目条目仍可无归属，与"所有权唯一来源"目标不完全闭合] → 有意保留：外部组织标准的编目不构成本机构所有权，这与既有命名空间前置条件语义一致；合并策略变更时再统一裁决。

## Open Questions

- 多注册机构绑定在实际部署中是否真实存在（MULTIPLE 模式下是否允许同一账号跨机构担任注册员）？若确认不存在，D4 的切换 UI 可简化为只读展示。
- `MergeExecutor` 当前是否已在外部机构表示与归属赋值上满足 D5，还是需要新增防护？启动实现时以代码为准核对。
