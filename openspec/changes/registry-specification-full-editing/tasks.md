# Tasks: registry-specification-full-editing

## 1. 后端时区校验错误码修正

- [x] 1.1 `RegistrySpecificationService.applyRequest` 捕获 `setDefaultTimeZone` 的 `IllegalArgumentException` 并转为 `BusinessException(ApiCodes.VALIDATION_FAILED, …)`；验证：`RegistrySpecificationServiceTest` 新增"非法时区 → 业务异常、合法 ±HHMM → 保存回读"用例并通过

## 2. 前端设置页属性补全

- [x] 2.1 `RegistrySpecificationSettings.vue` 对话框新增替代语言多选、参考文档标识种类标签输入、表示类方案标识输入框、上下文选择器（`/contexts/options`），并在 `openDialog`/`resetForm`/`submit` 中完成响应映射与 payload 组装；验证：vitest 新增 payload 组装与回显映射用例并通过
- [x] 2.2 指称/定义区替换为 `DesignationDefinitionSection`（`show-pairings=false`，`defaultLanguageId` 取自 `useRegistryDefaultLanguage`），删除中英硬编码输入框，保留行 id/contexts 回传；验证：vitest 用例 + `npm run build` 通过
- [x] 2.3 `defaultTimeZone` 增加 `[-+]HHMM` 前端格式校验（blur 提示、阻止提交）；验证：vitest 校验用例通过

## 3. 回归验证

- [x] 3.1 `npm run build`（vue-tsc + vite）通过
- [x] 3.2 后端 `./mvnw -pl metadata-registry-web test -Dtest='RegistrySpecificationServiceTest'` 通过
- [x] 3.3 既有 `init-wizard` e2e 与相关单测无回归（`npx playwright test e2e/init-wizard.spec.ts`、`npm test`）

## 4. 初始化向导注册规范步骤（人工验收反馈追加）

- [x] 4.1 `RegistrySpecificationService.createDefault` 扩展为接受全量标量属性（字符集/标准/合规三件套/特性/时区/替代语言标识/参考文档标识种类/表示类方案标识，缺省兜底）；验证：`RegistrySpecificationServiceTest` 全属性用例通过
- [x] 4.2 `InitWizardSetupRequest` 新增上述字段并由 `InitWizardService` 透传；验证：`InitWizardServiceIntegrationTest` 新增"向导所填属性落到注册规范"断言并通过
- [x] 4.3 向导恢复注册规范步骤（命名空间之后），完整属性表单 + 预填默认值 + 时区校验；验证：`init-wizard` e2e 两个流程用例更新后通过，`npm run build` 通过
