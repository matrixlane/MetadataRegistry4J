## 1. 数据库模式与核心实体实现

- [x] 1.1 新增 Flyway 数据库迁移脚本 `V20260601002__Create_registry_specification_table.sql`，创建 `registry_specification` 表。
- [x] 1.2 在 `metadata-registry-common-facilities` 模块的 `registration` 包中实现 `RegistrySpecification` 领域模型实体。
- [x] 1.3 在 `metadata-registry-web` 模块的 `repository` 包中实现 `RegistrySpecificationRepository` 持久层接口。

## 2. 后端服务与核心业务逻辑

- [x] 2.1 创建 `RegistrySpecificationService`，实现注册规范的查询、创建、编辑方法。
- [x] 2.2 在 `RegistrySpecificationService` 中实现防篡改验证逻辑：如已存在任何关联元数据（`AdministeredItem`），禁止修改关键配置字段。
- [x] 2.3 修改 `DataGovernanceReadinessService` 就绪检查逻辑，增加 `registrySpecificationExists` 指标校验。
- [x] 2.4 在 `DataInitializer` 中增加默认注册规范的 Seeding 逻辑，使演示数据完整可用。
- [x] 2.5 更新 `InitWizardService#setup` 方法，接收 `InitWizardSetupRequest` 传来的注册规范选项并执行保存。
- [x] 2.6 新增 `RegistrySpecificationInterceptor` 拦截器拦截数据治理写操作，并在 `WebConfig` 中配置生效，以实现治理门禁。

## 3. Web 控制层 API 开发

- [x] 3.1 创建 `RegistrySpecificationController`，对外暴露注册规范的 CRUD 接口以及检查元数据存在状态的接口。
- [x] 3.2 调整 `InitWizardController` 及 `InitWizardSetupRequest` 的 DTO 以支持可选提交注册规范。

## 4. 前端 UI 界面开发与交互更新

- [x] 4.1 在 `InitWizard.vue` 初始化向导中，当选择“超级管理员作为注册管理员”时，新增第四步配置步骤，提供“立即创建注册规范”选项和配置表单。
- [x] 4.2 开发注册规范管理与编辑页面（`RegistrySpecificationEdit.vue`），在编辑时查询后端，对已存在元数据的情况将关键表单字段设为只读。
- [x] 4.3 调整前端项目或数据元创建等功能，在没有注册规范时对用户进行全局警告和引导。

## 5. 测试与验证

- [x] 5.1 编写 `RegistrySpecificationServiceTest` 单元测试，重点覆盖已有元数据时修改关键字段被拒绝的逻辑。
- [x] 5.2 编写 `RegistrySpecificationInterceptorTest` 集成测试，验证治理门禁对未创建规范时的写操作拦截有效性。
- [x] 5.3 运行后端测试与打包，确保编译正常通过。
