# Session Journal — 2026-09-20 — item-mapping-clause11-rebuild

## 完成事项

- 用户询问"映射方案"是否存在于 ISO/IEC 11179-3：经 Milvus 全文核实**不存在**（第 11 章为 Item_Mapping package；Mapping_Scheme/mapping scheme 零命中）。用户指令：界面取消 + 回归 Item_Mapping 重新设计。
- 按 superpowers-openspec-superpowers-workflow 走完规划全程：设计稿（方案 A 获批：彻底移除+同 change 重建，3 决策点用户确认：externalUri 删除、订阅功能整体移除另立项）→ OpenSpec change `item-mapping-clause11-rebuild`（proposal/design/specs/tasks，validate 通过）。
- 实现完成：
  - 移除 MappingScheme 全栈 + 映射订阅全栈（notification/ 包整体删除）+ quality/diff 服务 + 7 个 DTO + 三方言 V20260920001。
  - 按第 11 章重建：MappingDegree 14 值枚举；ItemMapping extends Item（JOINED）；ItemMappingSubject/Object 关联类（owning FK + LAZY Item + mapping_variable_name + item_mapping_id 唯一约束）；MappingHierarchy；ItemMappingService/ImpactAnalysisService 重写（标量投影查询避免 Item 联合子类全图加载）；web 新增 ItemMappingCrudService/Controller/DTO（/api/v1/item-mappings + hierarchy）。
  - V20260920002 三方言重建脚本；前端删两页面 + 新建 ItemMappingList.vue（SmartItemPicker 复用）+ i18n + 菜单/e2e；静态产物同步进 web。
  - openapi.json 从运行实例重新生成；README 更新。
- 验证：item-mapping 322 tests / web 1151 tests（clean 后）全绿；前端 build + e2e menu 8 passed；SQLite 现有库副本迁移+API smoke 全通（建/查/层级/notation 校验 400/删）；SQL Server 真实库迁移成功、schema 精确收敛、应用运行于 8081。

## 关键发现（后续 change 需注意）

1. **SQL Server/达梦上 Hibernate ddl-auto=update 会为关联生成不可预测命名的 FK**——删表迁移必须先动态删除引用 FK（sys.foreign_keys / all_constraints 游标）。SQLite 无此问题（方言不生成 FK）。
2. **SQLite 上 Flyway 的 DDL 回滚不可靠**：迁移失败报 "Changes successfully rolled back" 但 DDL 已持久化、仅历史行回滚。SQLite 迁移脚本必须全幂等（DROP IF EXISTS / CREATE IF NOT EXISTS），任何半执行残留态可重跑收敛。
3. **`~/.metadata-registry/application-local.yml`（spring.config.import）会把本地启动覆盖到 SQL Server**，且 spring.config.import 累加式、命令行无法关闭；smoke 需用 `java -jar` + 显式命令行参数覆盖。
4. 全新库全量迁移重放被**既有** V20260919002（语言扩展 change，未提交）阻塞：language_identification.language_id NOT NULL——属上一个 change 的遗留，已记入 CURRENT_STATE。
5. web 模块删除源码后必须 `mvn clean`——残留 target/classes 旧类会被组件扫描（NoClassDefFoundError 集成测试全灭）。
6. @Valid 校验失败项目约定 422（GlobalExceptionHandler），非 400。
7. 开发库（metadata_registry.db）无任何 Flyway 历史（baseline 20260918002 设计）；SQL Server 库历史在 mdr schema。
8. Item 是 JOINED 根（30+ 子类表）：一切批量/遍历场景用标量投影查询；findById/count 会触发整图 join。

## 环境处置

- 杀掉了两个遗留 dev 进程：PID 49884（8081 旧实例，锁 jar）与 PID 90704（07:35 残留 javaw）。新实例（新代码+已迁移 SQL Server 库）现运行于 8081，已恢复用户环境。
- 用户 SQL Server 库曾被我的一次误连尝试 Flyway 20001（FK 3726 失败、整体回滚、verified 未变）；修复后正式迁移成功。

## 待办（下一步）

- 待 `continue-review`：评审（含按 OpenSpec 变更分组提交、ARCHITECTURE.md 于 GitNexus 重建索引后重新生成）。
- 达梦方言两脚本仍待实例实测（标注在脚本头）。
- 网络图 getNetworkGraph 每边一次 findById 取 description（N+1，已知取舍）。

## 评审修正迭代（2026-09-20 下午）

用户评审指出：①界面"条目"与上级菜单"项目映射模块"不一致；②"映射度"应作"项目映射程度"；③degree 取值英文应小写、空格、无下划线（如 related to），与 ISO/IEC 11179-3 Table 35 原文枚举值一致。已实施：
- 前端 i18n/类型/页面：主体条目→主体项目、客体条目→客体项目、映射度→项目映射程度；degree 传输送值改为标准小写（zh 中文标签不变，页面追加 `(标准值)` 括号，如 "语义等价 (semantically equivalent)"）。
- 后端全链路标准值：`MappingDegree.standardValue()` + `@JsonValue`/`@JsonCreator`；新增 `MappingDegreeConverter`（autoApply，落库小写，参照 AcceptabilityConverter）；`WebMvcConfig` 注册查询参数转换器；`CaseInsensitiveEnumConfig` 移除 MappingDegree；三方言迁移 CHECK 约束改标准小写值（已修订 V20260920002）。
- SQL Server 库修复：删除 flyway 20260920002 历史行使修正后脚本重放（表为空，安全）；应用现运行于 8081。
- 重验证：item-mapping 324 / web 1151 tests 全绿；前端 build 通过、静态已同步；openapi.json 重生成。
- 教训：web 打包内嵌 item-mapping jar，改 item-mapping 后必须重装再打包（否则 NoSuchMethodError）。

## 评审修正迭代二（2026-09-20 傍晚）：subject/object 放开 1..*（忠实标准 11.4）

用户裁定：一个映射可选多个主体项目和多个客体项目，每个绑定各自携带 mapping_variable_name（Item 与 Item_Mapping 多对多 + Subject_Mapping/Object_Mapping 关联类）。已按原标准忠实实现：
- OpenSpec 规格/设计（D3b）更新。
- 实体：ItemMappingSubject/Object 的 itemMapping 改 @ManyToOne、移除唯一约束；ItemMapping.subjects/objects 改 @OneToMany 集合。
- 仓储：删 pair 投影查询，改为 findMappingIdsBySubject/ObjectItemId + findDegreesByMappingIds + 每绑定行投影；派生分页查询改名 findBySubjects_Item_Id 系（漏改会导致 39 集成测试上下文加载失败）。
- ItemMappingService：EndpointBinding record；create/update 收绑定列表（各 ≥1 校验，集合 clear+rebuild）；网络图按 subject×object 笛卡尔展开边。
- ImpactAnalysisService：按映射条目集合 + 绑定角色判定方向（同侧保守 BOTH）。
- web DTO：subjects/objects 端点列表（@NotEmpty）；CrudService 批量解析/组装。
- 新迁移 V20260920003（三方言）去除唯一约束。SQL Server 教训：内联 UNIQUE 约束须 ALTER TABLE DROP CONSTRAINT（DROP INDEX 报 3723）；`DROP INDEX IF EXISTS schema.idx` 语法不会报错但也不生效（须 ON 子句）。
- **JPA 教训：@Enumerated(STRING) 会覆盖 autoApply 转换器**——MappingDegree 落库标准小写值须移除 @Enumerated（AcceptabilityConverter 字段即无 @Enumerated）。
- 前端（agent-6）：SmartItemPicker allowMultiple 多选 + 每所选项目一行变量名输入；表格 el-tag 多值。
- 重验证：item-mapping 337 / web 1153 全绿；SQLite 现有库迁移+新契约 API smoke（多主体创建/层级/校验 400）全通；SQL Server 20003 应用、唯一约束已除、8081 运行新代码；openapi.json 重生成；静态已同步。

## 评审修正迭代三（2026-09-20 晚）：界面禁显 UUID 原则（全应用彻查）

用户裁定：界面上任何数据行不得显示 UUID（内部标识），优先业务标识（identifier）+ 指称（designation），解析失败也不回退 UUID；要求彻查全应用。
- 彻查（explore 代理）：发现 16 个文件违规/风险——映射列表与对话框 tooltip、映射网络图（节点短码/tooltip 全 UUID/中心节点裸输入）、互联互通列表（两列必然显示 UUID）、命名规范"标识符"列（实为 UUID 短码）、SmartItemPicker 回填失败回显原始 UUID、上下文/合并 toast/配对编辑器/参考文档选项/命名空间选项/AppLayout 注册人员回退/AiDebugDrawer 等。
- 修复：新增共享 composable `useItemLabelCache`（50ms 微批反查 metadata search，会话级缓存；加载中/未知条目占位）；16 文件统一改业务标识+指称或占位。SmartItemPicker 增加 selectedLabels 回显缓存，未解析值显示"未解析条目"。
- 后端：`InteropMappingController.toResponse` 补 `interopDataElementName`（deIdentifier | deName，原注释 TODO）；localItemName 由前端反查。`InteropMappingControllerTest` 补新依赖 @MockBean。
- 验证：后端 337+1154 全绿；前端 build + vitest 198/198；全局 grep 无 slice(0,8)/shortId 残留；8081 已部署新包；openapi.json 重生成；SQLite smoke PASS。
- 教训：部署脚本要先杀干净 jar 进程（LISTENING PID 与实际 java 进程可能不一致，会残留持锁）。

## 评审修正迭代四（2026-09-20 夜）：编辑对话框回填根因 + 标签精简 + 交互增强

用户截图反馈：编辑映射时多选折叠标签出现 4 个"未解析条目"、标签含版本/创建于、需红色✕快捷移除与 +N 展开清单。
- 根因：`useSmartPicker.fetchById` 的 AbortController 会取消上一次未完成请求——5 个并发回填反查前 4 个全被中止。修复：SmartItemPicker onMounted 回填改串行 await，命中项写入 selectedLabels 与 picker.hits（options 源），多选编辑 0 未解析。
- 标签格式全局精简为「identifier | itemLabel」（SmartItemPicker formatLabel，去版本/创建于，连带删 metaLine/dayjs）。
- 措辞：未解析/未知/已删除"条目"→"项目"（zh；en 本为 item）。
- 交互：每所选项目行左侧红色✕移除（removeEndpoint）；collapse-tags-tooltip 全局生效（+N 点击展开）。
- 验证：build + vitest 198/198；8081 部署新包（教训：LISTENING PID 与实际 java 进程可能不一致，须全量枚举 java/javaw 进程再 taskkill，否则 repackage 失败且误启动旧 jar）。

## 评审修正迭代五~七（2026-09-20 深夜）：映射表格显示与表头底色

- 第5轮：表格主体/客体列——单元格只显示指称（useItemLabelCache 升级结构化存储，新增 designationFor()，labelFor 行为不变）；两列统一 el-tooltip 悬浮层（去掉列级 show-overflow-tooltip），悬浮层纵列列表「标识 | 指称」限高滚动。
- 第6轮：操作列表头底色根因——EP fixed 列克隆表头 .el-table__fixed-header-wrapper 未被全局表头色规则（layout.scss #f0f7f2）覆盖；先修映射页 :deep 补丁。
- 第7轮：全局根治——layout.scss 表头规则扩展覆盖 fixed 克隆表头 + fixed-right-patch + fixed hover 行；DataTablePage/InteropMappingList 的 .modern-table（#f8fafc）同法对齐；映射页补丁回删。全应用排查仅三处自定义表头色。
- 验证：build + vitest 198/198；8081 部署新包；openapi 重生成。

## 归档（2026-09-21）

- `openspec archive item-mapping-clause11-rebuild -y` 成功：openspec/specs/item-mapping/spec.md 新建（7 需求）；standardize-menu-layout 应用菜单 delta；change 移入 openspec/changes/archive/2026-09-21-item-mapping-clause11-rebuild。
- 评审阶段用户追加修正：表格重构（去 fixed 列、回退全部补丁规则）、悬浮层限高改为 >15 项才启用 .scrollable、浅色主题（#f7fbf8）、操作列一行（清 EP 默认 12px 按钮外边距）、移除页头副标题。
- 待跟进：ARCHITECTURE.md 待 GitNexus 重建索引后 `gitnexus wiki --lang chinese` 重新生成；提交按 change 分组。
