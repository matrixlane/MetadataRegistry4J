# 实现计划：personal-factory-reset-confirmation

- 日期：2026-10-08
- 设计：`docs/superpowers/specs/2026-10-08-personal-factory-reset-confirmation-design.md`（D1-D5）
- OpenSpec：`openspec/changes/personal-factory-reset-confirmation`（tasks.md 14 项）
- 工作方式：主树直接实施（影响面 LOW，单一直接调用方）；目标测试类绿 + 全量失败集对比既有基线。

## 任务映射

| tasks.md | 实施内容 |
|---|---|
| 2.1-2.3 | 后端 TDD 核心 |
| 1.1-1.4 | DTO/控制器/openapi |
| 3.1-3.3 | 前端 |
| 4.1-4.4 | 验证收尾 |

## 后端步骤（TDD：先红后绿）

1. **红**：`FactoryResetServiceTest` 新增用例组（mock 风格，参照既有 111-147 行结构）：
   - PERSONAL + 短语正确 + 127.0.0.1 → verify clearTables/reseed，且重建参数为 NO_PASSWORD 哨兵
   - PERSONAL + 短语错误/空白 → 422，不触达 clearTables
   - PERSONAL + `192.168.1.10` → 403，不触达 clearTables
   - PERSONAL + `::1` → 放行（IPv6 回环）
   - TEAM（edition=null 或 TEAM）→ 既有密码用例零改动全绿
   - stub：`systemSettingService.getEdition()` 返回 PERSONAL/TEAM/null
2. **绿**：`FactoryResetService`：
   - 注入 `SystemSettingService`
   - `executeFactoryReset(UUID, FactoryResetRequest, String remoteAddr)`
   - 清表前 `DeploymentEdition edition = systemSettingService.getEdition()` 传入 `reseedAdminUser(edition)`
   - PERSONAL：`isLoopback(remoteAddr)`（try-catch，失败按 false）→ 403；`"恢复出厂设置".equals(confirmationPhrase.trim())` → 否则 422；跳过密码
   - TEAM/null：密码非空 + matches（现状）
   - `reseedAdminUser`：PERSONAL 插入 `NO_PASSWORD` 明文哨兵；否则维持 `Admin@123` 哈希
3. **DTO/控制器**：`FactoryResetRequest(password, confirmationPhrase)` 均无 `@NotBlank`；`SystemController.factoryReset` 注入 `HttpServletRequest` 传 `getRemoteAddr()`。
4. **集成测试**：更新 `FactoryResetLanguagePresetIntegrationTest` 调用点（`new FactoryResetRequest("Admin@123", null)` + `"127.0.0.1"`）；新增 PERSONAL 分支集成用例（SQLite 内存库）：插 `system_setting` 的 `system.edition=PERSONAL` → 短语正确 → 执行成功 + 重建 admin `password_hash='NO_PASSWORD'`。
5. **Controller 测试**：`SystemControllerTest` 更新三处调用构造 + 新增 remoteAddr 透传断言、PERSONAL 下空密码不再 422（mock service 已接管校验）。
6. **openapi**：后端构建后重生成 `docs/technical/api/openapi.json|yaml`。

## 前端步骤（TDD：先红后绿）

1. **红**：新建 `metadata-registry-ui/src/views/system/settings/DangerSettingsPage.spec.ts`（vitest + element-plus，参照 `RegistrationAuthorityList.spec.ts` 挂载方式）：PERSONAL 渲染短语输入框且无密码框、输入完全匹配前"确认重置"禁用、匹配后启用；TEAM 渲染密码框。
2. **绿**：`api/system.ts` 改对象入参；`DangerSettingsPage.vue`：`edition = computed(() => auth.edition ?? fetched)`，挂载时 `auth.edition` 为空则 `GET /init-wizard/status` 补齐；仍 null → 按 TEAM 渲染。PERSONAL 第二步对话框：展示目标短语，`:disabled="phraseInput.trim() !== PHRASE"`。
3. **e2e**：读 `factory-reset.spec.ts` / `factory-reset-init-guard.spec.ts` 确认初始化版本（从用例填充 `Admin@123` 密码看应为团队版路径），团队版保持绿；个人版路径由单元测试+集成测试覆盖，e2e 不扩（记录在任务 3.3）。

## 验证命令

- 后端目标类：`mvn -pl metadata-registry-web -am test -Dtest='FactoryReset*Test,SystemControllerTest' -DfailIfNoTests=false`（多模块须 `-am`，防 ~/.m2 被并行流程改写）
- 后端全量：`mvn -pl metadata-registry-web -am test`（失败集须为既有基线子集）
- 前端：`cd metadata-registry-ui && npx vitest run && npm run build`
- `openspec validate personal-factory-reset-confirmation`
- 提交前：`node .gitnexus/run.cjs detect-changes --scope all --repo .`（无 partial/truncated）

## 回滚

单提交 revert 即可，无数据迁移。
