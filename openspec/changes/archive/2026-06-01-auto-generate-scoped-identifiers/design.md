## Context

系统目前已在 `common-facilities` 模块中支持复杂的标识符生成机制（如基于自增序列 `SEQUENCE` 或模板 `TEMPLATE` 的生成策略 `IdentifierStrategyConfigService`），但在业务元数据项（如 DataElement）创建和注册逻辑中，均是硬编码式地要求手动输入，或仅使用了较简单的全局 `IdentifierGenerationService` 默认逻辑。
此外，由于高并发场景下的元数据导入或并发写入，在分配自增序列号时需要行级悲观锁保护（如 `SELECT FOR UPDATE`），因此必须打通 `common-facilities` 与 `web` 数据访问层的逻辑。

## Goals / Non-Goals

**Goals:**
- 打通后端元数据注册机制与 `IdentifierStrategyConfigService` 体系，支持基于 (Namespace, IdentifierType) 进行策略路由和标识符生成。
- 为 `SEQUENCE` 自增序列策略提供高并发排他锁保护，保证多节点部署时的自增唯一性。
- 在元数据项创建的表单组件中，增加生成模式单选按钮（自动生成 vs 手动录入），优化数据验证流程。

**Non-Goals:**
- 提供标识符生成策略管理的前端编辑界面（本次开发只负责预载与应用，策略管理功能移至独立变动中）。
- 修改已经持久化在数据库中的元数据标识符。

## Decisions

### 决策 1：解决多模块循环依赖与解耦 (Module Decoupling)
`common-facilities` 无法直接调用 `web` 层的 Repository 访问数据库的自增序列记录。
- **方案**：在 `common-facilities` 模块中引入 `SequenceProvider` 接口，`IdentifierStrategyConfigService` 关联该 Provider 进行序列分配；在 `web` 模块中，`SequenceRegistryService` 实现该接口，并在系统启动时将其绑定到服务中。这既避免了循环依赖，又保留了模块边界。

### 决策 2：高并发自增并发控制 (Concurrency Locking)
对于 SEQUENCE 自增逻辑，如何避免并发注册下的序列号冲突？
- **方案**：使用 Spring Data JPA 的悲观写锁 (`@Lock(LockModeType.PESSIMISTIC_WRITE)`)，在分配序列前对 `identifier_sequence_registry` 中对应 namespace 的行进行排他锁检索。当且仅当持有排他锁时，对 sequence 递增并写回，避免脏读及并发分配重复。

### 决策 3：前端模式切换与动态验证 (Frontend Dynamic Validation)
前端元数据项在未手动输入标识符时会触发 Element Plus 表单拦截。
- **方案**：前端新增单选开关 `autoGenerate`。当 `autoGenerate = true` 时，输入框禁用，前端通过自定义 `validator` 跳过非空校验，向后端提交时，该字段传 `null`；后端 `ItemAdministrationService` 检测到该字段为空时，自动使用有效策略生成后写入。

## Risks / Trade-offs

- **[Risk] 并发自增行锁导致写入吞吐下降** → **Mitigation**: 悲观排他锁是基于 `(namespace, identifier_type)` 行记录的。只有在同一个 Namespace 下且同一个类型的标识符并发分配时，才需要等待行锁；多命名空间相互独立，不会阻塞。
- **[Risk] 配置了错误的模板字符（非法注入）** → **Mitigation**: `TemplateGenerationStrategy` 本身拥有严密的白名单模式拦截（`TEMPLATE_WHITELIST`），若渲染模板不满足安全白名单，策略解析自动报错并安全降级到 UUID。
