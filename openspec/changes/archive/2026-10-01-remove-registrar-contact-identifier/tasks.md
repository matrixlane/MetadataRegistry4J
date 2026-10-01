# Tasks: remove-registrar-contact-identifier

## 1. TDD 红：测试断言清理

- [x] 1.1 `RegistrarServiceTest` 删除 registrar identifier 相关断言（先 grep 全文定位），验证：`./mvnw -pl metadata-registry-web -am test -Dtest='RegistrarServiceTest' -DfailIfNoTests=false` 编译红
- [x] 1.2 全局排查其余测试（SubmitterWriteAccessSecurityTest/MergeExecutorStrategyTest/InitWizardServiceIntegrationTest 等 grep 命中文件）中真正的 Registrar identifier 断言并清理，验证：编译红清单收敛到 RegistrarService/DTO

## 2. 实体与迁移

- [x] 2.1 `Registrar` 删 identifier 字段，验证：web 模块编译通过
- [x] 2.2 三方言迁移 `V20261001001__Remove_registrar_identifier.sql`（sqlite 裸 drop column / sqlserver COL_LENGTH('mdr.registrar','identifier') 守卫 / dm ALL_TAB_COLUMNS 守卫 + EXECUTE IMMEDIATE，模式对照 V20260930001 三份），验证：三份存在且风格一致

## 3. 服务与 API 转绿

- [x] 3.1 `RegistrarService`：list 搜索谓词去 identifier（保留其余搜索条件）、applyRequest 去 setIdentifier、toResponse 去 identifier、createRegistrarForUser 去 `{username}-registrar` 生成，验证：1.1/1.2 红转绿
- [x] 3.2 `RegistrarRequest`（含 @NotBlank）/`RegistrarResponse` 删 identifier 字段，同步全部构造/引用点，验证：编译通过
- [x] 3.3 openapi.json/yaml 再生成（OpenApiExportTest，`-Dopenapi.export=true`），验证：Registrar 段无 identifier

## 4. 前端

- [x] 4.1 `RegistrarList.vue` 删标识符列（:13）、必填表单项（:27-28）、表单模型 identifier 字段（:98 附近），验证：`grep identifier` 无结果
- [x] 4.2 `ContactList.vue` 删幽灵标识符列与表单项，验证：`grep identifier` 无结果
- [x] 4.3 e2e/组件测试中 Registrar/Contact 标识符断言排查清理（init-wizard.spec/test_save_designation.spec 大概率是 Item/Namespace 标识符，确认无关则不动），验证：`cd metadata-registry-ui && npx vitest run` 全绿、`npm run build` 通过

## 5. 集成验证

- [x] 5.1 Python scratch 副本库实测迁移：registrar 表 identifier 列删除、重复执行行为符合预期，验证：脚本输出 PASS
- [x] 5.2 `./mvnw -pl metadata-registry-web -am test` 全量，验证：无新增失败（既有 11 个并发/WIP 失败除外）
- [x] 5.3 后端重启 → SQL Server 真实库应用 V20261001001 → 核查列删除，验证：sqlcmd 查询无 identifier 列
- [x] 5.4 冒烟（临时管理员账号，用后清理）：GET/POST/PUT `/api/v1/registrars` 契约无 identifier、关键词搜索 200、Contact 接口无 identifier，验证：响应字段核查通过
- [x] 5.5 `openspec validate` + `node .gitnexus/run.cjs detect-changes --scope all --repo .`，验证：无 error / 无 partial/truncated

## 6. OpenSpec 收尾

- [ ] 6.1 `openspec archive` 归档，验证：主 specs 落地 `registrar-standard-compliance`
- [ ] 6.2 记忆更新：CURRENT_STATE.md + session journal（backlog 勾掉 Registrar.identifier 项），验证：文件更新
