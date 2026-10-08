# Factory Reset Delta Specification

## MODIFIED Requirements

### Requirement: MUST Admin Authentication and Password Validation
MUST 为了防范越权和误操作风险，“恢复出厂设置”功能必须强制进行严格的身份验证与二次确认，确认机制按部署版本差异化：团队版（TEAM）必须验证调用者拥有超级管理员（ADMIN）角色，并且必须验证所输入的管理员当前密码是否正确；个人版（PERSONAL）无登录、无密码概念，必须改用输入固定确认短语（“恢复出厂设置”）的方式进行二次确认，且 MUST NOT 要求或校验任何密码。请求体中密码与确认短语均为可空字段，由服务端按当前部署版本强制对应的必填校验。

#### Scenario: Non-admin user attempts factory reset
- **WHEN** a user without the ADMIN role calls the factory reset endpoint
- **THEN** the system SHALL reject the request and return a 403 Forbidden status code.

#### Scenario: Admin user submits incorrect password
- **WHEN** 团队版已认证管理员调用恢复出厂设置端点但提供的当前密码不正确
- **THEN** the system SHALL reject the request and return a 400 Bad Request status code indicating password verification failure.

#### Scenario: 团队版提交空密码
- **WHEN** 团队版调用方提交空（或缺失）密码调用恢复出厂设置端点
- **THEN** the system SHALL 按认证失败处理，返回 400 Bad Request（错误码 401），提示密码验证失败（注：本变更前空密码由控制器校验前置拦截返回 422，语义化归一为认证失败，属有意契约收紧）

#### Scenario: 个人版提交错误确认短语
- **WHEN** 个人版调用方携带确认短语调用恢复出厂设置端点，但短语与目标短语“恢复出厂设置”不一致（含空值）
- **THEN** the system SHALL reject the request and return a 400 Bad Request with 业务校验错误码 422，提示确认短语不正确

#### Scenario: 个人版提交正确确认短语
- **WHEN** 个人版调用方从本机携带正确确认短语调用恢复出厂设置端点
- **THEN** the system SHALL 跳过密码校验并执行恢复出厂设置

### Requirement: MUST Frontend UI Control and Double Confirmation
MUST 前端控制台必须在系统设置模块中提供“恢复出厂设置”的操作入口。由于该操作是不可逆的毁灭性数据清除，UI 必须展示醒目的红色危险警告、执行中展示全屏加载状态以防网络中断或重复提交，并按部署版本提供差异化的二次确认：团队版强制管理员输入当前密码进行二次确认；个人版 MUST NOT 出现密码输入，改为要求用户逐字输入确认短语“恢复出厂设置”，输入与目标短语完全匹配前提交按钮 MUST 保持禁用。部署版本无法确定时，前端 MUST 按团队版（密码确认）渲染。

#### Scenario: UI validation and execution flow
- **WHEN** 团队版管理员点击“恢复出厂设置”按钮，阅读警告，填写正确密码并点击“确认”
- **THEN** 前端 UI SHALL display a loading overlay, trigger the backend reset API, clear Pinia/localStorage user authentication state, show a completion message, and automatically redirect the browser to the initialization wizard route.

#### Scenario: 个人版 UI 确认短语流程
- **WHEN** 个人版用户点击“恢复出厂设置”按钮，阅读警告后进入确认对话框
- **THEN** 前端 SHALL 展示需输入的确认短语“恢复出厂设置”且不呈现密码输入框；输入与短语不完全一致时“确认重置”按钮保持禁用，完全一致后方可提交，提交后进入全屏加载并自动跳转初始化向导

## ADDED Requirements

### Requirement: MUST 个人版本机来源限制
MUST 个人版（PERSONAL）部署下，恢复出厂设置端点仅接受来自本机回环地址（127.0.0.0/8 或 ::1）的请求：非本机来源的请求 MUST 被拒绝并返回 403 Forbidden（HTTP 400 + 错误码 403 的业务错误响应），无论其确认短语是否正确。团队版 MUST NOT 附加该限制。个人版对未携带凭证的请求自动以超级管理员身份处理，本机边界是系统内无任何可验证秘密前提下的强制防护。

#### Scenario: 个人版本机来源调用
- **WHEN** 个人版系统收到来自回环地址（127.0.0.1 或 ::1）且确认短语正确的恢复出厂设置请求
- **THEN** the system SHALL 正常执行恢复出厂设置

#### Scenario: 个人版非本机来源调用
- **WHEN** 个人版系统收到来自非回环地址的恢复出厂设置请求
- **THEN** the system SHALL 拒绝请求并返回 403 Forbidden（业务错误码 403），且不执行任何数据清除

#### Scenario: 团队版远程来源调用
- **WHEN** 团队版系统收到来自非回环地址、密码正确的恢复出厂设置请求
- **THEN** the system SHALL 不受本机限制，按团队版密码校验流程正常处理

### Requirement: MUST 个人版重建管理员为无密码哨兵状态
MUST 执行恢复出厂设置重建默认超级管理员（admin）账户时，按恢复操作发起时的部署版本写入初始口令状态：个人版 MUST 将 `password_hash` 写入无密码哨兵值（`NO_PASSWORD`），保持“个人版无密码概念”语义一致；团队版 MUST 维持既有行为（重置为初始默认密码哈希）。部署版本在清表前读取，重建过程 MUST NOT 依赖已被清空的系统设置数据。

#### Scenario: 个人版恢复出厂后管理员口令状态
- **WHEN** 个人版系统成功执行恢复出厂设置
- **THEN** 重建的 admin 账户口令状态为 `NO_PASSWORD` 哨兵值，与个人版初始化向导不落库密码的语义一致

#### Scenario: 团队版恢复出厂后管理员口令状态
- **WHEN** 团队版系统成功执行恢复出厂设置
- **THEN** 重建的 admin 账户口令为初始默认密码哈希，团队版行为与既有版本一致
