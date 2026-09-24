# Proposal: initial-status-incomplete

## Why

ISO/IEC 11179-6:2023 规定注册生命周期入口态为 Incomplete（4.3.2 Table 1；4.3.3.1.2：最低元数据仅需标识符与提交者信息、不纳入版本控制、约束不强制），Recorded 要求全部强制元数据属性填写完成并经注册机构确认（4.3.3.1.4）。当前系统新建管理项（数据元、值域、语境等全部创建路径）默认落 `RECORDED`（`ItemAdministrationService.java:419`），且创建时可经表单显式直建 `QUALIFIED` 等高级状态，绕过生命周期入口；而推进流程把 `null` 状态视为 `INCOMPLETE`（`RegistrationProcessService.java:482`、`RegistrationLifecycleService.java:266`），系统口径自相矛盾。用户已核实标准原文并裁定：新建条目统一从 Incomplete 开始（方案 A）。

## What Changes

- **BREAKING（创建语义）**：新建 Administered Item 的默认初始注册状态由 `RECORDED` 改为 `INCOMPLETE`。全部创建路径经 `ItemAdministrationService.ensureAdministeredItem` 汇聚（数据元、值域、语境、注册规范、约束集、数据类型、数据类型体系、许可值含义、条目映射、概念内容应用等），联邦并入（`GenericItemCreator`）同样适用，不特判。
- **显式初始状态标定收窄**：创建请求（`existing == null`）显式携带 `registrationStatus` 时，仅允许 `INCOMPLETE`（幂等默认）/`APPLICATION`/`HISTORICAL`（ISO/IEC 11179-6 文档态入口标定，原设计意图）；`CANDIDATE`/`RECORDED`/`QUALIFIED`/`STANDARD`/`PREFERRED_STANDARD`/`SUPERSEDED`/`RETIRED` 一律拒绝，返回新增错误码 `INVALID_INITIAL_STATUS = 1125`——生命周期态只能经推进流程（INCOMPLETE→CANDIDATE→RECORDED）获得。
- **既有条目路径不变**：`existing != null` 时表单携带不同状态仍拒绝（1124）、与当前值相同放行（回显兼容）；M2 遗留无状态条目落默认随之变为 `INCOMPLETE`，携带 `RECORDED` 将与默认不同而按 1124 拒绝（语义合理：遗留条目不能经编辑表单直接变 Recorded）。
- `ApiCodes` 新增 `INVALID_INITIAL_STATUS = 1125`（1100–1199 注册生命周期段内空闲）；`ItemRegistrationRequest.registrationStatus` 注释同步。
- `GlobalNamespaceService.wrapperOf`（命名空间系统内部簿记包装物）保持 `RECORDED` 不动；存量数据零迁移；无 schema 变更。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `registration-status`：ADDED 两条需求——"新建管理项初始注册状态为 Incomplete"与"创建时显式初始状态标定白名单（APPLICATION/HISTORICAL）"。

## Impact

- **代码**：`ItemAdministrationService.ensureAdministeredItem`（默认值 + 白名单校验）、`ApiCodes`、`ItemRegistrationRequest` 注释。
- **API 行为**：全部创建类端点新建条目初始状态变为 `INCOMPLETE`；创建请求显式携带非法初始状态返回业务错误 1125。联邦并入条目初始状态随之变为 `INCOMPLETE`（语义变更：目的注册机构视角下即新提交项）。
- **前端**：列表/详情状态列显示"不完整"（i18n 键 `registrationStatus.INCOMPLETE` 已存在）；创建表单不携带 `registrationStatus`（维持现状）；推进 UI 已存在，无需改动。
- **测试**：`ItemAdministrationServiceTest`（默认断言、M2 遗留路径、显式标定用例）及相关注册流程/数据字典/条目映射测试逐一核对。
- **数据**：无迁移脚本；既有条目状态保持原样。
