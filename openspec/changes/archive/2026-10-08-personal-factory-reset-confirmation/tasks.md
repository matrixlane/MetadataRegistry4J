# Tasks: personal-factory-reset-confirmation

参考：`design.md`（D1-D5）、delta spec `specs/factory-reset/spec.md`。

## 1. 后端 DTO 与控制器

- [x] 1.1 修改 `FactoryResetRequest`：`password` 去除 `@NotBlank`，新增可空 `confirmationPhrase` 字段；验证 `SystemControllerTest` 编译通过并按新契约调整（1.1-1.3 完成后跑该类测试全绿）
- [x] 1.2 修改 `SystemController.factoryReset`：注入 `HttpServletRequest`，将 `remoteAddr` 随请求体一并传入 service；验证测试 1.1 与 2.x 全绿
- [x] 1.3 重新生成 `docs/technical/api/openapi.json` 与 `openapi.yaml`，确认 factory-reset 请求体含两个可空字段；验证 openapi 文件中 `confirmationPhrase` 出现且 `password` 的 required 约束消失

## 2. 后端服务（TDD：先写测试）

- [x] 2.1 在 `FactoryResetServiceTest` 新增失败用例：个人版短语正确（127.0.0.1 来源）→ 执行清空与重建、短语错误/为空 → 422、非回环来源 → 403、个人版重建后 admin 为 `NO_PASSWORD` 哨兵；确认新增用例全部编译/运行失败
- [x] 2.2 实现 `FactoryResetService` 版本分支：清表前读取 edition；个人版校验回环地址（`InetAddress.isLoopbackAddress`，解析失败按拒绝处理）+ 确认短语精确匹配（trim），跳过密码；团队版/版本为空维持密码校验；验证 2.1 用例全绿
- [x] 2.3 实现 `reseedAdminUser(edition)` 参数化：个人版插入 `NO_PASSWORD` 哨兵，团队版维持 `Admin@123` 哈希；在 2.1 的重建断言用例上验证全绿，且既有团队版用例（成功/密码错误/清表失败）零回归
- [x] 2.4 更新 `SystemControllerTest`：remoteAddr 透传断言、空请求体在个人版分支不再 422（改由 service 校验）；验证该类全绿

## 3. 前端

- [x] 3.1 修改 `api/system.ts` 的 `factoryReset` 为对象入参 `{ password?, confirmationPhrase? }`；验证 `DangerSettingsPage.vue` 编译引用更新后前端 `vue-tsc`/构建通过
- [x] 3.2 在 `DangerSettingsPage.vue` 实现按 `auth.edition` 分支渲染（个人版：确认短语对话框，输入完全匹配前禁用提交；团队版：密码对话框；版本未知挂载时请求 `/init-wizard/status` 补齐，仍未知按团队版）；验证新建 `DangerSettingsPage.spec.ts` 用例（个人版渲染短语框/按钮禁用启用、团队版渲染密码框）红→绿
- [x] 3.3 检查 e2e `factory-reset.spec.ts` 与 `factory-reset-init-guard.spec.ts` 的 edition 环境：团队版用例保持绿；已核对：两个 e2e 的 /init-wizard/status mock 均无 edition 字段 → 走团队版渲染路径，现有断言保持成立；个人版路径由单元测试（5 用例）与集成测试覆盖，e2e 不扩

## 4. 验证与收尾

- [x] 4.1 后端：运行 web 模块测试（含 `-am` 多模块），确认目标测试类全绿且失败集为既有基线子集；已记录：1489 tests，失败 11（WorkspaceService 10 + SegmentTemplateStrategy 1）为 CURRENT_STATE 记载的并行会话既有失败集合，与本变更零交集；目标类 15/15 绿
- [x] 4.2 前端：`vitest run` 全绿 + `build` 通过；已记录：vitest 35 文件 332/332 全绿（+5 新用例）、build 通过
- [x] 4.3 个人版冒烟：临时实例执行完整链路（短语错误 422 → 非本机 403 → 正确短语执行 → 重建 admin 为 NO_PASSWORD → 重新初始化向导可进入）；已由真实方言（SQLite 内存库）集成测试覆盖：非本机 403 且数据保留 → 本机+正确短语执行成功 → 重建 admin=NO_PASSWORD → initialized=false；短语 422 由单元测试覆盖
- [x] 4.4 `openspec validate personal-factory-reset-confirmation` 通过；提交前 `detect_changes`（MCP 或 CLI）openspec validate 通过；detect_changes 无 partial/truncated（全树 Risk=critical 源自并行会话 WIP，本变更影响面经 impact 分析为 LOW）
