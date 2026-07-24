## 1. 后端模型与接口改造

- [x] 1.1 在 `LanguageIdentification.java` 中增加 `enabled` 字段，默认为 true
- [x] 1.2 在 `LanguageRequest.java` 和 `LanguageResponse.java` 中增加 `enabled` 字段
- [x] 1.3 更新 `LanguageIdentificationService.java` 中 `list`、`applyRequest`、`toResponse` 等方法以支持 `enabled` 字段的接收、映射和查询过滤
- [x] 1.4 更新 `LanguageIdentificationController.java` 以接收 `enabled` 过滤参数

## 2. 数据库内置语言标识初始化

- [x] 2.1 创建 `LanguageInitializer.java` 并实现 G20 国家 22 种常用语言标识在系统启动时的幂等初始化
- [x] 2.2 在 `LanguageInitializer.java` 上配置高优先级（如 `@Order(1)`），确保其执行先于其他种子数据初始化器

## 3. 前端路由、菜单与国际化集成

- [x] 3.1 在 `metadata-registry-ui/src/i18n/zh-CN.json` 的 `nav` 中添加 `"language": "语言标识"` 翻译
- [x] 3.2 在 `metadata-registry-ui/src/router/index.ts` 中添加并注册 `/languages` 路由及 routeTable 配置
- [x] 3.3 在 `metadata-registry-ui/src/layouts/AppLayout.vue` 中将“语言标识”集成至“注册与公共设施”菜单下

## 4. 界面启用/禁用开关及多语言过滤改造

- [x] 4.1 改造前端 `LanguageList.vue`：停用新建/编辑弹窗/删除动作，在表格中添加展示启用状态的 `el-switch` 并实现状态的快速切换与更新保存
- [x] 4.2 改造 `DesignationEditor.vue` 和 `DefinitionEditor.vue`：使下拉列表获取语言时只查已启用（`enabled=true`）的语言标识

## 5. 验证与测试

- [x] 5.1 编译运行项目，验证系统启动时能自动初始化 22 种内置语言数据
- [x] 5.2 登录系统，在左侧菜单点击“语言标识”能够正常访问且仅有状态控制开关（无新增/编辑/删除按钮）
- [x] 5.3 改变某种语言的启用开关，刷新确认其状态能持久化保存；并在指称/定义编辑器的语言下拉框中验证该语言是否已被过滤显示
