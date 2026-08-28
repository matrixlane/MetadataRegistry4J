# 设计：持久化 Item 标识符与版本的 AUTO/MANUAL 创建选择

## Context

`identification` 现行"编辑态标识符/版本语义统一"要求规定：编辑态标识符不可变（始终禁用）、版本可编辑并按命名空间版本策略递增（显式值优先于自动递增）。这一规则不区分创建时用户的"自动生成 / 手动输入"选择，且完全没有把这个选择作为持久化元数据留下来。

新业务诉求：

- **编辑会话一致性**：把创建时用户在 inline 控件上选择的 AUTO/MANUAL 状态持久化，编辑态据此锁定（创建时为 AUTO）或开放（创建时为 MANUAL）。
- **元数据可回溯**：将来通过 API 或数据库可查到每个 Item 的创建选择（AUTO 还是 MANUAL），便于审计、命名空间策略迁移、联邦合并等场景使用。

具体生成策略类型（`UUID` / `URN` / `SEQUENCE` / `TEMPLATE` / `SEMVER` / `SEQUENTIAL` / `DATE`）属于命名空间级配置（`IdentifierStrategyConfig.strategyType` / `Namespace.versioningPolicy`），全局统一，**不在 SI 上重复记录**。

当前相关状态：

- `ScopedIdentifier`（`metadata-registry-common-facilities/identification/`）字段 `identifier`、`namespace`、`version`、`versionStatus` 等；不含 mode 字段。
- `ItemRegistrationRequest`（`metadata-registry-web/dto/common/`）当前仅含 `namespaceId` / `identifier` / `version` / `designations` / `definitions` / `variables`；无 mode 字段。
- `ItemAdministrationService.ensureAdministeredItem`：创建路径无 mode 概念；编辑路径在 `else` 分支中按 `requestedVersion` 与 `si.getVersion()` 比较决定是接受显式版本还是按 `Versions.bump` 递增；identifier 仅在 `ai.getScopedIdentifiers().size() == 1` 且请求体有值时改写。
- `IdentifierInputPolicyService.resolveIdentifier/resolveVersion` 完全依赖命名空间 `identifierInputMode` / `versionInputMode` 与版本策略 `versioningPolicy`，不知道 SI 自身的 mode。
- 前端 `ItemRegistrationSection.vue`：编辑态 `identifierDisabled = isEditing || identifierAutoInEffect` → 编辑态下标识符始终禁用；`versionDisabled = versionAutoInEffect` → 编辑态下版本默认可编辑。
- 命名空间策略字段：`identifierInputMode` / `versionInputMode`（AUTO_ONLY / AUTO_WITH_OVERRIDE / MANUAL_ONLY）；`versioningPolicy`（SEMVER / SEQUENTIAL / DATE）；不影响本次改动。

## Goals / Non-Goals

**Goals：**

- 在 `ScopedIdentifier` 上持久化创建时用户选择的 `identifierCreationMode` / `versionCreationMode`（AUTO / MANUAL，可空）。
- 编辑态依据持久化的 mode 字段决定 UI 与后端可编辑性：AUTO → 锁定；MANUAL → 开放。
- 响应体（`ScopedIdentifierResponse` / `ItemMetaResponse`）暴露 mode 字段，便于任意时刻通过 API 检索。
- 既存记录（迁移前已存在的 SI）保持当前语义：通过 NULL 字段表达"legacy"，等价 MANUAL（不破坏既有数据）。
- 三方言数据库可移植。

**Non-Goals：**

- 不开放"创建后变更 mode"的能力——mode 创建即固化。
- 不在 SI 上记录具体生成策略类型（UUID / URN / SEQUENCE / TEMPLATE / SEMVER / SEQUENTIAL / DATE）。
- 不改命名空间策略本身的语义与字段。
- 不动联邦合并路径的核心逻辑：合并执行器显式复制来源方的 mode 字段到新建 SI 即可。
- 不引入专门审计日志（沿用现有 `lastChangeDateTime` 即可）。

## Decisions

### 决策 1：字段类型与持久化

新增枚举类 `IdentifierCreationMode`（`AUTO` / `MANUAL`），仅用于表示创建时用户在 inline 控件上的选择；与命名空间级 `IdentifierInputMode`（AUTO_ONLY / AUTO_WITH_OVERRIDE / MANUAL_ONLY）解耦。

```java
// metadata-registry-common-facilities/.../identification/IdentifierCreationMode.java
public enum IdentifierCreationMode {
    AUTO,    // 创建时按命名空间策略自动生成
    MANUAL   // 创建时由用户手动录入
}
```

```java
// metadata-registry-common-facilities/.../identification/ScopedIdentifier.java
@Enumerated(EnumType.STRING)
@Column(name = "identifier_creation_mode", length = 16)
private IdentifierCreationMode identifierCreationMode; // 可空

@Enumerated(EnumType.STRING)
@Column(name = "version_creation_mode", length = 16)
private IdentifierCreationMode versionCreationMode;   // 可空
```

`length = 16` 足以容纳 `AUTO` / `MANUAL` 字符串。NULL 表示 legacy / 外部命名空间 / 旧客户端未携带 mode 的情形。

### 决策 2：API 请求体扩展

`ItemRegistrationRequest` 新增两个可选枚举字段（不为空字符串 / null，避免向后兼容问题）：

```java
record ItemRegistrationRequest(
    UUID namespaceId,
    String identifier,
    String version,
    List<TextLine> designations,
    List<TextLine> definitions,
    Map<String, String> variables,
    IdentifierCreationMode identifierMode,   // 新增；NULL 表示按 identifier 反推
    IdentifierCreationMode versionMode      // 新增；NULL 表示按 version 反推
) {
    // 向后兼容：原 5 参构造委托至新构造，identifierMode/versionMode=null
}
```

便利静态方法 `IdentifierCreationMode resolveMode(String userSupplied)`：blank → AUTO，非空 → MANUAL；供后端在请求未携带 mode 时反推。

### 决策 3：API 响应体扩展

- `ScopedIdentifierResponse` 新增 `identifierCreationMode` / `versionCreationMode`（均可空）。
- `ItemMetaResponse` 新增同名字段；`ItemAdministrationService.buildItemMeta` 在 `preferPrimaryIdentifier(siList)` 取到 SI 后读取这两个字段并写入响应；NULL 时返回 null。
- 暴露这两个字段便于前端编辑态决定 UI 与将来 API 用户检索创建选择。

### 决策 4：后端创建路径

`ItemAdministrationService.ensureAdministeredItem` 中创建分支（无既有 SI）：

```java
if (ai.getScopedIdentifiers() == null || ai.getScopedIdentifiers().isEmpty()) {
    ScopedIdentifier si = new ScopedIdentifier();
    // 生成 / 接受 identifier 与 version（沿用现有逻辑）
    try {
        identifier = identifierInputPolicyService.resolveIdentifier(ns, req.identifier(),
                Hibernate.getClass(item).getSimpleName(), req.variables());
    } catch (...) { ... }
    si.setIdentifier(identifier);
    si.setVersion(identifierInputPolicyService.resolveVersion(ns, req.version()));

    // 持久化 mode（本次新增）
    si.setIdentifierCreationMode(
        req.identifierMode() != null
            ? req.identifierMode()
            : ItemRegistrationRequest.resolveMode(req.identifier()));
    si.setVersionCreationMode(
        req.versionMode() != null
            ? req.versionMode()
            : ItemRegistrationRequest.resolveMode(req.version()));

    si.setNamespace(ns);
    si.setIdentifiedItem(ai);
    ai.getScopedIdentifiers().add(si);
}
```

`IdentifierInputPolicyService` 不修改：保留命名空间级判定，本变更的所有 mode 决策都集中在 `ItemAdministrationService` 中。

### 决策 5：后端编辑路径

`else` 分支（已有 SI）增加 mode 读取：

```java
} else {
    ScopedIdentifier si = selectEditableIdentifier(ai, ns);

    // 标识符：AUTO 时不允许任何修改；MANUAL / NULL 时沿用单 SI 可改逻辑
    String requestedIdentifier = req.identifier();
    if (requestedIdentifier != null && !requestedIdentifier.isBlank()
            && !Objects.equals(requestedIdentifier, si.getIdentifier())) {
        if (si.getIdentifierCreationMode() == IdentifierCreationMode.AUTO) {
            throw new BusinessException(VALIDATION_FAILED,
                    "该标识符创建时选择自动生成，不允许手动修改");
        }
        if (ai.getScopedIdentifiers().size() == 1) {
            si.setIdentifier(requestedIdentifier);
        }
    }

    // 版本：AUTO 时忽略请求体显式值；MANUAL / NULL 时接受并校验
    String requestedVersion = req.version();
    if (si.getVersionCreationMode() == IdentifierCreationMode.AUTO) {
        si.setVersion(Versions.bump(si.getVersion(),
                ns != null ? ns.getVersioningPolicy() : null));
    } else if (requestedVersion != null && !requestedVersion.isBlank()
            && !requestedVersion.equals(si.getVersion())) {
        identifierInputPolicyService.validateManualVersion(ns, requestedVersion);
        si.setVersion(requestedVersion);
    } else {
        si.setVersion(Versions.bump(si.getVersion(),
                ns != null ? ns.getVersioningPolicy() : null));
    }
    // ... 其余沿用
}
```

mode 字段本身在编辑过程中不变（创建即锁定，不引入"反向能力"）。

### 决策 6：前端编辑态 props 与展示

`ItemRegistrationSection.vue` 新增可选 props：

```ts
interface Props {
  isEditing?: boolean;
  registrationAuthorityName?: string | null;
  // 新增
  identifierCreationMode?: 'AUTO' | 'MANUAL' | null;
  versionCreationMode?: 'AUTO' | 'MANUAL' | null;
}
```

`ItemFormDialog.vue` 在打开编辑时读取 `ItemMetaResponse` 中两个字段透传给 Section。Section 内计算属性：

- `wasIdentifierAuto = computed(() => props.isEditing && props.identifierCreationMode === 'AUTO')` —— 编辑态且 mode 为 AUTO。
- `wasVersionAuto = computed(() => props.isEditing && props.versionCreationMode === 'AUTO')`。
- `showIdentifierChoice`：`!isEditing && identifierMode === 'AUTO_WITH_OVERRIDE'`（保持原逻辑；编辑态不再展示切换控件）。
- `identifierDisabled`：当 `wasIdentifierAuto` 为 true → 输入框 MUST 禁用、占位符"自动生成不可编辑"。
- `versionDisabled`：当 `wasVersionAuto` 为 true → 输入框 MUST 禁用、占位符"自动生成不可编辑"。
- `buildPayload`：编辑 AUTO 时显式把 `identifier` / `version` 设为 `null` 并填 `identifierMode: 'AUTO'` / `versionMode: 'AUTO'`；编辑 MANUAL 时按既有逻辑。

### 决策 7：数据库迁移

三种方言等价迁移脚本（命名 `V20260828001__Add_scoped_identifier_creation_mode_columns.sql`）：

- SQL Server：`ALTER TABLE scoped_identifier ADD identifier_creation_mode nvarchar(16) NULL; ALTER TABLE scoped_identifier ADD version_creation_mode nvarchar(16) NULL;`
- SQLite：`ALTER TABLE scoped_identifier ADD COLUMN identifier_creation_mode TEXT; ALTER TABLE scoped_identifier ADD COLUMN version_creation_mode TEXT;`
- 达梦：`ALTER TABLE scoped_identifier ADD identifier_creation_mode VARCHAR2(16); ALTER TABLE scoped_identifier ADD version_creation_mode VARCHAR2(16);`

不做数据回填（NULL = legacy = MANUAL）。

JPA：`@Enumerated(EnumType.STRING)` + `@Column(length = 16)` 即可，无需方言特化。

### 决策 8：范围与豁免

- 命名空间（`Namespace`）上不增加新字段。
- 标识符生成策略（`IdentifierStrategyConfig`）不增加新字段。
- 联邦合并路径：合并执行器显式复制来源方的两个 mode 字段到新建 SI 上（沿用现有合并实现）；本变更仅暴露字段与持久化能力，不修改合并核心逻辑。
- 审计日志：本变更对 SI 的修改仍由 `lastChangeDateTime` 体现。

## Risks / Trade-offs

- [既存 SI（NULL mode）按 MANUAL 处理，标识符变得可编辑] → 与既有前端禁用不一致。变更说明 / 培训材料中标注"迁移后第一次打开既存记录编辑表单，标识符输入框将变为可编辑"。
- [三类方言迁移脚本需逐个验证] → tasks 阶段跑 SQLite + SQL Server 集成测试，DM 走 `mvn -Pdm` 集成测试。
- [前端 `ItemRegistrationSection` 新增 props 触发宿主 regression] → 保持新增字段全部可选（带默认值），`ItemFormDialog` 仅做透传。
- [旧客户端不带 mode 字段的请求体] → 反推逻辑与现有行为对齐（identifier 是否为空），不引入新校验错误。
- [后端 `ScopedIdentifierResponse` / `ItemMetaResponse` 新增字段，旧客户端忽略] → 严格保持非破坏性，纯新增。

## Migration Plan

纯代码 + 一次数据库迁移即可。回滚：删除迁移脚本 + 还原代码变更；NULL 字段下原有数据无副作用。

## Open Questions

1. 既存 NULL 记录的标识符变为可编辑（前后端一致性）是否需要单独公告 / UI 提示？倾向：在 `ItemRegistrationSection` 占位符提示"原记录无 mode 信息，按手动模式处理"。
2. 是否需要在 SI 上额外记录具体生成策略类型（UUID / URN / SEQUENCE / TEMPLATE / SEMVER / DATE）以满足更细粒度审计诉求？本次明确不做（用户已确认策略是命名空间级全局统一）。
3. AUTO 模式下后端是否在某些触发条件下主动重新生成（命名空间策略升级等）？本次不实现，留作后续能力。