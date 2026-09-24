# system-initialization 增量（add-submitter-contact-identity）

## ADDED Requirements

### Requirement: 个人版自动成为 Registrar

初始化向导 SHALL 按版本差异化处理"超级管理员是否成为 Registrar"：`PERSONAL` 版 SHALL NOT 向管理员提供该选择——当前超级管理员 SHALL 自动成为所创建注册机构的 Registrar（后端强制 `selfAsRegistrar=true`，前端不展示开关并提示"自动"）；仅 `TEAM` 版 SHALL 提供该选择的开关，由管理员自行决定。

#### Scenario: 个人版无需选择自动成为

- **WHEN** 管理员在初始化向导选择 `PERSONAL` 并进入注册机构步骤
- **THEN** 向导不展示"设为注册管理员"开关，以"自动"标识说明当前超级管理员将自动兼任 Registrar；提交后管理员即持有 Registrar 身份

#### Scenario: 团队版提供选择开关

- **WHEN** 管理员在初始化向导选择 `TEAM` 并进入注册机构步骤
- **THEN** 向导展示"将当前超级管理员设为该机构的注册管理员"开关，管理员勾选则提交后成为 Registrar，不勾选则不创建
