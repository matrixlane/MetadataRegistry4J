## 1. 领域模型与持久化

- [ ] 1.1 在 `metadata-registry-common-facilities/.../identification/` 新增 `IdentifierCreationMode` 枚举类（AUTO / MANUAL），配套 JavaDoc 标注其与命名空间级 `IdentifierInputMode` 的区别
- [ ] 1.2 `ScopedIdentifier.java` 新增字段 `identifierCreationMode`、`versionCreationMode`，`@Enumerated(EnumType.STRING)`，`length=16`，可空
- [ ] 1.3 不在 SI 上记录具体生成策略类型（UUID / URN / SEQUENCE / TEMPLATE / SEMVER / SEQUENTIAL / DATE）—— 这些由命名空间级配置全局承载

## 2. 数据库迁移（三方言）

- [ ] 2.1 `metadata-registry-web/.../db/migration/sqlserver/V20260828001__Add_scoped_identifier_creation_mode_columns.sql`：使用 `COL_LENGTH` 守卫后 `ALTER TABLE scoped_identifier ADD identifier_creation_mode nvarchar(16) NULL` 与 `version_creation_mode nvarchar(16) NULL`
- [ ] 2.2 `metadata-registry-web/.../db/migration/sqlite/V20260828001__Add_scoped_identifier_creation_mode_columns.sql`：`ALTER TABLE scoped_identifier ADD COLUMN identifier_creation_mode TEXT` / `version_creation_mode TEXT`（SQLite 3.35+ 支持 ADD COLUMN，不需守卫）
- [ ] 2.3 `metadata-registry-web/.../db/migration/dm/V20260828001__Add_scoped_identifier_creation_mode_columns.sql`：使用 `EXECUTE IMMEDIATE` 包裹 `ALTER TABLE scoped_identifier ADD identifier_creation_mode VARCHAR2(16)` 与 `version_creation_mode VARCHAR2(16)`，配 `BEGIN ... EXCEPTION WHEN OTHERS THEN NULL; END;` 块

## 3. API DTO 与请求体

- [ ] 3.1 `ItemRegistrationRequest` 新增两个可选字段 `IdentifierCreationMode identifierMode`、`IdentifierCreationMode versionMode`；新增便利静态方法 `IdentifierCreationMode resolveMode(String userSupplied)`：blank → AUTO，非空 → MANUAL；保留旧 5 参构造向后兼容（mode 传 null）
- [ ] 3.2 `ScopedIdentifierResponse` 新增 `identifierCreationMode` / `versionCreationMode`（在 `metadata-registry-web/.../dto/scopedidentifier/`）
- [ ] 3.3 `ItemMetaResponse` 新增同名字段；`ItemAdministrationService.buildItemMeta` 在 `preferPrimaryIdentifier(siList)` 取到 SI 后读取这两个字段并写入响应；NULL 时返回 null

## 4. 后端创建与编辑路径分支

- [ ] 4.1 `ItemAdministrationService.ensureAdministeredItem` 创建路径：在新建第一个 SI 后追加 `si.setIdentifierCreationMode(...)` 与 `si.setVersionCreationMode(...)`，请求未携带 mode 时调用 `ItemRegistrationRequest.resolveMode(...)` 反推
- [ ] 4.2 `ItemAdministrationService.ensureAdministeredItem` 编辑路径：读取 `si.getIdentifierCreationMode()` 与 `si.getVersionCreationMode()`，按 design 决策 5 的伪代码分支：
  - AUTO + 请求体携带 identifier → 抛业务异常
  - AUTO + 请求体携带 version → 忽略，沿用自动递增
  - MANUAL / null + 单 SI → 沿用既有 identifier 可改逻辑
  - MANUAL / null + version → 沿用既有接受显式值 / 自动递增逻辑
  - mode 字段本身在编辑过程中 MUST 保持不变
- [ ] 4.3 `IdentifierInputPolicyService` 不必修改语义；保留其命名空间级判定逻辑；本变更的所有 mode 决策都集中在 `ItemAdministrationService` 内

## 5. 前端共享组件改造

- [ ] 5.1 `ItemRegistrationSection.vue` 新增 props `identifierCreationMode?: 'AUTO' | 'MANUAL' | null`、`versionCreationMode?: 'AUTO' | 'MANUAL' | null`，默认值 null
- [ ] 5.2 新增计算属性 `wasIdentifierAuto = isEditing && identifierCreationMode === 'AUTO'` 与 `wasVersionAuto = isEditing && versionCreationMode === 'AUTO'`
- [ ] 5.3 调整 `showIdentifierChoice` / `showVersionChoice`：编辑态 + mode 锁定（AUTO/MANUAL）时不再展示切换控件（沿用"非 AUTO_WITH_OVERRIDE 隐藏"逻辑）；非编辑态行为不变
- [ ] 5.4 调整 `identifierDisabled` / `versionDisabled`：编辑 AUTO 时 MUST 强制禁用并占位符"自动生成，不可编辑"；编辑 MANUAL / null 时启用
- [ ] 5.5 调整 `buildPayload`：编辑 AUTO 时显式把 `identifier` / `version` 设为 null 并填 `identifierMode: 'AUTO'` / `versionMode: 'AUTO'`；其余情形沿用既有逻辑
- [ ] 5.6 `ItemRegistrationSection.spec.ts` 新增场景：编辑 AUTO 标识符禁用 + 切换控件不显示；编辑 MANUAL 标识符启用 + 切换控件可显示；编辑 null 等价 MANUAL；版本字段三档对称

## 6. 宿主对话框透传 mode

- [ ] 6.1 `ItemFormDialog.vue` 在打开编辑模式时（既有加载 `ItemMetaResponse` 的代码路径）读取 `identifierCreationMode` / `versionCreationMode` 传给 `ItemRegistrationSection`
- [ ] 6.2 新建模式不传（沿用 props 默认值 null）
- [ ] 6.3 现有宿主测试 `ItemFormDialog.spec.ts` 回归，确认数据元 / 值域 / 概念域 / 语境四个既有界面无 regression

## 7. 后端测试

- [ ] 7.1 `ItemAdministrationServiceTest` 新增测试：创建时显式携带 `identifierMode='AUTO'` 写入 SI；创建时携带 `identifierMode='MANUAL'` 写入 SI；旧客户端不带 mode 时按 identifier 反推写入 SI
- [ ] 7.2 `ItemAdministrationServiceTest` 新增编辑场景：
  - AUTO + 请求体携带 identifier → 抛业务异常
  - AUTO + 请求体携带 version → 字段被忽略并按策略递增
  - MANUAL（字段显式 MANUAL）+ 单 SI + identifier 修改 → 改写成功，mode 字段保持
  - MANUAL + version 显式值 → 改写成功，mode 字段保持
  - 既存 NULL → 等价 MANUAL（保持原行为）
- [ ] 7.3 `ScopedIdentifierServiceTest` 新增：响应包含两个 mode 字段；NULL 时正确序列化
- [ ] 7.4 `IdentifierInputPolicyServiceTest` 保持不变（本次不修改该 service 的语义）

## 8. 数据库迁移测试

- [ ] 8.1 SQLite 集成测试：`mvn test -pl metadata-registry-web -Dtest=*Migration*` 启动嵌入式 H2/SQLite，验证 `scoped_identifier` 表新增两列存在且可空
- [ ] 8.2 SQL Server 集成测试：Testcontainers mssql 启动后跑 Flyway，确认迁移成功且既有 SI 数据保持原 identifier / version 值不变
- [ ] 8.3 达梦：手动跑 `mvn -Pdm test`（如环境可用），验证 `scoped_identifier` 表新增两列

## 9. 构建与回归

- [ ] 9.1 后端：`mvn -pl metadata-registry-web test` 全部通过（包含 ItemAdministrationServiceTest、ScopedIdentifierServiceTest、IdentifierInputPolicyServiceTest、PermissibleValueMeaningServiceTest、NamespaceConstraintValidatorTest、IdentifierResolutionServiceTest 等）
- [ ] 9.2 前端：`npx vitest run` 全部通过（包含 `ItemRegistrationSection.spec.ts` 与 `ItemFormDialog.spec.ts`）
- [ ] 9.3 前端：`npx vue-tsc --noEmit` 通过（类型校验）
- [ ] 9.4 前端：`npx vite build` 通过（产物可正常打包）
- [ ] 9.5 用 `git diff --stat` 与 `git status` 核对变更范围：仅限本 tasks 列出的文件，无意外扩散

## 10. 文档与归档

- [ ] 10.1 在 commit message 中引用 OpenSpec 变更名 `2026-08-28-persist-item-creation-mode` 与本 tasks 列表
- [ ] 10.2 在合并前以 `openspec validate 2026-08-28-persist-item-creation-mode --strict` 复核产出物一致性
- [ ] 10.3 用户确认实现完成后，按 OpenSpec 流程归档该 change 到 `openspec/changes/archive/2026-08-28-persist-item-creation-mode/`