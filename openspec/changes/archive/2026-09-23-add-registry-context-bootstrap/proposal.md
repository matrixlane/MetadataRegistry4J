# Proposal: add-registry-context-bootstrap

## Why

ISO/IEC 11179-3:2023 要求所有指称与定义必须至少关联一个上下文，且该要求对条目本身是上下文时依然适用；标准明确这不构成无限回归，并给出最佳实践：设立一个 "registry context"（附录 C 的 Context1），所有上下文的指称/定义归入其中（含其自身），且上下文自身的指称/定义可自指。当前系统无任何 Registry Context 种子，`RegistrySpecification.context`（标准 Table 28："context which represents the registry itself"）始终为空；系统在初始化完成后缺少代表注册系统自身的默认上下文这一标准引导数据。

## What Changes

- 新增 `RegistryContextBootstrapService`：幂等创建 Registry Context（条目主键固定 UUID，仅作幂等键/系统引用），创建时机为**初始化向导完成（setup 并 commit）、注册机构自有命名空间的标识/版本生成规则配置之后**，由向导 commit 端点在向导事务提交后组合调用（独立事务）；创建失败仅 log warn，不阻断初始化（重启补建兜底）。
- Registry Context 作为已注册条目（AdministeredItem）创建：**所在命名空间为管理员向导中创建的注册机构自有命名空间**（`ra.getNamespace()`，签名层强制，不用 `sys:` 系统默认命名空间）；**标识符与版本由该命名空间的生成规则产出**——AUTO 类策略走 `IdentifierInputPolicyService` 自动生成（如 `CTX.00001` / SEMVER 初版 `1.0.0`），**仅 MANUAL_ONLY 策略使用固定字符串** identifier `registry-context`、version `1.0`。
- 自动填写指称（`Registry Context` / `注册系统上下文`）与定义（中英文，忠实取自 11179-3:2023 8.2.2.2 原文），语境全部自指（`SELF_CONTEXT_REFERENCE` 哨兵）。
- 创建后将 `RegistrySpecification.context` 回填指向该 Registry Context。
- 新增启动 Runner `RegistryContextBackfillRunner`：仅对**已初始化**的既有库做一次性补建（未初始化系统的启动期不创建，满足"晚于规则设置"的时序约束）；`ContextTestDataInitializer` 补 `@Order(100)` 保证测试种子不被抑制。
- 三方言迁移 `V20260923002__Acceptability_check_standard_lowercase`：重建 5 张表（designation/definition/两处语境关联/配对）的 acceptability CHECK 为大小写不敏感，与 `AcceptabilityConverter` 按 ISO/IEC 11179-3:2023 表 20 的标准小写落库对齐——修复既有平台缺陷（存量库上设置可接受性必失败，SQLite 重建表已实测，SQL Server/达梦带守卫未实测）。
- 恢复出厂与 `abort()` 无需改动（重跑向导自然重建）。
- 默认命名规范正则规则允许指称含空格（与默认字符集规则一致；ISO/IEC 11179-3:2023 对指称字符构成无强制限制，格式约束属各命名空间可选的命名约定范畴）。**影响全局**：所有指称默认校验口径同步放宽。

## Capabilities

### New Capabilities

- `registry-context-bootstrap`: 注册系统默认上下文（Registry Context）的引导创建——时序（晚于标识/版本规则配置）、命名空间来源（RA 自有命名空间）、标识/版本生成（随命名空间策略，MANUAL_ONLY 固定字符串回退）、自指指称/定义、幂等语义、`RegistrySpecification.context` 回填、既有已初始化库的启动补建。

### Modified Capabilities

- `system-initialization`: 初始化向导的业务数据创建范围扩展——在系统初始化提交（commit）时创建 Registry Context（归属 RA、命名空间为 RA 自有命名空间、标识/版本由已配置规则生成，独立事务、失败不阻断），并回填注册规范的默认上下文。

## Impact

- **代码**：新增 `metadata-registry-web/.../service/RegistryContextBootstrapService.java`、`.../seed/RegistryContextBackfillRunner.java`；修改 `InitWizardService`（commit() 接线 + setup 无改动）、`ContextTestDataInitializer`（仅新增 `@Order(100)`）、`RegexNamingConventionRule`（默认正则允许空格）。
- **数据库**：零实体变更；新增三方言迁移 `V20260923002`（重建 5 张表 acceptability CHECK，修复既有平台缺陷）；新增数据行（context、designation/definition、语境关联、scoped_identifier、administered_item 等）。
- **API**：无新增/变更端点。
- **数据库**：新增三方言迁移 `V20260923002`（重建 5 张表 acceptability CHECK 为大小写不敏感，修复既有平台缺陷）；新增数据行（context、designation/definition、语境关联、scoped_identifier、administered_item 等）。
- **前端**：无改动。
- **既有部署**：已初始化库在下次启动时自动补建 Registry Context 并补挂 `registry_specification.context_id`。
- **标准依据**：ISO/IEC 11179-3:2023 §6.4.2.3（Context）、§8.2.2.2（定义文本）、§8.5.1.1/§8.5.2.1 注（自指与无限回归）、Table 28（Registry_Specification.context）、附录 C（Context1 示例）。
