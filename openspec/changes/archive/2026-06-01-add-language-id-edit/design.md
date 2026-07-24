## Context

在元数据注册中心（MDR）中，语言标识（`LanguageIdentification`）是多语言名称和定义的重要引用词典。我们决定：
1. 废弃原先的语言标识动态 CRUD（增删改）设计。
2. 在数据库中内置预设 G20 国家所使用的语言标识种子数据。
3. 改造前端“语言标识”管理界面，使其仅用于展示并控制各个语言标识的“启用”和“禁用”状态。
4. 改造多语言指称和定义输入组件，使其只能选用当前已启用的语言标识。

## Goals / Non-Goals

**Goals:**
- 在 `LanguageIdentification` 数据模型中增加 `enabled` 状态字段（默认为 `true`）。
- 实现一个数据库启动初始化器 `LanguageInitializer`，在系统启动时预置 G20 常用语言列表。
- 后端接口支持传入 `enabled` 过滤，并在服务层和控制器层处理状态的接收与更新。
- 前端管理界面仅提供基于 `el-switch` 的状态切换操作，不支持新建和删除。
- 保证指称和定义组件只加载已启用的语言。

**Non-Goals:**
- 不删除现存其他实体中对 `LanguageIdentification` 的关系映射（这属于破坏性变更，无必要）。

## Decisions

### 1. 实体与传输对象改造 (Backend Model)
- **LanguageIdentification.java**:
  增加字段 `private Boolean enabled = true;`，并在数据库对应列上设置 `nullable = false`。
- **LanguageRequest.java**:
  增加 `Boolean enabled` 字段，作为更新状态的数据载体。
- **LanguageResponse.java**:
  增加 `Boolean enabled` 字段传回前端。

### 2. G20 语言标识系统初始化 (Backend Seed)
新增 `LanguageInitializer.java` 作为 `ApplicationRunner`。在该初始化器中：
- 声明 G20 国家使用的 22 种常见语言标识（zh-CN, en-US, ja-JP 等）。
- 启动时，如数据库中未存在该标识，则将其插入到数据库中，`enabled` 默认设为 `true`。
- 由于其他初始化器依赖语言数据，`LanguageInitializer` 应标记较高的优先级（如使用 `@Order(1)`），确保在 `SystemDefaultNamespaceInitializer` 等组件运行之前执行完成。

### 3. 控制器及服务逻辑过滤 (Backend API)
- **LanguageIdentificationService.java**:
  - `list` 方法重构，支持在 `Specification` 中过滤 `enabled` 参数。
  - `applyRequest` 复制 request 中的 `enabled` 属性到 entity。
  - `toResponse` 在构造 `LanguageResponse` 时填充 `enabled`。
- **LanguageIdentificationController.java**:
  - `list` 接口增加可选参数 `@RequestParam(required = false) Boolean enabled`。

### 4. 前端状态管理界面与组件适配 (Frontend UI)
- **i18n/zh-CN.json & router/index.ts & AppLayout.vue**:
  - 保留并更新原本的路由配置和菜单，指向 `/languages`。
- **LanguageList.vue**:
  - 移去新建、删除和原有的编辑弹窗，`:can-create="false"`，`:can-edit="false"`，`:can-delete="false"`，`:show-action="false"`。
  - 在表格中增加“启用状态”列，放一个 `el-switch` 组件，并绑定 `row.enabled`。
  - 当开关发生改变时，触发 `change` 事件直接向后台发起 `languageApi.update(row.id, { ...row, enabled: val })` 请求并显示操作结果。
- **DesignationEditor.vue & DefinitionEditor.vue**:
  - 在 `onMounted` 钩子中，请求参数改为 `languageApi.list({ size: 100, enabled: true })`，确保下拉框仅呈现已启用的语言。

## Risks / Trade-offs

### 预置语言标识覆盖
- **风险**: 初始化数据中部分语言（如 zh-CN, en-US, ja-JP）在现有种子数据 `DataInitializer` 中也有插入，可能会发生冲突。
- **缓解**: 使用 `findByLanguageIdentifier` 进行幂等防重判断，即“若已存在则直接返回/不重新插入”。所有初始化器统一通过 `languageRepository.findByLanguageIdentifier` 获取语言实体。
