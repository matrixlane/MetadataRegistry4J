# Tasks

## 1. 段模型引擎（common-facilities）

- [x] 1.1 段模型 POJO 与 JSON 序列化（LITERAL/OBJECT_ABBREVIATION/SEQUENCE/UUID/SEPARATOR/TIMESTAMP/CHECKSUM/VARIABLE，RANDOM 可选）
- [x] 1.2 段类型注册表：每类型提供参数 schema、生成器、正则片段、解析片段、UI 配置面板（可扩展）
- [x] 1.3 对象缩写映射表（对象类型→缩写，可配置；默认 数据元→DE、值域→VD/CV）与生成上下文的条目类型传递
- [x] 1.4 生成器：按段有序拼装，接受变量值上下文
- [x] 1.5 格式校验正则派生器与反向解析器（含生成→解析往返一致）
- [x] 1.6 序号回填：按解析结果对齐各 scope 计数器

## 2. 顺序号作用域

- [x] 2.1 `SequenceRegistry` 增加 `scope_key` 列，唯一约束改为 (`namespace_id`, `scope_key`)（实体注解优先；如需迁移脚本按三方言目录提供）
- [x] 2.2 `SequenceProvider`/`SequenceRegistryService` 支持按 scope_key 原子分配（沿用悲观锁）
- [x] 2.3 序号溢出检测与溢出策略处理

## 3. 策略整合（web）

- [x] 3.1 `IdentifierStrategyConfig` 新增段模板策略类型；registry/loader 接入解析
- [x] 3.2 `ScopedIdentifierService`：生成入口接受 variables 与条目类型（供 OBJECT_ABBREVIATION 推导）；外部命名空间拒绝无标识创建；全局默认回退短路
- [x] 3.3 策略配置 REST：查询/草稿/发布/停用/预览试生成/格式校验
- [x] 3.4 独立策略选项收敛为段模板：UUID/URN/SEQUENCE 不再作为独立策略（UUID 保留为段组件）

## 4. 初始化

- [x] 4.1 `InitWizardService.setup`：`createNamespace` 改强制 true，显式 false 返回业务校验错误
- [x] 4.2 初始化向导 UI：命名空间步骤取消跳过，策略配置默认段模板（可载预设）

## 5. 前端设计器与表单

- [x] 5.1 段规则设计器组件（段块条/组件调色板/参数面板/实时预览/校验提示）
- [x] 5.2 预设库：WS/T 363 数据元标识符预设（JSON 资源）
- [x] 5.3 命名空间设置页按性质分支（自有=设计器+版本策略；外部=手动说明+格式校验）
- [x] 5.4 注册表单按模板动态渲染变量段输入并校验
- [x] 5.5 外部命名空间设置：手动策略说明 + 可选正则校验规则配置（含样例即时测试）

## 6. 测试

- [x] 6.1 段引擎单测：各段类型、作用域组合、溢出、往返一致
- [x] 6.2 scope 并发分配集成测试
- [x] 6.3 WS/T 363 金样本：`DE01.00.001.00` 生成、解析、回填
- [x] 6.4 外部命名空间：拒绝生成、全局回退短路、导入格式校验
- [x] 6.5 初始化：命名空间必选、策略同步发布
