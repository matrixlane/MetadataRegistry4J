## 1. 活动 Registrar 身份解析服务

- [ ] 1.1 新增 `ActiveRegistrarService`：从 `SecurityContextHolder` 取当前账号，经 `ContactRepository.findByAccount` 过滤 Registrar 实例得到身份列表，返回活动 Registrar 身份（唯一自动激活/无身份/失效回退语义），活动注册机构由身份派生
- [ ] 1.2 账号实体新增可空 `active_registrar_id`（UUID/VARCHAR(36)，不绑定 schema），确认 Hibernate 自动 DDL 在 SQL Server 与 SQLite 双方言下生效
- [ ] 1.3 新增活动 Registrar 身份查询/切换 API（GET/PUT），切换时校验目标身份确为该账号的 Registrar 绑定，失效选择自动回退首个有效身份

## 2. 注册权限与所有权归属

- [ ] 2.1 `ItemAdministrationRequest` 移除 `registrationAuthorityId` 字段；确认 Jackson 忽略未知字段（或显式配置），避免旧客户端 400
- [ ] 2.2 `ItemAdministrationService` 创建路径：非 Registrar 一律拒绝（含团队版未自指派 Registrar 的 Admin，提示先取得身份）；注册机构自有命名空间 → 归属设为活动身份所在机构，命名空间所属机构与活动身份机构不一致时拒绝；外部命名空间（含导入与手工编目）→ 同样要求活动 Registrar 身份，条目归属活动身份所在机构（Namespace 不关联注册机构，`namingAuthority` 记录外部来源机构，实体字段已具备，仅核对赋值链路）
- [ ] 2.3 `ItemAdministrationService` 更新/删除路径：有归属条目要求当前用户以归属机构的 Registrar 身份为活动身份（未激活须先切换）；无归属条目（仅历史遗留）维持既有校验；超级管理员不豁免

## 3. 联邦合并防护

- [ ] 3.1 核对 `MergeExecutor`/`GenericItemCreator`：并入条目保留来源注册机构表示；对已建 `item_link` 的本地既有条目，禁止用合并负载覆盖 `registrationAuthority`，不满足时补齐防护

## 4. 前端

- [ ] 4.1 条目创建/编辑表单移除注册机构下拉，改为只读展示服务端推导的归属注册机构
- [ ] 4.2 多 Registrar 身份用户登录后的活动身份选择，以及全局活动身份切换入口（单身份用户只读展示当前身份）

## 5. 测试与收尾

- [ ] 5.1 后端单测/集成测试：非 Registrar 拒绝（自有与外部命名空间）、归属自动推导、外部命名空间条目归属本机构且 namingAuthority 保持外部来源、跨机构写入拒绝、未激活身份修改拒绝、失效回退、联邦归属保持
- [ ] 5.2 `openspec validate active-registrar-ownership --strict` 通过；`gitnexus_detect_changes()` 核对受影响符号与执行流符合预期
