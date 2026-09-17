# Design: registry-specification-full-editing

## Context

注册规范实体（`RegistrySpecification`，ISO/IEC 11179-3:2023 §9.4.10 表 28）与
`RegistrySpecificationRequest` DTO 已覆盖标准全部属性；缺口全部在前端设置页
（`RegistrySpecificationSettings.vue`，列表 + 编辑对话框）。指称/定义的多语言编辑
组件（`DesignationDefinitionSection` = DesignationEditor + DefinitionEditor）已在
概念域等对象上使用，可直接复用。详见 `docs/superpowers/specs/2026-09-17-registry-spec-full-editing-design.md`。

## Goals / Non-Goals

**Goals:**

- 设置页开放表 28 全部属性的编辑（补齐替代语言、参考文档标识种类、表示类方案标识、上下文）。
- 指称/定义支持任意语言与上下文编辑（替换硬编码中/英文输入框）。
- 时区格式前后端一致校验，非法输入返回 400 而非 500。

**Non-Goals:**

- 不重构页面为单例表单（保留列表 + 对话框）。
- 不实施"元数据存在后关键字段锁定"（该要求所列字段已迁至命名空间级别且从未实施，随本变更从规范移除）。
- 不改动数据模型、DTO、初始化向导与 `createDefault` 默认值；无迁移脚本。

## Decisions

- **复用 `DesignationDefinitionSection` 而非 `MultilingualTextEditor`**：
  前者仅需 designations/definitions/defaultLanguageId 三个核心输入，无 AI 生成等
  钩子，契合注册规范对话框；后者与 ItemFormDialog 的 AI 功能深度耦合，接入成本高。
  配对编辑（pairings）非 Registry_Specification 属性，`show-pairings=false`。
- **上下文选择器用 `/contexts/options`**：既有轻量端点（id+label），无需分页列表。
- **（追加）向导步骤的字段边界**：向导内的注册规范步骤只放表 28 标量属性；
  上下文（context）在初始化时无可选项（上下文由用户后续创建），指称/定义的
  多语言编辑初始化后在设置页维护——避免向导步骤过载，两处界面职责清晰分层。
- **参考文档标识种类用可创建标签输入**（`allow-create`）：标准说明该值为注册方自定义
  标识形式（URI、文档系统不透明标识等），无法枚举。
- **时区校验位置**：前端 blur 即时反馈；后端将实体 `IllegalArgumentException`
  转 `BusinessException`（400），保证 API 直接调用时的契约一致性。

## Risks / Trade-offs

- [设置页对话框字段增多，表单变长] → 指称/定义折叠面板（DesignationDefinitionSection 自带 collapse）+ 既有注册标识分区，视觉分层不变。
- [指称/定义编辑器模型切换引入映射 bug] → 打开编辑时保留行 id 与 contexts 原样回传；vitest 覆盖映射与 payload 组装。
- [移除"关键字段锁定"规范要求与历史决策冲突] → 该要求从未实施且所列字段已不存在于实体；在 spec delta 中 REMOVED 并注明原因，存档后主规范一致。

## Migration Plan

无数据迁移。前端构建产物随常规打包发布；后端变更仅错误码语义（500→400），
无破坏性接口变更。

## Open Questions

无。
