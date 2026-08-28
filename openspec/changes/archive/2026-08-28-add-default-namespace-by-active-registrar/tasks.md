# Tasks

## 1. 后端默认推导

- [ ] 1.1 在 `NamespaceRepository` 新增 `Optional<Namespace> findFirstByRegistrationAuthority_IdAndNamespaceKindNotAndSchemeReferenceNot(UUID, NamespaceKind, String)` 派生查询（方言中立）
- [ ] 1.2 在 `ItemAdministrationService` 中新增私有方法 `resolveDefaultNamespace()`：读取 `ActiveRegistrarContext`，按 D2 推导；显式 `explicitAuthority` 路径或无活动 Registrar/无自有命名空间时返回 null
- [ ] 1.3 在 `ensureAdministeredItem(Item, ItemRegistrationRequest, RegistrationAuthority)` 中，`req.namespaceId() == null` 时调用 `resolveDefaultNamespace()` 推断默认命名空间，再走既有 `enforceRaNamespacePrecondition`
- [ ] 1.4 对 D4 边界（`namespaceKind != EXTERNAL` 且 `schemeReference != 'urn:metadata-registry:system-default'`）写为派生前置断言，并配单测覆盖

## 2. 前端预选与切换响应

- [ ] 2.1 在 `ItemRegistrationSection.vue#loadOptions()` 内两路 resolved 之后新增 `applyDefaultNamespace()`：仅在 `!props.isEditing` 且 `model.value.namespaceId == null` 时执行；命中条件与 D1 公式一致
- [ ] 2.2 在 `applyDefaultNamespace()` 中显式排除 `namespaceKind === 'EXTERNAL'` 与 `schemeReference === SYSTEM_DEFAULT_SCHEME` 的命名空间
- [ ] 2.3 监听活动 Registrar 切换（顶栏身份切换器复用现有事件/接口路径），切换后清空 `model.value.namespaceId` 并再次执行 `applyDefaultNamespace()`
- [ ] 2.4 保证默认预选不修改 `identifierChoice` / `versionChoice` 默认值，亦不写入任何"上次选择"持久化

## 3. 与既有闸门的一致性

- [ ] 3.1 与 `enforceRaNamespacePrecondition` 行为保持一致：默认推导返回 null 时由该校验接管拒绝
- [ ] 3.2 与 `namespace-identifier-strategy` 中 `AUTO_ONLY` / `MANUAL_ONLY` / `AUTO_WITH_OVERRIDE` 行为保持一致：默认命名空间切换后，输入框禁用/启用状态由既有 `identifierDisabled` / `versionDisabled` computed 派生
- [ ] 3.3 与 `identification` 中"无 RA 关联命名空间时创建自有数据标准被阻止"场景保持一致：前端沿用既有 alert 引导

## 4. 测试

- [ ] 4.1 单测 `ItemAdministrationService` 三个默认推导分支：命中自有命名空间/无活动 Registrar/无自有命名空间（前置门槛拒绝）
- [ ] 4.2 单测调用方显式传入 `namespaceId` 时不被默认推导覆写（含 `explicitAuthority` 路径）
- [ ] 4.3 单测外部/system-default 命名空间不会被选中；多 RA 时仅当前活动 RA 的自有命名空间被命中
- [ ] 4.4 单测一致性错误：当同一 RA 关联多条自有命名空间时拒绝默认推导并提示修复
- [ ] 4.5 前端 `ItemRegistrationSection.vue` 组件测试：创建态预选/编辑态无变化/身份切换刷新预选
- [ ] 4.6 Playwright 回归脚本：数据元新建对话框打开后默认命名空间即选中；切换活动 Registrar 后新打开的对话框默认指向新 RA 的命名空间

## 5. 文档与同步

- [ ] 5.1 在 `ARCHITECTURE.md`（如最终决定落地于该文件）补充"按活动 Registrar 默认命名空间"一行说明；若决定仅在本 change 提案/任务中说明则跳过该文件
- [ ] 5.2 在前端 `ItemRegistrationSection.vue` 内 `applyDefaultNamespace()` 上方加一行注释，链接到本 change 的 proposal.md，便于后人检索
- [ ] 5.3 归档本 change 前确认 openspec validate 通过、CI/单测绿灯

- [ ] 5.1 在 `ARCHITECTURE.md`（如最终决定落地于该文件）补充"按活动 Registrar 默认命名空间"一行说明；若决定仅在本 change 提案/任务中说明则跳过该文件
- [ ] 5.2 在前端 `ItemRegistrationSection.vue` 内 `applyDefaultNamespace()` 上方加一行注释，链接到本 change 的 proposal.md，便于后人检索
- [ ] 5.3 归档本 change 前确认 openspec validate 通过、CI/单测绿灯

## 6. 已确认的设计决议（来自用户反馈）

- 活动 Registrar 切换触发完整上下文重解析：UI 中所有依赖活动 Registrar 的派生状态（命名空间预选、模板变量加载、`raNamespaceExists` 提示等）都必须同步刷新（任务 2.3 覆盖）。
- 不增加"创建命名空间"快捷按钮：保留既有 alert 引导即可。
- 不引入运行时配置开关：`resolveDefaultNamespace` 以独立方法实现，便于未来按需包装为可配置策略。
