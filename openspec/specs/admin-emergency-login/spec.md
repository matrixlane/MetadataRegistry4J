# Admin Emergency Login Specification

## Purpose
This capability provides the super administrator (`admin`) with a first-login passwordless initialization flow, a local-file emergency login code recovery mechanism for forgotten passwords, an account self-protection guard, and an admin-driven temporary-password reset for regular users that enforces a single-use login followed by a mandatory password change.

## Requirements

### Requirement: MUST 超级管理员首次登录免密与强制改密
MUST 当超级管理员账号首次登录系统时，不需要输入密码即可登录，但登录成功后必须强制重定向或弹出强制修改密码页面，不完成新密码设置则无法访问其他系统功能。

#### Scenario: 首次登录免密进入系统
- **WHEN** 数据库中超级管理员账号未设置密码（密码为空或为特定初始标记），且用户以该账号尝试登录
- **THEN** 系统允许登录成功，并生成临时令牌，但标记其状态为"需强制改密"

#### Scenario: 首次登录强制改密
- **WHEN** 超级管理员在"需强制改密"状态下访问系统
- **THEN** 系统强制显示修改密码表单，并拦截所有其他 API 请求
- **AND** 当成功设置新密码后，该强制状态解除，此后登录必须使用新密码

### Requirement: MUST 应急登录码恢复机制
MUST 当超级管理员遗忘密码时，可使用部署目录下的本地应急码文件中的一次性登录码进行登录。应急码验证成功后，该应急码失效并重置，用户强制进入改密流程，且新密码不能与原密码相同。

#### Scenario: 使用应急码登录
- **WHEN** 超级管理员输入用户名，并在密码框中输入当前的有效应急登录码尝试登录
- **THEN** 系统允许登录成功，同时立即将应急码文件中的应急码废弃并生成一个新的随机应急码写入文件
- **AND** 账号状态被标记为"应急登录强制改密"

#### Scenario: 应急登录强制改密与密码查重
- **WHEN** 用户处于"应急登录强制改密"状态，提交修改密码表单
- **THEN** 系统校验新密码是否与之前的密码一致
- **AND** 如果新密码与旧密码不同，则更新密码成功并解除强制状态
- **AND** 如果新密码与旧密码相同，则返回错误提示，要求重新输入

### Requirement: MUST 超级管理员自身安全防护
MUST 超级管理员必须受到保护，系统禁止超级管理员禁用或删除自己当前的账户，只能管理其他的普通账户。

#### Scenario: 尝试禁用自身账户
- **WHEN** 超级管理员在用户管理中尝试点击自身账号的"禁用"按钮，或者发起禁用自身的接口调用
- **THEN** 前端禁用按钮处于禁用状态，且后端返回明确错误信息"不能禁用自己的账号"

#### Scenario: 尝试删除自身账户
- **WHEN** 超级管理员在用户管理中尝试点击自身账号的"删除"按钮，或者发起删除自身的接口调用
- **THEN** 前端删除按钮处于禁用状态，且后端返回明确错误信息"不能删除自己的账号"

### Requirement: MUST 普通用户密码重置与单次登录强制改密
MUST 系统允许超级管理员针对普通用户账户进行"重设密码"操作。重设后生成一个随机密码，该随机密码仅可成功登录一次，且登录后用户必须强制设置新密码。

#### Scenario: 管理员重设用户密码
- **WHEN** 超级管理员在账户列表中点击某个普通用户的"重置密码"并确认
- **THEN** 系统生成一个随机密码哈希更新至该账号，同时将"强制改密"标志设为 true，"临时密码已使用"标志设为 false
- **AND** 接口将该明文随机密码仅返回并显示一次

#### Scenario: 首次使用临时密码登录
- **WHEN** 用户输入正确的临时密码进行登录
- **THEN** 系统允许登录成功，并将"临时密码已使用"标志更新为 true
- **AND** 在返回的身份凭证中携带"强制改密"标志

#### Scenario: 再次使用临时密码登录
- **WHEN** 用户已使用临时密码登录过一次但未修改密码，再次尝试使用该临时密码登录
- **THEN** 系统拒绝登录请求，并返回错误"临时密码已失效，请联系管理员重新重置密码"

#### Scenario: 临时密码登录后强制改密
- **WHEN** 用户通过临时密码登录，处于强制改密状态下，输入新密码并提交
- **THEN** 系统更新用户密码，并将"强制改密"和"临时密码已使用"标志清除（设为 false）
- **AND** 随后用户可以正常访问系统，且后续登录只能使用该新密码
