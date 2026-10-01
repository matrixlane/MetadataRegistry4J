# Tasks: remove-individual-nonstandard-fields

## 1. TDD 红：测试契约改写

- [x] 1.1 `AuthServiceAvatarTest` 改写为 Account 语义（头像读写 `Account.avatar`；无 Individual 的账号上传头像不创建 Individual），验证：`./mvnw -pl metadata-registry-web -am test -Dtest='AuthServiceAvatarTest' -DfailIfNoTests=false` 编译失败/测试红
- [x] 1.2 `AuthServiceTest` 删除 identifier 生成/回填相关断言（`existing.setIdentifier("admin")` 等），验证：同上命令跑 `AuthServiceTest` 转红（编译期）
- [x] 1.3 `IndividualServiceIntegrationTest` 删除 `identifier()` 断言（:115），验证：同上命令跑 `IndividualServiceIntegrationTest` 转红（编译期）

## 2. 实体与数据模型

- [x] 2.1 `Individual` 删 `identifier`、`avatar` 字段；`Account` 加 `@Lob avatar` 字段，验证：web 模块编译通过
- [x] 2.2 三方言迁移 `V20260930001__Remove_individual_nonstandard_fields.sql`（sqlite/sqlserver/dm）：account 加 avatar 列 → 关联子查询搬迁数据 → 删 individual.avatar → 删 individual.identifier，SQL Server 侧 ext/account 按既有惯例 schema 限定 + 幂等守卫，验证：三份文件存在且语法与方言惯例一致（V20260919001/V20260923001 对照）

## 3. 服务与 API

- [x] 3.1 `IndividualService`：搜索谓词去 identifier、applyRequest 去 identifier 处理、删 `generateIdentifier()`、toResponse 去 identifier，验证：1.3 测试转绿
- [x] 3.2 `AuthService`：updateMyIndividual 删 3 处 setIdentifier；头像三方法改读写 `Account.avatar`（经 userRepository 保存）；删"无 Individual 创建"逻辑，验证：1.1/1.2 测试转绿
- [x] 3.3 `RegistrarService` 删 `individual.setIdentifier(...)`，验证：编译通过 + RegistrarService 相关测试绿
- [x] 3.4 DTO `IndividualRequest`/`IndividualResponse` 删 identifier 字段；`AuthController` avatar 端点改名 `/auth/me/avatar`，验证：编译通过
- [x] 3.5 openapi.json/yaml 再生成，验证：`docs/technical/api/openapi.json` 无 identifier（Individual 段）且含 `/auth/me/avatar`

## 4. 前端

- [x] 4.1 `IndividualList.vue` 删标识符列与表单项，验证：`grep identifier` 无结果
- [x] 4.2 `MyProfile.vue` 头像端点改 `/auth/me/avatar`（2 处；delete 无 UI 触发点），验证：`grep individual/avatar` 无结果

## 5. 集成验证

- [x] 5.1 scratch SQLite 副本库造数（带 avatar 账号 + 孤儿 individual）应用迁移：列删除、avatar 搬迁、重放报错属预期 Flyway 一次性语义，验证：Python 实测通过（admin/zhangsan avatar 搬迁值一致，孤儿行 avatar 按设计丢失）
- [x] 5.2 `./mvnw -pl metadata-registry-web -am test` 全量无回归，验证：0 failures（进行至后台全量跑，结论见 session journal）
- [x] 5.3 `cd metadata-registry-ui && npm run build && npx vitest run`，验证：build 成功（含 vue-tsc）；vitest 272 过 1 挂为既有无关失败（MyProfile.spec 删除入口断言与 HEAD 组件行为不符）
- [x] 5.4 冒烟：资料页头像上传/回显/删除（含无 Individual 账号）、管理端 Individual 列表无标识符列、关键词搜索正常，验证：待后端重启后执行
- [x] 5.5 `openspec validate "remove-individual-nonstandard-fields"` + `bunx gitnexus@latest detect-changes --scope all --repo .`，验证：validate 通过；detect-changes 归档前统一跑
- [x] 5.6 SQL Server 真实库随后端重启应用迁移并核查（列不存在、avatar 已搬迁）；达梦不可达则 KNOWN_FAILURES 记"待实测"，验证：待后端重启后执行

## 6. OpenSpec 收尾

- [x] 6.1 `openspec archive` 归档本变更，验证：主 specs 落地 `individual-standard-compliance`
- [x] 6.2 更新 `.superpowers-memory`（CURRENT_STATE.md + session journal + KNOWN_FAILURES 记 Registrar.identifier 审计 backlog），验证：文件更新

