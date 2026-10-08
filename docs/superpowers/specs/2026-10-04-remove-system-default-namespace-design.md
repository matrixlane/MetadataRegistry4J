# 移除系统默认命名空间（sys:）设计

日期：2026-10-04
状态：待确认
OpenSpec change（预定）：`remove-system-default-namespace`

## 1. 背景与依据

- ISO/IEC 11179-3:2023 无"系统默认/根命名空间"概念：`Namespace.shorthand_prefix` 为 0..1 的人类可读便捷前缀，标准无唯一性要求；Namespace 作为 Item 的识别是可选的（item_identification 关联"zero or one"）；`identifier_scope` 仅规定每个 Scoped_Identifier 必属恰好一个 Namespace。
- 实现现状（实库核实）：`sys:` 未 scope 任何 ScopedIdentifier；无任何 Namespace 实例被识别；`sys:` 的唯一运行职责是门槛/默认推导中的排除哨兵。
- 用户裁定：移除系统默认命名空间功能，标准没有、实现用不到的就不保留。

## 2. 方案

采用方案一（一次性彻底移除）。已否决的备选：

- 方案二：仅停代码、不动存量数据——存量库语义残留，与已确认的"数据迁移删除"决策冲突，否决。
- 方案三：先代码后迁移分两阶段——引入中间不一致态，否决。

## 3. 变更范围

### 3.1 后端主代码（metadata-registry-web）

| 文件 | 变更 |
|---|---|
| `seed/SystemDefaultNamespaceInitializer.java` | 整类删除（含常量 `SYSTEM_DEFAULT_PREFIX` / `SYSTEM_DEFAULT_SCHEME` / `SYSTEM_ORGANIZATION_ID`） |
| `seed/StaticDataPresetService.java` | `presetAll()` 移除第 2 步（系统组织 + sys: 命名空间）；类 Javadoc 步骤序更新 |
| `service/InitWizardService.java` | 删除 `systemDefaultNamespaceInitializer` 字段与 setup() 3.6 步调用；`abort()` 注释更新 |
| `service/ItemAdministrationService.java` | `enforceRaNamespacePrecondition`：外部命名空间判定简化为 `registrationAuthority == null`（去掉 system-default 排除）；`resolveDefaultNamespace`：改用 `findFirstByRegistrationAuthority_Id`（去掉 scheme 排除） |
| `repository/NamespaceRepository.java` | 删除 `findFirstByRegistrationAuthority_IdAndSchemeReferenceNot` 与 `findByShorthandPrefix`（唯一调用方均为 sys: 相关）；新增 `findFirstByRegistrationAuthority_Id` |
| `service/OrganizationService.java` | 删除 `SYSTEM_ORGANIZATION_ID` 三处引用（导入、`list` 过滤、`delete` 保护） |
| `service/FactoryResetService.java` | 注释中的"系统组织 / sys: 命名空间"表述更新 |
| `resources/application.yml` | 第 105 行附近注释更新 |
| `metadata-registry-common-facilities` `identification/Namespace.java` | Javadoc 中 `urn:metadata-registry:system-default` 示例中性化 |

### 3.2 数据迁移（三方言）

新增 `V20261004001__Remove_system_default_namespace.sql`（`db/migration/sqlserver|sqlite|dm` 各一份），单事务、条件删除天然幂等：

1. 按 scheme 定位：`SELECT id FROM namespace WHERE scheme_reference = 'urn:metadata-registry:system-default'`（scheme 比 prefix 稳定，prefix 可空可改）。
2. 防御检查：若 `scoped_identifier` 存在 `namespace_id = 该 id` 的行，跳过该命名空间的删除（理论上不存在；防止误删业务标识）。
3. 删除子表行（按实际外键依赖顺序）：`designation`（item_id）、`designation_*` 关联表（含 designation_id 的）、`namespace_naming_convention`（namespace_id）。
4. 删除 `namespace` 行。
5. 遗留系统组织（固定 UUID `00000000-0000-0000-0000-000000000001`）：存在且 `namespace.naming_authority_id`、`contact.organization_id` 均无引用时，删 `organization_name`/`organization_email`/`organization_phone`，再删 `organization`。
6. 方言差异：SQLite 的 UUID 列为 BLOB 存储（`X'…'` 字面量），SQL Server/达梦为字符串/uniqueidentifier 语义；迁移脚本按各方言既有迁移的写法编写。

### 3.3 前端（metadata-registry-ui）

| 文件 | 变更 |
|---|---|
| `components/ItemRegistrationSection.vue` | 删除 `SYSTEM_DEFAULT_SCHEME` 常量、`gateBlocked` 判定、默认推导中的 `schemeReference !== SYSTEM_DEFAULT_SCHEME` 条件 |
| `components/ItemRegistrationSection.spec.ts` | 删除"system-default 命名空间不参与默认推导"用例 |
| `components/ItemFormDialog.vue` | 第 637 行注释更新 |

### 3.4 测试（后端）

- 删除 `seed/SystemDefaultNamespaceInitializerTest.java`。
- `NamespaceGateIntegrationTest`：删除 `createDataElement_systemDefaultNamespace_blocked` 用例（外部命名空间用例已覆盖无 RA 路径）。
- `ItemAdministrationServiceTest`：改造 5 处——scheme=system-default 的用例改为验证"无 RA 命名空间按外部命名空间处理"；`findFirstByRegistrationAuthority_IdAndSchemeReferenceNot` 的 mock 改为新 finder。
- `InitWizardServiceIntegrationTest`：移除 sys: NamingAuthority 挂接断言，改为断言 setup 后 namespace 表仅含向导创建的命名空间。
- `FactoryResetServiceTest`、`DataGovernanceReadinessServiceTest`：注释/描述更新。

### 3.5 OpenSpec 活跃 spec 修订

- `openspec/specs/system-initialization/spec.md`：静态预置集合移除"系统组织与 `sys:` 系统默认命名空间"；删除 sys: NamingAuthority 挂接要求；系统组织段落改写（不再以 sys: 为存在理由）。
- `openspec/specs/identification/spec.md`：删除"系统内部命名空间（system-default）SHALL NOT 计入该前置条件"。
- `openspec/specs/default-namespace-by-active-registrar/spec.md`：默认推导边界移除 system-default（Requirement 与 Scenario）。
- `openspec/specs/active-registrar-ownership/spec.md`："非 system-default"措辞改为"无关联注册机构"。

### 3.6 文档与基线

- `db-baseline/README.md`、`db-baseline/*.sql` 头部注释：移除 sys: 相关表述（数据本身不含 sys: 行，仅注释残留）。
- 按签入前文档同步规则检查 `README.md`、`docs/architecture/ARCHITECTURE.md`。

## 4. 非目标

- Registry Context 承载于注册机构自有命名空间的设计不变（与 sys: 无关）。
- 不新增"命名空间自身被识别/发标识"能力；若未来需要，届时按"上一级命名空间"重新立项。
- 外部命名空间（EXTERNAL / 无 RA 关联）机制不变。
- 不重新构建提交前端 dist 产物（构建产物由常规构建更新）。

## 5. 验收标准

1. 三方言迁移在空库、含 sys: 存量库、无 sys: 库上均可成功执行且可重复执行不报错。
2. 存量库迁移后：`namespace` 表无 `scheme_reference = 'urn:metadata-registry:system-default'` 行；无引用的 …0001 组织行被清除；向导创建的注册机构自有命名空间不受影响。
3. 全新系统向导初始化完成后，`namespace` 表恰好 1 行（注册机构自有命名空间）。
4. `getWizardState` 在"库设置完成重启后、向导未执行"时返回 `READY`（nsCount 不再被 sys: 污染）。
5. `metadata-registry-web` 全量测试通过；`metadata-registry-ui` 相关前端测试通过。
6. `openspec status --change remove-system-default-namespace` 全部 artifact 就绪，spec 修订与实际行为一致。

## 6. 风险与缓解

| 风险 | 缓解 |
|---|---|
| 存量库中 sys: 被用户手工用于存放条目 | 迁移前的 scoped_identifier 防御检查：有引用则跳过删除；跳过的残留行在代码中按"无 RA 关联"走外部命名空间路径，无特判 |
| designation 关联表外键删除顺序 | 实施时按实库外键依赖确认删除顺序（先子后父） |
| 测试 mock 大面改造引入回归 | 按 3.4 清单逐个改造，全量测试验证 |
