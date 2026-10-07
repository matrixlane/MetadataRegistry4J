# Admin Emergency Login Delta Specification

## ADDED Requirements

### Requirement: 登录类机制仅团队版适用

首登免密与强制改密、应急登录码、普通用户临时密码重置等全部登录类机制 SHALL 仅适用于 `TEAM` 版。`PERSONAL` 版不存在登录流程，上述机制 MUST NOT 对个人版产生任何行为：个人版不签发会话令牌、不触发强制改密（498）拦截、应急码文件不参与认证。存量 `PERSONAL` 库升级后，账号的强制改密与临时密码标志 MUST 由迁移清零。

#### Scenario: 个人版不触发强制改密

- **WHEN** `PERSONAL` 版系统的管理员账号仍带强制改密标志（存量库未迁移场景除外），或任何个人版会话访问业务接口
- **THEN** 系统 MUST NOT 返回 498 强制改密拦截

#### Scenario: 个人版应急码不参与认证

- **WHEN** `PERSONAL` 版部署目录存在应急码文件
- **THEN** 该文件对个人版无任何作用（无登录端点消费它）

#### Scenario: 团队版机制不受影响

- **WHEN** `TEAM` 版系统运行首登改密、应急码登录或临时密码重置
- **THEN** 行为与本变更前完全一致
