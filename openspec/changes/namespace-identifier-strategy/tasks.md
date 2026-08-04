# Tasks

## 1. 段模型引擎（common-facilities）

- [ ] 1.1 段模型 POJO 与 JSON 序列化（LITERAL/VARIABLE/SEQUENCE/DATE/CHECKSUM，RANDOM 可选）
- [ ] 1.2 生成器：按段有序拼装，接受变量值上下文
- [ ] 1.3 格式校验正则派生器与反向解析器（含生成→解析往返一致）
- [ ] 1.4 序号回填：按解析结果对齐各 scope 计数器

## 2. 顺序号作用域

- [ ] 2.1 `SequenceRegistry` 增加 `scope_key` 列，唯一约束改为 (`namespace_id`, `scope_key`)（实体注解优先；如需迁移脚本按三方言目录提供）
- [ ] 2.2 `SequenceProvider`/`SequenceRegistryService` 支持按 scope_key 原子分配（沿用悲观锁）
- [ ] 2.3 序号溢出检测与溢出策略处理

## 3. 策略整合（web）

- [ ] 3.1 `IdentifierStrategyConfig` 新增段模板策略类型；registry/loader 接入解析
- [ ] 3.2 `ScopedIdentifierService`：生成入口接受 variables；外部命名空间拒绝无标识创建；全局默认回退短路
- [ ] 3.3 策略配置 REST：查询/草稿/发布/停用/预览试生成/格式校验
- [ ] 3.4 业务策略选项收敛：UUID 仅内部兜底，移除 URN 与独立 SEQUENCE 选项

## 4. 初始化

- [ ] 4.1 `InitWizardService.setup`：`createNamespace` 改强制 true，显式 false 返回业务校验错误
- [ ] 4.2 初始化向导 UI：命名空间步骤取消跳过，策略配置默认段模板（可载预设）

## 5. 前端设计器与表单

- [ ] 5.1 段规则设计器组件（段块条/调色板/参数面板/实时预览/校验提示）
- [ ] 5.2 预设库：WS/T 363 数据元标识符预设（JSON 资源）
- [ ] 5.3 命名空间设置页按性质分支（自有=设计器+版本策略；外部=手动说明+格式校验）
- [ ] 5.4 注册表单按模板动态渲染变量段输入并校验

## 6. 测试

- [ ] 6.1 段引擎单测：各段类型、作用域组合、溢出、往返一致
- [ ] 6.2 scope 并发分配集成测试
- [ ] 6.3 WS/T 363 金样本：`DE01.00.001.00` 生成、解析、回填
- [ ] 6.4 外部命名空间：拒绝生成、全局回退短路、导入格式校验
- [ ] 6.5 初始化：命名空间必选、策略同步发布
