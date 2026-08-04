## 1. 活动注册员解析服务

- [ ] 1.1 新增 `ActiveRegistrarService`：从 `SecurityContextHolder` 取当前账号，经 `ContactRepository.findByAccount` 过滤 Registrar 绑定，返回活动注册机构（含无绑定/回退语义）
- [ ] 1.2 账号实体新增可空 `active_registration_authority_id`（UUID/VARCHAR(36)，不绑定 schema），确认 Hibernate 自动 DDL 在 SQL Server 与 SQLite 双方言下生效
- [ ] 1.3 新增活动注册机构查询/切换 API（GET/PUT），切换时校验目标机构确为该账号的 Registrar 绑定，失效选择自动回退首个有效绑定

## 2. 创建/更新所有权归属

- [ ] 2.1 `ItemAdministrationRequest` 移除 `registrationAuthorityId` 字段；确认 Jackson 忽略未知字段（或显式配置），避免旧客户端 400
- [ ] 2.2 `ItemAdministrationService` 创建路径：注册机构自有命名空间 → 归属设为命名空间所属机构，且校验与活动注册机构一致；外部命名空间 → 保持无归属、不要求注册员身份
- [ ] 2.3 `ItemAdministrationService` 更新/删除路径：有归属条目要求当前用户持有归属机构的有效 Registrar 绑定（不要求等于活动选择）；无归属条目维持既有校验；超级管理员不豁免

## 3. 联邦合并防护

- [ ] 3.1 核对 `MergeExecutor`/`GenericItemCreator`：并入条目保留来源注册机构表示；对已建 `item_link` 的本地既有条目，禁止用合并负载覆盖 `registrationAuthority`，不满足时补齐防护

## 4. 前端

- [ ] 4.1 条目创建/编辑表单移除注册机构下拉，改为只读展示服务端推导的归属注册机构
- [ ] 4.2 多机构注册员的顶部栏活动注册机构切换入口（单绑定时只读展示）

## 5. 测试与收尾

- [ ] 5.1 后端单测/集成测试：归属自动推导、跨机构写入拒绝、变更权限约束、无绑定拒绝、失效回退、联邦归属保持
- [ ] 5.2 `openspec validate active-registrar-ownership --strict` 通过；`gitnexus_detect_changes()` 核对受影响符号与执行流符合预期
