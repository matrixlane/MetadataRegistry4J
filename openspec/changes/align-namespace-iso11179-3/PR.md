## PR 描述：Namespace 与 ISO/IEC 11179-3:2023 对齐

### 变更摘要

本 PR 将 `Namespace` 及关联模型与 ISO/IEC 11179-3:2023 对齐，主要包含：

1. **移除非标准 `NamespaceStatus`**：删除 `NamespaceStatus` 枚举、`NamespaceStateMachine` 服务及 DTO/数据库中的 `namespaceStatus` 字段；Namespace 生命周期统一由 `AdministeredItem → RegistrationState` 表达。
2. **保留并澄清双向可选 RA 关联**：`Namespace.registrationAuthority` 与 `RegistrationAuthority.namespace` 均保留为 `0..1`，并在 Javadoc 中明确 Clause 9.5.5 语义。
3. **补齐 `NamingConvention` 管理 API**：新增 `NamingConvention` 实体、Service、Controller、Repository、DTO，端点 `/api/v1/naming-conventions`。
4. **实现 Namespace 三大约束校验**：新增 `NamespaceConstraintValidator`，在 `NamespaceService` / `DesignationService` / `ScopedIdentifierService` 中调用，覆盖 `mandatoryNamingConventionIndicator`、`oneNamePerItemIndicator`、`oneItemPerNameIndicator`。
5. **统一 Namespace DTO**：`NamespaceRequest` / `NamespaceResponse` / `NamespaceDetailResponse` 暴露完整 ISO 治理属性。
6. **数据库迁移**：新增 Flyway 迁移脚本，处理 `naming_authority` 外键、`namespace_naming_convention` 与 `designation_naming_convention` 连接表、以及 `namespace_status` 列的删除。

### 主要变更文件

**实体与模型**
- `metadata-registry-common-facilities/src/main/java/io/github/matrixlane/metadataregistry/commonfacilities/identification/Namespace.java`
- `metadata-registry-common-facilities/src/main/java/io/github/matrixlane/metadataregistry/commonfacilities/registration/RegistrationAuthority.java`
- `metadata-registry-common-facilities/src/main/java/io/github/matrixlane/metadataregistry/commonfacilities/designationanddefinition/NamingConvention.java`（新增）
- `metadata-registry-common-facilities/src/main/java/io/github/matrixlane/metadataregistry/commonfacilities/identification/NamespaceStatus.java`（删除）

**服务与校验**
- `metadata-registry-web/src/main/java/io/github/matrixlane/metadataregistry/web/service/namespace/NamespaceConstraintValidator.java`（新增）
- `metadata-registry-web/src/main/java/io/github/matrixlane/metadataregistry/web/service/NamingConventionService.java`（新增）
- `metadata-registry-web/src/main/java/io/github/matrixlane/metadataregistry/web/service/NamespaceService.java`
- `metadata-registry-web/src/main/java/io/github/matrixlane/metadataregistry/web/service/DesignationService.java`
- `metadata-registry-web/src/main/java/io/github/matrixlane/metadataregistry/web/service/ScopedIdentifierService.java`
- `metadata-registry-web/src/main/java/io/github/matrixlane/metadataregistry/web/service/namingconvention/NamingConflictDetector.java`
- `metadata-registry-web/src/main/java/io/github/matrixlane/metadataregistry/web/service/NamespaceStateMachine.java`（删除）

**控制器与 DTO**
- `metadata-registry-web/src/main/java/io/github/matrixlane/metadataregistry/web/controller/NamingConventionController.java`（新增）
- `metadata-registry-web/src/main/java/io/github/matrixlane/metadataregistry/web/dto/namespace/NamespaceRequest.java`
- `metadata-registry-web/src/main/java/io/github/matrixlane/metadataregistry/web/dto/namespace/NamespaceResponse.java`
- `metadata-registry-web/src/main/java/io/github/matrixlane/metadataregistry/web/dto/namespace/NamespaceDetailResponse.java`
- `metadata-registry-web/src/main/java/io/github/matrixlane/metadataregistry/web/dto/namingconvention/*`（新增）

**数据库迁移**
- `metadata-registry-web/src/main/resources/db/migration/V20260724001__Align_namespace_iso11179_model.sql`（新增）
- `metadata-registry-web/src/main/resources/db/migration/V20260724002__Drop_namespace_status.sql`（新增）

**测试**
- `metadata-registry-web/src/test/java/io/github/matrixlane/metadataregistry/web/service/namespace/NamespaceConstraintValidatorTest.java`（新增）
- `metadata-registry-web/src/test/java/io/github/matrixlane/metadataregistry/web/service/NamingConventionServiceTest.java`（新增）
- `metadata-registry-web/src/test/java/io/github/matrixlane/metadataregistry/web/controller/NamingConventionControllerTest.java`（新增）
- `metadata-registry-web/src/test/java/io/github/matrixlane/metadataregistry/web/service/NamespaceStateMachineTest.java`（删除）
- 更新：`NamespaceServiceTest`、`NamespaceControllerTest`、`DesignationServiceTest`、`ScopedIdentifierServiceTest`、`NamingConflictDetectorTest`、`SystemDefaultNamespaceInitializerTest`、`NamespaceTest`

### 破坏性变更

- `NamespaceStatus` 枚举及 `namespaceStatus` 字段已完全移除；外部调用方需改用 `AdministeredItem.registrationState`。
- `Namespace` DTO 不再暴露 `registrationAuthorityId`；如需从 Namespace 查找 RA，请使用 `RegistrationAuthorityRepository.findByNamespaceId`。
- `POST /api/v1/namespaces` 与 `PUT /api/v1/namespaces/{id}` 现在接受完整治理字段。

### 测试

运行命令：
    .\mvnw.cmd -pl metadata-registry-web test "-Dtest=NamespaceTest,NamespaceServiceTest,NamespaceControllerTest,DesignationServiceTest,NamingConflictDetectorTest,ScopedIdentifierServiceTest,NamespaceConstraintValidatorTest,NamingConventionServiceTest,NamingConventionControllerTest,SystemDefaultNamespaceInitializerTest"

结果：**78 tests, 0 failures, 0 errors, 0 skipped**

### 关联文档

- `openspec/changes/align-namespace-iso11179-3/proposal.md`
- `openspec/changes/align-namespace-iso11179-3/design.md`
- `openspec/changes/align-namespace-iso11179-3/tasks.md`
- `ISO_11179-3_2023_Comparison_Report.md`
