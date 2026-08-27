## MODIFIED Requirements

### Requirement: 前端标识符生成模式切换与验证控制 (Dynamic Validation and Form Control)

前端项创建表单 MUST 在"标识符"输入框的右侧内嵌一个切换控件（与输入框视觉复合，承载于 `el-input` 的 `append` 槽位，如 `el-segmented` 或等价的紧凑单选组件），提供"自动生成"与"手动输入"两种模式；该内嵌控件仅在所选命名空间的 `identifierInputMode = AUTO_WITH_OVERRIDE` 且非编辑态下显示，`AUTO_ONLY` 与 `MANUAL_ONLY` 模式下 MUST 隐藏（无需切换，由命名空间输入策略直接确定）。当内嵌控件选中"自动生成"时，标识符输入框 MUST 被禁用、提示"保存后自动生成"占位符，且前端表单必填校验 MUST 放过；当内嵌控件选中"手动输入"时，输入框 MUST 启用并强制必填校验，可按命名空间 `manualIdentifierPattern` 校验。当编辑已有项时，标识符输入框 MUST 置灰禁用，内嵌切换控件 MUST 不显示。同样的内嵌控件模式 SHALL 应用于版本字段：`AUTO_ONLY` / `MANUAL_ONLY` / 编辑态 MUST 隐藏控件；自动模式下版本输入框禁用并提示"保存后自动生成"占位符；手动模式下启用并按命名空间 `versioningPolicy` 校验。

#### Scenario: 切换为自动生成模式

- **WHEN** 用户在新建元数据表单中通过标识符输入框右侧的内嵌切换控件选中"自动生成"
- **THEN** 标识符输入框变为禁用状态，值被清空，且前端表单通过"标识符必填项"的校验

#### Scenario: 切换为手动输入模式

- **WHEN** 用户在新建元数据表单中通过标识符输入框右侧的内嵌切换控件选中"手动输入"
- **THEN** 标识符输入框变为可用状态，用户可以直接编辑，并且前端提交前必须通过"标识符不能为空"的非空规则校验

#### Scenario: 编辑已有元数据项

- **WHEN** 用户打开已有数据元的编辑表单
- **THEN** 内嵌切换控件不显示，标识符输入框呈现禁用状态，显示当前的标识符，且不可编辑

#### Scenario: AUTO_ONLY 命名空间下隐藏内嵌切换控件

- **WHEN** 所选命名空间的 `identifierInputMode` 为 AUTO_ONLY
- **THEN** 标识符输入框右侧的内嵌切换控件 MUST 隐藏，标识符输入框禁用并提示"保存后自动生成"

#### Scenario: MANUAL_ONLY 命名空间下隐藏内嵌切换控件

- **WHEN** 所选命名空间的 `identifierInputMode` 为 MANUAL_ONLY
- **THEN** 标识符输入框右侧的内嵌切换控件 MUST 隐藏，标识符输入框启用并强制必填（可按 `manualIdentifierPattern` 校验）

#### Scenario: 版本字段内嵌切换控件在 AUTO_WITH_OVERRIDE 下显示

- **WHEN** 所选命名空间的 `versionInputMode` 为 AUTO_WITH_OVERRIDE 且非编辑态
- **THEN** 版本输入框右侧的内嵌切换控件 MUST 显示，默认选中"自动生成"；切换至"手动输入"后版本输入框启用并按命名空间 `versioningPolicy` 校验

#### Scenario: 版本字段内嵌切换控件在 AUTO_ONLY / MANUAL_ONLY / 编辑态下隐藏

- **WHEN** 所选命名空间的 `versionInputMode` 为 AUTO_ONLY 或 MANUAL_ONLY，或当前为编辑态
- **THEN** 版本输入框右侧的内嵌切换控件 MUST 隐藏；AUTO_ONLY 下版本输入框禁用并提示"保存后自动生成"；MANUAL_ONLY 下启用并强制必填；编辑态下输入框禁用并保留当前版本号
