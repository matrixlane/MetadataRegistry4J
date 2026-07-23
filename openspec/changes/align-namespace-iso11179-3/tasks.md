## Task Breakdown

### 1. 移除非标准 NamespaceStatus
- [x] 1.1 删除 `NamespaceStatus.java` 枚举。
- [x] 1.2 从 `Namespace.java` 移除 `namespaceStatus` 字段。
- [x] 1.3 从 `NamespaceRequest`/`NamespaceResponse`/`NamespaceDetailResponse` 移除 `namespaceStatus`。
- [x] 1.4 删除 `NamespaceStateMachine.java` 及对应测试。
- [x] 1.5 创建 Flyway 迁移 `V20260724002__Drop_namespace_status.sql` 删除 `namespace_status` 列。

### 2. 保留并澄清 Namespace ↔ RegistrationAuthority 双向可选关联
- [x] 2.1 保留 `Namespace.registrationAuthority`（`@OneToOne`，可选）。
- [x] 2.2 保留 `RegistrationAuthority.namespace`（`mappedBy = "registrationAuthority"`，可选）。
- [x] 2.3 更新 Javadoc，明确 Clause 9.5.5 语义：RA 的标识符受 Namespace 约束。
- [x] 2.4 DTO 侧不暴露 `registrationAuthorityId`，避免语义误读。

### 3. 补齐 NamingConvention 管理 API
- [x] 3.1 创建 `NamingConvention` 实体（继承 `Item`，含 `scopeRule`、`authorityRule`）。
- [x] 3.2 创建 `NamingConventionRequest`/`NamingConventionResponse` DTO。
- [x] 3.3 创建 `NamingConventionRepository`。
- [x] 3.4 创建 `NamingConventionService`。
- [x] 3.5 创建 `NamingConventionController`，路径 `/api/v1/naming-conventions`。
- [x] 3.6 为 `NamingConventionService` 和 `NamingConventionController` 添加单元/集成测试。

### 4. 实现 Namespace 三大约束校验
- [x] 4.1 创建 `NamespaceConstraintValidator` 组件。
- [x] 4.2 实现 `mandatoryNamingConventionIndicator` 校验。
- [x] 4.3 实现 `oneNamePerItemIndicator` 校验。
- [x] 4.4 实现 `oneItemPerNameIndicator` 校验。
- [x] 4.5 扩展 `NamingConflictDetector`，支持 ScopedIdentifier 冲突检测。
- [x] 4.6 在 `NamespaceService.create/update` 中调用 `validateNamespace`。
- [x] 4.7 在 `DesignationService.create/update` 中调用 `validateDesignation`。
- [x] 4.8 在 `ScopedIdentifierService.create/update` 中调用 `validateScopedIdentifier`。
- [x] 4.9 新增 `NamespaceConstraintValidatorTest` 覆盖通过/失败场景。

### 5. 统一 Namespace DTO 与迁移
- [x] 5.1 `NamespaceRequest` 补齐 `namingAuthorityId`、`shorthandPrefix`、`schemeReference`、三个 indicator、`namingConventionIds`、`parentId`。
- [x] 5.2 `NamespaceResponse` / `NamespaceDetailResponse` 返回完整治理字段。
- [x] 5.3 创建 Flyway 迁移 `V20260724001__Align_namespace_iso11179_model.sql`：新增 `naming_authority_id`、`registration_authority_id`、连接表等。
- [x] 5.4 更新 `SystemDefaultNamespaceInitializer`，确保系统 Namespace 绑定到系统 Organization。
- [x] 5.5 更新 `DataInitializer`，为示例 Namespace 设置 `namingAuthority` 和 NamingConvention 关联。

### 6. 测试与验证
- [x] 6.1 更新 `NamespaceServiceTest`、`NamespaceControllerTest`、`DesignationServiceTest`、`ScopedIdentifierServiceTest`、`SystemDefaultNamespaceInitializerTest`。
- [x] 6.2 运行目标测试套件，确认 78 个测试全部通过：
  ```bash
  .\mvnw.cmd -pl metadata-registry-web test "-Dtest=NamespaceTest,NamespaceServiceTest,NamespaceControllerTest,DesignationServiceTest,NamingConflictDetectorTest,ScopedIdentifierServiceTest,NamespaceConstraintValidatorTest,NamingConventionServiceTest,NamingConventionControllerTest,SystemDefaultNamespaceInitializerTest"
  ```

### 7. 文档（OpenSpec）
- [x] 7.1 编写 `proposal.md`。
- [x] 7.2 编写 `design.md`。
- [x] 7.3 编写 `tasks.md`。
- [x] 7.4 编写 `specs/namespace-iso11179-model/spec.md`。
- [x] 7.5 编写 `specs/namespace-constraint-enforcement/spec.md`。
- [x] 7.6 编写 `specs/naming-convention-management/spec.md`。

### 8. 可选后续（不在本次范围）
- [ ] 8.1 前端表单增加 Namespace 治理属性输入字段。
- [ ] 8.2 将 `NamingConvention` 的自然语言规则与 `NamingConventionEngine` 的机器可执行规则对接。
- [ ] 8.3 批量导入场景下提供 `skipNamespaceValidation` 开关以提升性能。
