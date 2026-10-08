# Proposal: personal-factory-reset-confirmation

## Why

个人版（PERSONAL）免登录改造后，系统中不存在任何用户密码（admin 的 `password_hash` 为 `NO_PASSWORD` 哨兵），而"恢复出厂设置"仍强制要求输入当前用户密码做二次确认——用户没有任何可知密码，该功能对个人版完全不可用。但按部署版本规范，恢复出厂设置是版本切换的唯一途径，必须保留并恢复可用。

## What Changes

- **后端按版本分支确认机制**：`FactoryResetRequest` 去除密码必填约束并新增可空 `confirmationPhrase` 字段；`FactoryResetService` 按部署版本分支校验——个人版校验确认短语（固定短语"恢复出厂设置"）替代密码校验，团队版维持现有密码校验不变。
- **个人版本机边界**：个人版恢复出厂设置端点额外校验请求来源为回环地址（127.0.0.0/8 或 ::1），非本机来源返回 403。个人版对无令牌请求自动以管理员身份处理，本机边界是系统内无秘密前提下的唯一真实防护。
- **个人版重建管理员语义一致**：恢复出厂设置重建默认管理员时，个人版写入 `NO_PASSWORD` 哨兵（团队版维持 `Admin@123` 真实哈希不变）；edition 在清表前读取。
- **前端按版本渲染确认对话框**：个人版第二步对话框改为"输入确认短语"（GitHub 删库式，输入与目标短语完全匹配才可提交），团队版维持密码对话框；无法确定版本时 fail-closed 按团队版渲染。
- **规范同步**：`openspec/specs/factory-reset` 的确认需求按版本差异化改写，并新增个人版本机边界与 `NO_PASSWORD` 重建需求。

非目标：

- 不引入倒计时/两段式令牌确认；
- 不改变个人版自动认证过滤器语义；
- 团队版一切既有行为（密码校验、重建 admin/Admin@123、重新初始化流程）保持不变。

## Capabilities

### New Capabilities

（无。）

### Modified Capabilities

- `factory-reset`：确认机制按部署版本差异化（团队版密码 / 个人版确认短语 + 本机来源限制）；新增"个人版本机来源限制"与"个人版重建管理员为 NO_PASSWORD 哨兵"需求。

## Impact

- **代码**：`metadata-registry-web` 4 个文件（`FactoryResetRequest`、`FactoryResetService`、`SystemController` 及其测试）；`metadata-registry-ui` 3 个文件（`api/system.ts`、`DangerSettingsPage.vue` 及新增组件测试）。
- **API**：`POST /api/v1/system/factory-reset` 请求体由必填 `password` 变为可空 `password` + 可空 `confirmationPhrase`；个人版非本机来源返回 403，短语错误返回 422。openapi 描述需同步。
- **数据**：无迁移；无数据模型变更。
- **兼容**：个人版功能由"不可用"恢复为可用；团队版请求/响应行为完全不变。
- **部署**：个人版若直连接入局域网（非经本机反向代理），恢复出厂设置仅能本机发起——有意取舍，已写入规范。
- **依赖**：无新增依赖。
