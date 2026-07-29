## Requirements

### REQ-CE-01 强制 mandatory_naming_convention_indicator
当 Namespace 的 `mandatoryNamingConventionIndicator` 为 TRUE 时，该 Namespace SHALL 至少关联一个 `NamingConvention`；且在该 Namespace 下创建或更新 `Designation` 时，每个 `Designation` 必须至少关联到一个属于该 Namespace 的 `NamingConvention`。

#### Scenario: 创建 Namespace 时启用 mandatoryNamingConventionIndicator 但未传 NamingConvention
- **WHEN** 调用方设置 `mandatoryNamingConventionIndicator = true` 且 `namingConventionIds` 为空
- **THEN** 系统拒绝请求并返回验证错误

#### Scenario: 在强制 Namespace 下创建无 NamingConvention 的 Designation
- **WHEN** Namespace 的 `mandatoryNamingConventionIndicator = true`，且调用方为其下的 Item 创建 Designation 时未指定 `namingConventionIds`
- **THEN** 系统拒绝保存并返回验证错误

#### Scenario: 在强制 Namespace 下创建符合 NamingConvention 的 Designation
- **WHEN** Namespace 的 `mandatoryNamingConventionIndicator = true`，且 Designation 关联了该 Namespace 下的某个 NamingConvention
- **THEN** 系统允许保存

### REQ-CE-02 强制 one_name_per_item_indicator
当 Namespace 的 `oneNamePerItemIndicator` 为 TRUE 时，同一 `Item` 在该 Namespace 下 SHALL 最多只能有一个 `Designation` 和一个 `ScopedIdentifier`。

#### Scenario: 同一 Item 已有一个 Designation，再添加第二个
- **WHEN** Namespace 的 `oneNamePerItemIndicator = true`，且调用方为已有一个 Designation 的 Item 再添加一个 Designation
- **THEN** 系统拒绝并返回冲突错误

#### Scenario: 同一 Item 已有一个 ScopedIdentifier，再添加第二个
- **WHEN** Namespace 的 `oneNamePerItemIndicator = true`，且调用方为已有一个 ScopedIdentifier 的 Item 再添加一个 ScopedIdentifier
- **THEN** 系统拒绝并返回冲突错误

### REQ-CE-03 强制 one_item_per_name_indicator
当 Namespace 的 `oneItemPerNameIndicator` 为 TRUE 时，同一 sign 或 identifier 在该 Namespace 下 SHALL 最多只能对应一个 `Item`。

#### Scenario: 同一 sign 已被 Item A 使用，Item B 再次使用
- **WHEN** Namespace 的 `oneItemPerNameIndicator = true`，且调用方为 Item B 创建与 Item A 相同 sign 的 Designation
- **THEN** 系统拒绝并返回冲突错误

#### Scenario: 同一 identifier 已被 Item A 使用，Item B 再次使用
- **WHEN** Namespace 的 `oneItemPerNameIndicator = true`，且调用方为 Item B 创建与 Item A 相同 identifier 的 ScopedIdentifier
- **THEN** 系统拒绝并返回冲突错误

### REQ-CE-04 冲突检测必须覆盖 Designation、ScopedIdentifier 与 Namespace 自身
`NamespaceConstraintValidator` SHALL 在 `NamespaceService.create/update`、`DesignationService.create/update` 和 `ScopedIdentifierService.create/update` 路径中被调用；`NamingConflictDetector` SHALL 扩展以支持 ScopedIdentifier 冲突查询。

#### Scenario: 创建 Namespace 时自身约束校验
- **WHEN** 调用方创建 `mandatoryNamingConventionIndicator = true` 的 Namespace 但未提供 NamingConvention
- **THEN** `NamespaceService.create` 在校验阶段拒绝请求

#### Scenario: 更新 Designation 时变更 sign 引发冲突
- **WHEN** 调用方更新 Designation 的 sign 为 Namespace 中已存在的 sign
- **THEN** 系统在保存前检测到冲突并拒绝

#### Scenario: 更新 ScopedIdentifier 时变更 identifier 引发冲突
- **WHEN** 调用方更新 ScopedIdentifier 的 identifier 为 Namespace 中已存在的 identifier
- **THEN** 系统在保存前检测到冲突并拒绝
