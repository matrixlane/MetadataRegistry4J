## 1. 后端校验拦截实现

- [x] 1.1 在 `RegistrationAuthorityService` 中注入 `SystemSettingService` 依赖
- [x] 1.2 在 `RegistrationAuthorityService.create` 方法入口处新增单注册表模式下的注册机构数量校验
- [x] 1.3 编写相关集成测试或单元测试验证后端拦截逻辑

## 2. 前端界面控制与按钮禁用实现

- [x] 2.1 修改 `RegistrationAuthorityList.vue` 引入自定义 `fetchFn`，根据加载结果的 `totalElements` 更新 `hasExistingAuthority` 变量
- [x] 2.2 在 `RegistrationAuthorityList.vue` 中在组件挂载时，调用获取初始化向导状态的 API 接口，并保存系统运行模式到 `isSingleMode` 变量
- [x] 2.3 将 `DataTablePage` 的 `can-create` 属性绑定为 `!isSingleMode || !hasExistingAuthority`
