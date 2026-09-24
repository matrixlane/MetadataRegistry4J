# 实现计划：Registry Context 初始化引导（add-registry-context-bootstrap）

日期：2026-09-23
依据：`docs/superpowers/specs/2026-09-23-registry-context-bootstrap-design.md`（v4 已确认）+ `openspec/changes/add-registry-context-bootstrap/`（tasks.md 已确认）
影响分析（任务 1.1 已完成）：`InitWizardService.setup` LOW（唯一调用方 `InitWizardController`）；`ContextTestDataInitializer.run` UNKNOWN 经文本搜索确认为 Spring Boot Runner 框架调度，无代码调用方——均安全。

## 实现顺序（TDD）

1. **`RegistryContextBootstrapService`**（新建，`web/service`）
   - 常量：`REGISTRY_CONTEXT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002")`；指称/定义文本（见设计 v4 §4.3）。
   - `ensureRegistryContext(RegistrationAuthority ra)`：
     - `Namespace ns = ra.getNamespace()`，null → `IllegalStateException("registration authority has no namespace")`；
     - `contextRepository.existsById(REGISTRY_CONTEXT_ID)` → 返回既有；
     - `new Context()` + `setId(REGISTRY_CONTEXT_ID)`；
     - 4 条 `TextLine`（2 指称 + 2 定义，`contexts = List.of(new TextLineContextEntry(SELF_CONTEXT_REFERENCE, Acceptability.PREFERRED))`，acceptability=PREFERRED）；
     - `applyDesignations(entity, lines, ns.getId())` / `applyDefinitions(entity, lines)`；
     - `ItemRegistrationRequest req`：identifier/version 按 `ns` 的 identifierInputMode（AUTO 类 → null；MANUAL_ONLY → `registry-context`/`1.0`）；
     - `ensureAdministeredItem(entity, req, ra)` → `contextRepository.save(entity)` → `recordSubmission(entity, req)`；
     - spec 回填：经 `RegistrySpecificationService` 加载唯一 spec，`getContext()==null` 时设置并保存。
   - `backfillIfInitialized()`：`systemSettingService.isInitialized()` 假/已存在 → 返回；取唯一 RA → `ensureRegistryContext`；try/catch + log warn。
   - 注入：`ContextRepository`、`ItemAdministrationService`、`RegistrySpecificationService`、`SystemSettingService`、`RegistrationAuthorityRepository`。
2. **`RegistryContextBackfillRunner`**（新建，`web/seed`）：`ApplicationRunner`，`@Order(101)`，委托 backfill；不挂 `app.seed.enabled`。
3. **`ContextTestDataInitializer`**：类上新增 `@Order(100)`（仅此一行）。
4. **`InitWizardService.setup()`**：`createDefault(...)` 之后、`systemDefaultNamespaceInitializer` 重调用附近，加 `try { bootstrapService.ensureRegistryContext(ra); } catch (Exception e) { log.warn(...) }`；注入新服务。

## 测试

- 新建 `RegistryContextBootstrapServiceTest`（参照 `ItemAdministrationServiceTest` 的测试装配）：覆盖任务清单 2.1–2.5、3.1 全部断言（固定 UUID、4 条自指 relation、`CTX.00001`/`1.0.0`（真实段模板，测试库 mock/配置策略）、MANUAL 回退 `registry-context`/`1.0`、幂等、spec 回填不覆盖、backfill 三分支）。
- `InitWizardServiceTest` 增补：setup 后 Registry Context 存在、scoped identifier 命名空间 = RA 命名空间、spec.context 非空；abort 再 setup 幂等重建。

## 验证命令

- `mvn -pl metadata-registry-web -am test`（全量）
- `scratch/registry-context-smoke/smoke.sh`（新建，参照 `scratch/submitter-smoke/smoke.sh` 模式）
- `bunx gitnexus@latest detect-changes --scope all --repo .`（或 MCP）无 partial/truncated
