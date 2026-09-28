# 2026-09-28 默认上下文预填（RegistrySpecification.context 定案）

## 最终结论（用户定案）

**默认上下文的唯一语义与唯一入口就是 `RegistrySpecification.context`（注册表自身的上下文）。**
上下文本身不设"默认标志"功能；ext.context 卫星表方案探索后被用户明确作废，不实施、不留档（设计稿已删除）。

## 已落地的实现（本会话前三轮交付）

- 后端 `GET /api/v1/contexts/default`（`ContextController` → `ContextService.getDefault`）：读 `RegistrySpecification.context`，缺失/未关联 → 404。不依赖固定主键 `…0002`（该 UUID 仅保留为 bootstrap 创建幂等键）。
- 前端 `useDefaultContext` composable（模块级缓存，404 静默降级为不预填）；预填接入点：`MultilingualTextEditor`（新建行 + 添加上下文）、`DesignationEditor`、`DefinitionEditor`、`DesignationDefinitionPairingEditor`（新建配对行）、`RegistrySpecificationSettings`（新建注册规范预填 contextId 字段）。
- 种子文案更名：`注册系统上下文` → `注册表上下文`（英文 `Registry Context` 不变）；存量库由 `normalizeSystemPresetStatus` 启动归一自动更名；该方法同时兜底回填 `spec.context`（经自注入代理调用获得事务，访问 LAZY 集合）。
- 文档同步：README.md、openspec/specs/{system-initialization, registry-context-bootstrap}/spec.md。

## 验证

- `RegistryContextBootstrapServiceTest`（含新增 legacyZhDesignation 更名、specContextNull 回填两例）+ `ContextControllerTest` 全绿。
- 前端 vue-tsc + vitest 262/262 全绿（修复了 vi.mock 桩缺 `default` 方法的 137 个未处理拒绝——composable 内 `Promise.resolve().then(() => contextApi.default?.())` 防御）。

## 遗留与注意

- 工作区存在**并行未提交的初始化向导改动**（InitWizardService 新增 TEAM 版必填校验、InitWizardSetupRequest 组件压缩 26→24 等）：`InitWizardServiceIntegrationTest` 2 失败 + 3 错误属该路改动配套测试未更新，**与本功能无关**。已将 RegistryContextBootstrapServiceTest 向导 helper 适配新 DTO（改用 PERSONAL 版）。
- 签入前需按 OpenSpec 变更分组 + `detect_changes --scope all`（AGENTS.md 强制）。
