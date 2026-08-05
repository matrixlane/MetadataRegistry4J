# Tasks

## 1. 段引擎规则（common-facilities）

- [x] 1.1 `SegmentType`/`SegmentTypeRegistry` 移除 RANDOM（删除 `RandomSegmentHandler` 注册项；含 RANDOM 段的模板校验报错）
- [x] 1.2 `SegmentTemplateEngine.validate` 新增唯一性段规则：SEQUENCE/TIMESTAMP/UUID 恰好一个，缺失或多占均报错
- [x] 1.3 TIMESTAMP 作唯一性段的精度规则：pattern 连续 `S` ≥ 3，否则校验报错
- [x] 1.4 模板各段最大输出长度之和 ≤ 255 校验；VARIABLE 段新增 `maxLength` 参数（未声明按 32 计）
- [x] 1.5 SEQUENCE 段禁止显式 `scopeRefs`（校验报错）；作用域自动推导：模板含 OBJECT_ABBREVIATION 段则以其解析值为 scope key，否则 `__GLOBAL__`（复用现有 scope_key 分配机制，`SequenceRegistry` 零改动）
- [x] 1.6 `ObjectAbbreviationSegmentHandler` 改经系统级映射表解析：新增 `object-abbreviations.json` 资源与加载器（覆盖全部已注册对象类型，用户可编辑）；段参数 `mapping` 忽略并提示，`default` 保留兜底

## 2. web 后端

- [x] 2.1 `InitWizardService.createNamespaceStrategyConfig` 策略白名单收敛为仅 `SEGMENT_TEMPLATE`（UUID/SEQUENCE/TEMPLATE 拒绝）；默认模板更换为 OBJECT_ABBREVIATION + SEPARATOR(".") + SEQUENCE(pad=5)（无变量段）
- [x] 2.2 `InitWizardSetupRequest` 移除 `identifierPrefix`/`strategySeqStart`/`strategySeqPad` 死字段，同步清理调用点
- [x] 2.3 `IdentifierStrategyAdminController` 新增 `POST /identifier-strategy/preview`（configJson + variables + itemType），与按命名空间预览共用引擎渲染管线，不产生序号分配；另新增 `GET /identifier-strategy/object-abbreviations` 供前端展示系统映射表
- [x] 2.4 预设资源更换：删除 `identifier-presets/wst363-data-element.json`；新增四个通用预设（对象缩写+顺序号、固定前缀+对象缩写+顺序号、对象缩写+UUID、对象缩写+时间戳）
- [x] 2.5 存量模板装载归一化：显式 `scopeRefs` 按自动推导重写并记 WARN；含 RANDOM 或缺唯一性段的存量模板在编辑/发布时报错
- [x] 2.6 生成端 TIMESTAMP 唯一性段触发 `(namespace_id, identifier)` 唯一约束冲突时的有界重试（重取时间戳）

## 3. 前端编辑器（SegmentTemplateDesigner.vue）

- [x] 3.1 调色板按组分类（内容段/唯一性段/修饰段）并移除 RANDOM；唯一性段已存在时再添加弹替换确认
- [x] 3.2 顺序号参数面板移除 scopeRefs 配置项，面板底部静态说明作用域自动推导规则
- [x] 3.3 时间戳 pattern 预设下拉（年月日/完整到秒/毫秒/微秒 + 自定义）；作唯一性段精度不足即时红字提示并阻止发布
- [x] 3.4 段块条上方只读模板表达式视图（形如 `{LITERAL:WN}.{OBJECT_ABBREVIATION}.{SEQUENCE(pad=5)}`），随编辑实时刷新
- [x] 3.5 预览区"再生成一个"按钮，最多连续 5 个样例，顺序号样例按起始值递增展示
- [x] 3.6 预览统一改走后端预览端点（含无命名空间上下文），删除本地简化预览 `localPreview`；端点不可达时降级为前端静态校验（唯一性段计数、必填参数），不阻断提交
- [x] 3.7 OBJECT_ABBREVIATION 参数面板改为系统映射表只读展示 + `default` 兜底输入（移除逐模板 mapping 编辑）
- [x] 3.8 VARIABLE 参数面板新增 `maxLength` 输入（默认 32）

## 4. 初始化向导（InitWizard.vue）

- [x] 4.1 删除"策略类型"单选、`SEQUENCE`/`TEMPLATE` 两段死表单及对应字段与校验规则
- [x] 4.2 命名空间步骤内嵌标识模板编辑器，默认载入"对象缩写+顺序号"编排；`MANUAL_ONLY` 输入模式下编辑器禁用
- [x] 4.3 提交契约：`identifierTemplate` 携带编辑器产出的模板 JSON；`identifierStrategy` 固定为 `SEGMENT_TEMPLATE`
- [x] 4.4 `identifierStrategy.ts` 增加无命名空间预览端点调用

## 5. WS/T 363 特例清除

- [x] 5.1 全仓扫描并清除代码与 UI 中的 WS/T 363 字样（InitWizard.vue 提示文案、SegmentTemplateDesigner.vue 预设占位符、InitWizardService 注释、IdentifierStrategyAdminController 注释等；openspec 目录除外）
- [x] 5.2 测试金样本改写为通用编排（SegmentTemplateEngineTest、SegmentTemplateStrategyIntegrationTest、IdentifierInputPolicyServiceTest 中的 WS/T 363 命名与 `DE01.00.001.00` 样本）

## 6. 测试

- [x] 6.1 引擎单测：唯一性段缺失/多占/合法三者择其一、时间戳精度不足、总长超限、RANDOM 拒绝、显式 scopeRefs 拒绝
- [x] 6.2 引擎单测：作用域自动推导（含对象缩写按类型计数、不含则全局）、系统映射表解析与 `default` 兜底、段内 mapping 忽略
- [x] 6.3 集成测试：`WN.DE.00001`/`WN.DE.00002`/`WN.VD.00001` 按类型计数序列；`{SEQUENCE}` 全局计数；导入 `DE.00001`..`DE.00026` 回填后生成 `DE.00027`
- [x] 6.4 无命名空间预览端点测试：样例/格式正则/校验错误返回正确，且不产生序号分配
- [x] 6.5 向导集成测试：默认模板落库 PUBLISHED、旧策略值请求拒绝、就地编排模板初始化后生效（首个数据元 `WN.DE.00001`）
- [x] 6.6 TIMESTAMP 唯一性段并发撞唯一约束的有界重试测试

## 7. 收尾验证

- [x] 7.1 `.\mvnw.cmd test -pl metadata-registry-common-facilities,metadata-registry-web -am` 通过（1128 测试，0 失败）
- [x] 7.2 前端 `npm run build`（vue-tsc + vite）通过并已刷新 `metadata-registry-web` 内置 static 资源；预览与运行时一致性由 `namespaceAgnosticPreview_matchesRuntimeWithoutAllocation` 集成测试覆盖（手工向导点击验证未执行）
- [x] 7.3 提交前变更范围核对：本会话 GitNexus MCP 工具不可用，按 AGENTS.md 替代方案以 `git status`/`git diff --stat` 人工核对变更范围与预期一致
