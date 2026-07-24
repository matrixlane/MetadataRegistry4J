## Why

在系统运行模式为单注册表模式（SINGLE）时，系统应该只允许存在一个注册机构。目前，系统初始化时会创建一个默认的注册机构，但此后在“注册机构”管理页面上，管理员仍然可以无限制地继续新增注册机构。这与“单注册表”的业务定义相违背，可能导致数据管理混乱和系统概念模型冲突。因此，需要在单注册表模式且已存在一个注册机构时，在前端和后端均禁止继续新增注册机构。

## What Changes

1. **前端控制**：在“注册机构”列表页面中，当检测到系统处于单注册表运行模式且注册机构数量已达 1 个或以上时，隐藏/禁用“新建”注册机构按钮，不允许继续执行创建操作。
2. **后端校验**：在创建注册机构的 API 服务层添加校验逻辑。若系统运行模式为 `SINGLE` 且数据库中已存在注册机构（数量 >= 1），则抛出业务校验异常（`VALIDATION_FAILED`, 422），防止直接调用接口新增。

## Capabilities

### New Capabilities

无

### Modified Capabilities

- `registry-specification`: 增加在单注册表模式下只能创建一个注册机构的约束规则。

## Impact

1. **后端服务层 (`metadata-registry-web`)**：
   - 依赖注入 `SystemSettingService` 到 `RegistrationAuthorityService`。
   - 修改 `RegistrationAuthorityService.create` 方法，引入运行模式与注册机构数量校验逻辑。
2. **前端页面 (`metadata-registry-ui`)**：
   - 修改 `RegistrationAuthorityList.vue`，在挂载时获取系统运行模式，并根据当前列表的条目总数判断，动态禁用或隐藏“新建”按钮。
