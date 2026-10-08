# 2026-10-09 RegistrySpecification 元模型纠偏（registry-specification-model-correction）

## 事故链（本次纠偏的根因回溯）

1. **起点（历史偏差）**：`RegistrySpecification` 被实现为 `extends Item`（JPA JOINED），但 ISO/IEC 11179-3:2023 9.4.10 并未声明它是 Item 子类——该 class 小节只有 Description / Associations（"No associations are specified in this metamodel region."）/ Attributes（表 28），**无 "Direct superclass" 小节**；标准中真正的 Item 子类都有固定句式声明（Namespace §7.3.3.1、Context §6.4.2.3、Naming_Convention §8.4.4.1、Constraint_Set §9.4.5.1 等）。2026-10-09 经 Milvus 检索原文逐条复核确认。
2. **级联问题①**：JOINED 继承使 `mdr.registry_specification.id` 成为指向 `mdr.item(id)` 的 FK（Hibernate `ddl-auto=update` 启动时生成，Flyway 脚本中无），历史数据产生"子表有、基表无"的孤儿行 → Hibernate 启动加 FK 失败。
3. **级联问题②**：2026-07-29 `523d7dc4` 打了两个补丁——一次性迁移 `V20260728001`（清存量孤儿）+ 启动兜底监听 `cleanupOrphanRecords()`（ApplicationReadyEvent 上 findAll 全表内存比对）。
4. **级联问题③（生产事故）**：2026-10 WS 363 全量数据恢复后 `mdr.item` 达 25222 行，`cleanupOrphanRecords` 的全表 `findAll()` 阻塞 ApplicationReadyEvent 发布 13+ 分钟，前端启动页（`/api/v1/startup/status` 轮询 ready=true）永久等待。当日先以反连接查询（`findOrphanIds`）缓解性能，随后用户要求根治。

## 纠偏内容（本变更）

- **实体**：`RegistrySpecification extends Item` → `extends Entity`（独立根实体，对齐 Contact/Organization 范式）；表列不变；不再产生 item 基行与 FK。
- **能力移除**：DTO/服务删 designations/definitions/registration/label/meta；`ObjectClassService.byKeyword`（`<T extends Item>` 泛型硬阻塞）改写为按 name 的 Specification；联邦删 `instanceof RegistrySpecification` 过滤；全局搜索删类型分支与白名单条目；前端设置页删「指称与定义」「注册标识」两区块；`api/system.ts` 死代码 `registrySpecificationApi` 删除。
- **兜底退役**：删 `cleanupOrphanRecords()` 与 `findOrphanIds()`（孤儿语义随 FK 根除）；`V20260728001` 保留原样（历史已应用，新模型下自然空转）。
- **三方言迁移 V20261009008**：动态解析（sys.foreign_keys/ALL_CONSTRAINTS）删除 Hibernate 历史 FK（实库名 FK1c3c6293egus14tbd3hxi638a，不可硬编码）→ designation 六关联子表 → designation → definition/slot → item 基行；全守卫幂等，全新库空转。
- **防再发三层**：① `StandardInheritanceConformanceTest`（web 测试目录，纯反射）：硬断言 spec 超类为 Entity；`extends Item` 继承边白名单制（实 27 条：5 条已核实条款引用 + 22 条"待全量审计"占位），未登记继承边构建失败；② AGENTS.md 新增「实体继承规则（强制）」；③ LEARNING_BACKLOG 登记全量审计候选。

## 验证

- 守护测试红→绿：实体改前 3 用例全红（"expected Entity but was Item" + 未登记继承边），改后全绿。
- 目标测试 52/52 绿；web 全量 1492 tests，11 失败全部为既有基线（Workspace 6F+3E、SegmentTemplate 1E、DatasourceConfig 1F，均不引用 RegistrySpecification）。
- 前端 vitest 332/332、vue-tsc 0、build 过。
- **SQL Server 真实库**：备份（scratch/migtest/metadata_registry_predecouple.bak）→ 先在与 migtest 副本验证（FK 0、基行 0、spec 行无损、item 总量 25222 不变、二次执行幂等）→ 真实库应用成功并复核同结果。
- **SQLite**：合成脏库冒烟（spec+基行+designation+关联子表+无关 item 行）——只清 spec 相关，无关行保留，二次执行幂等；历史库副本核实 SQLite 本无该 FK（无需重建表）。
- **重启实测**：新后端 15s 就绪；spec GET/PUT 走通且 DB 复验 0 基行/0 指称行；默认上下文 200；搜索 type=RegistrySpecification 200 空结果；前端 5173 活。
- `detect-changes --scope all` 无 partial/truncated；`openspec validate` 本变更通过（--all 的 2 个失败是并行 WIP 变更 clickable-registrar-switcher 既有问题）。

## 教训（已在 AGENTS.md/LEARNING_BACKLOG 固化）

- **继承关系必须摆标准原文核对 "Direct superclass" 小节**，不能凭类名直觉或二手资料（10-04 naming-convention 回退教训同款：实现前核对子类能力清单）。
- 补丁叠补丁（迁移 + 启动兜底）会掩盖根因；性能问题找根因而不是只修查询（当日先修 findAll 为反连接，用户随即指出必须根治继承偏差——正确决策顺序应以根因优先）。
- 守护测试把"标准声明的继承关系"变成构建期约束，是防再发的机械保证；其余 22 条白名单占位的全量审计已入 backlog。

## 遗留

- 待评审后 OpenSpec 归档（3 个能力 delta：registry-specification 改写 4 需求+新增 1 需求、identification 改写 1 需求、standard-inheritance-conformance 新增 1 能力 1 需求）。
- 全量审计变更（15→27 个 Item 子类逐一按 11179-3/31/32/33/35 核对并补全白名单引用）。
- 真实库回滚备份 scratch/migtest/metadata_registry_predecouple.bak 保留至用户确认后删除。
- 未提交：工作树含本变更 + 并行的 Sign/Text/ContactExtensionRepository 修改，提交需按变更分组。

## 独立评审与修复（2026-10-09 第二轮，双代理并行评审）

**结论：修复后通过**。发现 2 BLOCKER + 4 MAJOR + 9 MINOR，已全部处理或记录：

- **B1（后端）**：三方言迁移引用了已被 V20261009005/09006 删除的旧名表（designation_context_relation/designation_naming_convention）——全新/干净库 Flyway 必炸（评审用 scratch/remove-sysns-smoke/existing.db 证明；我此前的合成冒烟自建了这两张表造成假绿）。修复：删除这两条例句。**连带发现**：version_history 属 ext schema、registration_transition_approval 已迁 ext（mdr 同名旧表为残留）——SQL Server/达梦脚本已改 ext 限定，SQLite 裸名不变。
- **B-1（前端）**：dist→static 同步实际未生效（Git Bash 把 robocopy 的 /MIR 参数当路径转换，静默失败；且此前用管道取 exit code 掩盖了 sqlcmd/mvn 退出码两次）。修复：cp 整目录同步（142=142 资产，新 chunk 验证无残留区块），已重打 jar。
- **M1**：守护测试扫描口径加宽为"直接超类也是 @Entity 的全部实体继承边"（不限 extends Item），白名单新增 4 条已核实条目（Registrar→Contact §9.4.7、RegistrationAuthority→Organization §9.4.6、AdministeredItem→RegisteredItem §9.4.2、AttachedItem→RegisteredItem §9.4.3，均 Milvus 原文核实）。
- **M2**：迁移补 definition_context、designation_definition_pairing 的 definition_id 侧、注册链（scoped_identifier→administered/attached/approval→registered/identified）清理；合成链库冒烟通过、无关行保留。
- **M3**：FK 游标 SELECT DISTINCT（防复合约束重复执行）。
- **M4**：达梦块改为删除后回查、存留则 RAISE_APPLICATION_ERROR（不吞异常）。
- 次要：byKeyword 加 Locale.ROOT + LIKE 通配符转义；5 个文件补末尾换行；design/tasks 文档更正（测试位置在 web、降级语义、守卫表述）；前端删 loadDefaultLanguage 死调用。
- **教训（评审方法）**：① 管道 `cmd | head` 取 `$?` 拿到的是 head 的退出码——sqlcmd/mvn 验证必须直取退出码；② Git Bash 下调 robocopy 须用 `//MIR` 双斜杠防路径转换；③ 迁移冒烟必须用**真实演进路径的库**（如已在 09007 的 existing.db），自建全表假库会造成假绿。
- **范围外记录（交用户决策）**：工作树中 V20260525001__Add_performance_indexes.sql 被并行流程改写（Flyway checksum 风险，提交时需 repair 或回退）；mreg 下残留旧名表（designation_context_relation/designation_naming_convention/registration_transition_approval 等被旧 jar 重建）建议另立清理迁移。

**重验证**：守护测试+RegistrySpecification*Test mvn exit 0（加宽后无未登记边）；前端 registry-spec 16/16 + vue-tsc 0；SQL Server 全新恢复库执行修正版 exit 0 且结果正确（FK 0/基行 0/spec 无损）；SQLite B1 回归库 + M2 链库双冒烟幂等通过；真实库 flyway 历史 09008 行已删、随新 jar 重启自动按修正版重放（幂等无感）。

## 追加：cleanup-stale-legacy-tables（同日，用户指令"清理历史旧表"）

评审 B1 的连带发现落地：mdr 中 11 张被 10-08 旧模型 jar 重建的残留表（9 张为 09005/09006 更名表的旧名 + value_meaning_permissible_value_meaning 冗余表 4674 行旧 jar 并行写入 + ws_import_run 零引用 WIP 残留）经三方言 V20261009009 全守卫删除。判定双证据：建表时间晚于迁移应用时间 + 当前模型零引用（逐一 grep）；ext.contact/ext.registrar 虽同被重建但属现行卫星表，不动。验证：migtest 全新恢复库 11→0、rename 目标与 ext 行数不变、幂等；sqlite 合成库 + 干净库双场景；真实库随新 jar 自动应用成功。openspec 归档为 2026-10-09-cleanup-stale-legacy-tables。
