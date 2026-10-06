# Design: ra-creation-wizard

## Context

初始化向导的命名空间创建逻辑现内联于 `InitWizardService.setup`（designation、RA 双挂、标识设置卫星、SEGMENT_TEMPLATE 策略配置）。注册机构创建需复用同一语义。

## Decisions

- **抽取 `NamespaceProvisioningService.provisionOwnNamespace(ra, spec)`**：InitWizardService 改为委托（行为不变，既有向导测试钉住）；注册机构服务同事务复用。单事务、禁 REQUIRES_NEW 约束不变。
- **API 语义**：`selfAsRegistrar`——PERSONAL 忽略请求值（恒建立）；TEAM 缺省 false、显式 true 时为当前超管建立 Registrar（复用 createRegistrarForUser，roleName 缺省"注册管理员"）。`createNamespace=true` 时创建自有命名空间（缺省名"默认命名空间"，输入模式缺省 AUTO_WITH_OVERRIDE、版本策略 SEMVER，MANUAL_ONLY 不建策略配置——与初始化一致）。
- **前端**：新建入口打开两步向导（el-steps）；命名空间步骤顶部开关控制编辑区显隐；个人版注册人员以固定提示呈现（无开关），团队版 el-switch 缺省开。
- **编辑路径不变**：仅创建走向导。

## Risks / Trade-offs

- [InitWizardService 重构引入回归] → 既有向导集成测试（InitWizardServiceIntegrationTest 含命名空间断言）必须在抽取后原样通过。
- [团队版批量自建注册人员] → 已有 SUPER_ADMIN 门槛约束，且创建动作低频，可接受。
