## 1. 基础结构搭建

- [x] 1.1 在 `metadata-registry-data-specification` 模块中创建关联服务包（如 `...dataspecification.association`）。
- [x] 1.2 定义关联操作异常 `MetadataAssociationValidationException` 与 `UnsupportedMetadataAssociationException`。
- [x] 1.3 定义关联类型枚举或白名单，明确允许的元数据对象对（DataElement-DataElementConcept、DataElement-ValueDomain、DataElementConcept-ObjectClass、DataElementConcept-Property、DataElementConcept-ConceptualDomain、ValueDomain-DataType、ValueDomain-UnitOfMeasure 等）。

## 2. 统一关联服务与校验器实现

- [x] 2.1 实现 `MetadataObjectAssociationService`，提供 `associate`、`dissociate`、`replace`、`validate`、`validateCompleteness` 方法。
- [x] 2.2 实现关联校验器接口 `MetadataAssociationValidator` 与注册机制。
- [x] 2.3 实现 `DataElementAssociationValidator`，覆盖源/目标类型匹配校验；保留完整性校验扩展点（当前按 ISO/IEC 11179-31 下界为 0 保持可选）。
- [x] 2.4 实现 `DataElementConceptAssociationValidator`，覆盖源/目标类型匹配校验、与概念域双向一致性校验。
- [x] 2.5 实现 `ValueDomainAssociationValidator`，覆盖值域与数据类型、计量单位的类型匹配校验。
- [x] 2.6 为 `MetadataObjectAssociationService` 与校验器编写单元测试。

## 3. 数据元关联改造

- [x] 3.1 改造 `DataElementService`，使其通过 `MetadataObjectAssociationService` 处理数据元概念和值域的关联/解除/替换。
- [x] 3.2 `DataElementController` 无需改动：API 契约保持不变。
- [x] 3.3 运行并修复 `DataElementServiceTest` 相关测试。

## 4. 数据元概念关联改造

- [x] 4.1 改造 `DataElementConceptService`，使其通过 `MetadataObjectAssociationService` 处理对象类、属性、概念域的关联/解除/替换。
- [x] 4.2 `DataElementConceptController` 无需改动：API 契约保持不变。
- [x] 4.3 运行并修复 `DataElementConceptServiceTest` 相关测试。

## 5. 值域与概念域关联改造

- [x] 5.1 改造 `ValueDomainService`，使其通过 `MetadataObjectAssociationService` 处理数据类型、计量单位的关联/解除/替换（值域与数据元的关联由 DataElement 端维护，本次不改动）。
- [x] 5.2 `ValueDomainController` 无需改动：API 契约保持不变。
- [x] 5.3 `ConceptualDomainService` 无需主动改造：当前代码未直接维护 DataElementConcept 关联，该多对多关系由 `DataElementConceptService` 统一维护。
- [x] 5.4 `ConceptualDomainController` 无需改动：API 契约保持不变。
- [x] 5.5 运行并修复 `ValueDomainServiceTest`、`ConceptualDomainServiceTest` 相关测试。

## 6. 集成测试与文档更新

- [x] 6.1 编写跨对象的关联集成测试，覆盖建立、解除、替换、非法关联等场景（见 `MetadataObjectAssociationServiceTest`）。
- [x] 6.2 运行 `metadata-registry-data-specification`（28 个测试通过）与 `metadata-registry-web`（1175 个测试通过）模块的完整测试套件。
- [ ] 6.3 如必要，更新 `ARCHITECTURE.md` 或模块 README 中关于元数据对象关联的说明。
- [x] 6.4 运行 `gitnexus detect-changes` 验证影响范围（4 个文件、14 个符号、15 个执行流程受影响，风险 HIGH）。
