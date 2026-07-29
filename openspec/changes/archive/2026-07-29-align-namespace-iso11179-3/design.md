## Context

`Namespace` 当前已实现为 `Item` 子类，具备 `shorthandPrefix`、`schemeReference`、三个 indicator 等治理属性（见 `metadata-registry-common-facilities/.../identification/Namespace.java`）。`NamingConvention` 实体已创建（Clause 8.4.4）。本次变更的目标是按 ISO/IEC 11179-3:2023 修正以下偏差：

- `NamespaceStatus` 是项目自行引入的枚举/状态机，标准中不存在，需完全移除。
- `Namespace.registrationAuthority` 与 `RegistrationAuthority.namespace` 双向可选关联需保留并澄清语义（0..1）。
- `Designation` 与 `NamingConvention` 的 `naming_convention_conformance` 关联已加，但业务层未强制校验。
- `mandatory_naming_convention_indicator` 等三大约束未在 `DesignationService` / `ScopedIdentifierService` 中执行。
- `NamingConvention` 没有 Service/Controller/DTO，无法通过 API 创建。
- 旧 `naming_authority` 字符串列需迁移到 `Organization` 外键。

## Goals / Non-Goals

**Goals：**
1. 使 `Namespace` 模型与 ISO/IEC 11179-3:2023 Clause 7.3.3 / 8.3.1 / 8.4.4 / 9.5.5 在属性类型与关联语义上完全一致。
2. 实现 `NamingConvention` 的可管理化（补齐 Service、Controller、DTO、Repository）。
3. 在 `NamespaceService`、`DesignationService` 和 `ScopedIdentifierService` 中强制 Namespace 三大约束。
4. 统一 Namespace CRUD DTO，确保 API 暴露完整 ISO 治理属性。
5. 提供 Flyway 迁移，兼容已有 Namespace 数据。

**Non-Goals：**
1. 不实现 Classification 包（Clause 10）和 Mapping 包（Clause 11）。
2. 不实现 `Scoped_Identifier` 的派生属性 `full_expansion` / `shorthand_expansion`（需单独提案）。
3. 不修改 `AdministeredItem` 的生命周期状态机本身；仅移除 `NamespaceStatus`，统一使用 `RegistrationState`。
4. 不替换现有 `NamingConventionEngine` 的全局规则引擎，而是新增按 Namespace 的 per-convention 校验入口。
5. 不新增前端 UI 字段（可选后续任务）。

## Decisions

### Decision 1：完全移除 `NamespaceStatus`
- **理由**：用户明确“NamespaceStatus 不建议保留，因为原标准文档中没有”。标准中 Namespace 作为 `Item` 子类，其生命周期应由 `AdministeredItem → RegistrationState` 表达。
- **实现**：删除 `NamespaceStatus.java` 枚举、`Namespace.namespaceStatus` 字段、`NamespaceStateMachine` 服务及测试；从所有 DTO 中移除 `namespaceStatus`；新增 Flyway 迁移 `V20260724002__Drop_namespace_status.sql` 删除数据库列。
- **影响**：涉及多个文件变更，需确保无遗留引用。

### Decision 2：保留 `Namespace.registrationAuthority` 与 `RegistrationAuthority.namespace` 双向可选关联（0..1）
- **理由**：用户明确“Namespace.registrationAuthority 和 RegistrationAuthority.namespace，应该都要的，虽然这两个关系都是可选的，也就是 0..1”。标准 Clause 9.5.5 定义了 `registration_authority_namespace` 关联，双向导航在项目中有实际查询需求。
- **实现**：保留双向 `@OneToOne`，`RegistrationAuthority.namespace` 使用 `mappedBy = "registrationAuthority"`；在 Javadoc 中分别说明语义；DTO 侧不暴露 `registrationAuthorityId`，避免误读为“RA 管辖 Namespace”。
- **影响**：实体层保留双向关联，API 层保持简洁。

### Decision 3：`NamingConvention` 走完整的 `Item` 生命周期，提供独立 REST 端点
- **理由**：标准中 `Naming_Convention` 继承 `Item`，可被命名、定义、注册、分类。当前只有实体和 Repository，无法独立管理。
- **实现**：新增 `NamingConventionController`、`NamingConventionService`、`NamingConventionRequest`/`Response`，路径 `/api/v1/naming-conventions`。
- **影响**：前端或管理员可以先创建 NamingConvention，再在 Namespace 中引用。

### Decision 4：约束执行放在业务服务层，而非数据库触发器
- **理由**：约束涉及 `Designation`、`ScopedIdentifier`、`Namespace` 三表语义，数据库触发器难以表达“同一 Item 最多一个 Designation”等业务规则；且项目现有模式（`NamingConflictDetector`）已在服务层校验。
- **替代方案**：数据库唯一约束（如 `UNIQUE(namespace_id, sign)`）可保证 `one_item_per_name`，但无法表达 `mandatory_naming_convention` 的多对多规则。因此服务层主导，数据库辅助唯一索引。
- **影响**：需要在 `NamespaceService.create/update`、`DesignationService.create/update`、`ScopedIdentifierService.create/update` 中调用新的 `NamespaceConstraintValidator`。

### Decision 5：统一普通 CRUD 与 Wizard 使用完整字段集
- **理由**：当前 `NamespaceRequest` 缺少治理字段，仅 `NamespaceWizardRequest` 暴露，导致客户端依赖 Wizard 路径。标准属性应在所有入口一致。
- **实现**：`NamespaceRequest` 增加 `namingAuthorityId`、`shorthandPrefix`、`schemeReference`、三个 indicator、`namingConventionIds`、`parentId`；`NamespaceResponse` / `NamespaceDetailResponse` 返回完整治理字段；`NamespaceWizardRequest` 作为兼容层继续存在，内部调用同一 `applyRequest` 逻辑。
- **影响**：前端需更新普通 Namespace 创建表单以发送完整字段；后端保留 Wizard 端点避免立即破坏。

## Risks / Trade-offs

| 风险 | 缓解措施 |
|------|----------|
| 旧 `naming_authority` 字符串列迁移导致已有 Namespace 数据丢失 | Flyway 迁移脚本先新增 `naming_authority_id`，将现有字符串值映射到系统默认 Organization；再删除旧列；无法映射时回滚到默认 Organization。 |
| `NamespaceStatus` 移除破坏外部调用方 | 同步删除 DTO 字段与相关状态机端点；API 文档中说明生命周期改为 `RegistrationState`。 |
| 约束执行影响现有数据导入性能 | `NamespaceConstraintValidator` 在创建/更新时按需加载，批量导入场景可提供 `skipNamespaceValidation` 标志（未来增强，不在本次实现）。 |
| `NamingConvention` 规则内容无统一表达 | `scopeRule`/`authorityRule` 使用 `Text` 类型存储自然语言规则；机器可执行规则由项目扩展字段或后续 `NamingConventionEngine` 接入，本次不强制。 |
| 前端 DTO 字段变化 | 在 API 文档和提案中标记 **BREAKING**；同步更新前端表单组件（可选后续任务）。 |

## Migration Plan

1. **数据库迁移（Flyway）**
   - 新增 `naming_authority_id`（UUID，可空，外键指向 `organization`）。
   - 新增 `namespace_naming_convention` 连接表。
   - 新增 `designation_naming_convention` 连接表。
   - 对已有 Namespace，将旧 `naming_authority` 字符串值匹配到 `Organization`；未匹配到的写入系统默认 Organization。
   - 删除旧 `naming_authority` 字符串列。
   - 新增迁移 `V20260724002__Drop_namespace_status.sql` 删除 `namespace_status` 列。
2. **应用部署**
   - 启动时 `SystemDefaultNamespaceInitializer` 自动创建 system Organization 并绑定到 system Namespace。
   - 运行集成测试验证迁移后数据一致性。
3. **回滚策略**
   - Flyway 脚本提供 down 脚本：恢复字符串列，将 `naming_authority_id` 反向写回字符串（Organization 名称），删除外键与连接表。
   - 代码回滚：保留旧 DTO 字段一个版本，使用 `@Deprecated` 标记，逐步下线。

## Open Questions

1. `NamingConvention` 的机器可执行规则是否需要与现有 `NamingConventionEngine` 对接？本次仅存储自然语言规则，执行层面由 validator 检查“是否关联”而非“是否匹配规则”。
2. 是否需要为批量导入提供 `skipNamespaceValidation` 开关以提升性能？不在本次范围，可后续讨论。
3. 是否需要在前端支持 Namespace 治理属性输入（`shorthandPrefix`、`schemeReference`、三个 indicator、`namingConventionIds`、`parentId`、`namingAuthorityId`）？建议另开独立任务处理 UI 字段。
