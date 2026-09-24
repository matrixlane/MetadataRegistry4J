# Proposal: registry-specification-full-editing

## Why

注册规范是注册表的固有组成部分（ISO/IEC 11179-3:2023 §9.4.10，"Describes the environment in which a registry operates"）。初始化向导已不再单独配置注册规范（setup 时自动创建默认值），因此「系统设置 → 注册规范」页是该对象的唯一配置入口。当前该页只开放了部分标量属性：替代语言、参考文档标识种类、表示类方案标识、上下文 4 个属性完全缺失，指称/定义仅硬编码中/英文两个输入框，与其"注册表运行环境完整描述"的定位不符。

## What Changes

- 注册规范编辑对话框补齐 4 个缺失属性编辑：
  - `alternativeLanguages`（0..*，语言多选）
  - `referenceDocumentIdentifierKinds`（0..*，标签输入）
  - `representationClassSchemeId`（0..1，输入框）
  - `contextId`（0..1，上下文选择器，`/contexts/options`）
- 指称/定义编辑从硬编码中/英文输入框替换为既有 `DesignationDefinitionSection`
  （DesignationEditor + DefinitionEditor，支持任意语言与上下文）。
- `defaultTimeZone` 前端增加 `[-+]HHMM` 格式校验；后端将实体
  `setDefaultTimeZone` 抛出的 `IllegalArgumentException` 转为 400 业务校验错误
  （现状为 HTTP 500）。
- 规范文件修正：`registry-specification` 中"元数据存在后关键字段锁定"要求
  所列字段已不在注册规范实体（标识/版本策略已迁至命名空间级别），且从未实施，
  予以移除，避免与属性开放编辑冲突。
- **（人工验收反馈追加）初始化向导恢复注册规范配置步骤**：位于命名空间之后、
  管理员密码之前，开放表 28 全部标量属性编辑（含替代语言、参考文档标识种类、
  表示类方案标识、合规三件套、时区、网址、注释等），以系统默认值预填；setup
  按所填值创建注册规范（仍不可跳过）。上下文与指称/定义多语言编辑仍在初始化后
  设置页维护，不进入向导。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `registry-specification`: 注册规范属性在设置页的完整编辑能力（表 28 全属性 + 继承的指称/定义），含替代语言、参考文档标识种类、表示类方案标识、上下文编辑，以及时区格式校验的前后端正负路径。

## Impact

- `metadata-registry-ui`：`RegistrySpecificationSettings.vue` 单文件改造（新字段 + 指称/定义编辑器替换 + 校验）；vitest 单测补充。
- `metadata-registry-web`：`RegistrySpecificationService.applyRequest` 时区校验异常转 400；单测补充。数据模型与 DTO 不变，无迁移脚本。
- 无第三方依赖变更；既有 API 契约不变（仅新增 UI 已存在字段的提交与一处错误码语义修正）。
