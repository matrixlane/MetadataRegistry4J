# Proposal: remove-registry-running-mode

## Why

应用实例本身就是一个 Registry/Register（ISO/IEC 11179-3：Registry_Specification 描述注册系统本身的运行环境），不存在"单注册表/多注册表"之分。`authority-contact-governance` 交付后，注册机构创建仍残留 TEAM 版 SINGLE 运行模式的"仅允许一个"限制（前端 toast"单注册表模式下只能创建一个注册机构"），且系统设置中保留了无意义的 SINGLE/MULTI 治理策略概念。该概念应从产品语义、API、界面与文档中彻底移除。

## What Changes

- 后端移除运行模式：删除 `SystemSettingService` 的 `system.running_mode` 读写与 `setRegistryPolicy`、SystemController 的治理策略切换端点、向导 commit 的 `setRunningMode("SINGLE")` 写入、状态/请求 DTO 的 `runningMode` 字段。
- 注册机构创建不再有任何数量限制（两版本一致）：任何版本下超级管理员均可创建任意数量注册机构（个人版仍自动成为注册人员；删除保护仍按版本+Primary 判定，不变）。
- 前端移除门控与字段：auth store 的 `runningMode`、向导 payload 的 `runningMode`、注册机构页"团队版 SINGLE 隐藏新建入口"逻辑（新建入口常显）。
- 修正描述性注释中"单注册表模式"表述（改为按"系统唯一机构"计数推导的客观描述）。
- 注意区分：数据字典导出的 `ExportMode.SINGLE/MULTI`（单文件/多文件）是导出格式概念，与注册表模式无关，保持不变。

## Capabilities

### Modified Capabilities

- `registration-authority-lifecycle`: 注册机构创建规则去除运行模式约束（TEAM 不再限建一个）。
- `deployment-edition`: 个人版/团队版功能约束移除 SINGLE/MULTI 治理策略表述。
- `system-initialization`: 提交语义移除"写入注册表治理策略"；向导请求/响应契约移除 `runningMode`。

## Impact

- 后端：`SystemSettingService`、`SystemController`、`RegistrationAuthorityService.create`、`InitWizardService`（getStatus/commit）、`InitWizardStatusResponse`/`InitWizardSetupRequest`、若干注释。
- 前端：`stores/auth.ts`、`InitWizard.vue`、`RegistrationAuthorityList.vue`（+spec）。
- 测试：注册机构服务测试（TEAM SINGLE 拒绝用例改为放行）、向导测试、列表组件测试。
- 数据：存量库 `system_setting.system.running_mode` 行成为无人读取的孤儿键（无害，不迁移）。
