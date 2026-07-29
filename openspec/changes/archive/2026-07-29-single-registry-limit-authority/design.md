## Context

在系统初始化时，超级管理员可以选择单注册表（SINGLE）或多注册表（MULTIPLE）运行模式。在单注册表模式下，系统仅允许存在唯一一个注册机构（Registration Authority）。但目前在“注册机构”界面中，即使已存在注册机构，系统依然展示“新建”按钮，并允许管理员直接调用后端 POST `/api/v1/registration-authorities` API 新增注册机构。这违背了单注册表模式的设计初衷，会导致系统模型混乱。

## Goals / Non-Goals

**Goals:**
- **后端拦截**：当系统处于单注册表模式（SINGLE）且已有注册机构（count >= 1）时，后端接口禁止新增注册机构，并抛出 422 异常。
- **前端禁用**：在“注册机构”列表页面中，当检测到系统为单注册表模式且注册机构数量已达 1 个或以上时，隐藏/置灰“新建”按钮，使用户无法触发新建流程。

**Non-Goals:**
- 不限制多注册表模式下的注册机构新增。
- 不影响已有注册机构的更新（update）、删除（delete）和详情查询（get）操作。

## Decisions

### 1. 后端依赖注入与校验拦截
- **方案**：在 `RegistrationAuthorityService` 中注入 `SystemSettingService`，并在 `create` 方法入口处添加校验。
- **核心逻辑**：
  ```java
  if ("SINGLE".equalsIgnoreCase(systemSettingService.getRunningMode()) && repository.count() >= 1) {
      throw new BusinessException(ApiCodes.VALIDATION_FAILED, "单注册表模式下只能创建一个注册机构");
  }
  ```
- ** rationale**：直接在 Service 层作统一拦截是最安全的防御手段，能同时作用于前端操作和 API 接口调用。

### 2. 前端动态判断显示“新建”按钮
- **方案**：在 `RegistrationAuthorityList.vue` 中：
  - 定义 `isSingleMode` 响应式变量，在挂载时异步调用 `/api/v1/init-wizard/status` 接口获取当前系统的 `runningMode`；
  - 定义 `hasExistingAuthority` 响应式变量，自定义 `DataTablePage` 的 `fetchFn` 代理，根据分页查询结果的 `totalElements > 0` 实时更新该变量值；
  - 将 `DataTablePage` 的 `can-create` 属性动态绑定为 `!isSingleMode || !hasExistingAuthority`。
- **rationale**：`DataTablePage` 组件已经支持通过 `canCreate` 隐藏新建按钮。直接利用此 Prop 可以保证 UI 的一致性与现代美观性。

## Risks / Trade-offs

- **[Risk]** UI 状态延迟：若在系统挂载时，获取初始化状态的 API `/init-wizard/status` 因网络原因较慢，可能会有一瞬间显示“新建”按钮。
  - **Mitigation**：将按钮的初始渲染状态设为隐藏，直到系统初始化状态接口返回后再决定是否渲染；或在接口返回前默认 `canCreate` 为 false。
