# Design: personal-factory-reset-confirmation

## Context

个人版免登录（`personal-no-login-registrar-default`）后，admin 账户 `password_hash` 为明文哨兵 `NO_PASSWORD`，`FactoryResetService` 的 `passwordEncoder.matches(rawPassword, "NO_PASSWORD")` 恒失败/异常，且用户无任何可知密码——恢复出厂设置对个人版不可用。探索确认：团队版重新初始化无缺口（`InitWizardService.setup()` 已无条件 `resetSuperAdminToNoPassword()`）；`executeFactoryReset` 上游影响面 LOW（唯一直接调用方为 `SystemController`）。动机与范围见 proposal.md。

## Goals / Non-Goals

- 目标：个人版恢复出厂设置恢复可用，确认强度不低于团队版的防误操作水平，并补上脚本化远程触发的边界防护；团队版行为零变化。
- 非目标：两段式令牌/倒计时确认；修改自动认证语义；动团队版重建管理员行为。

## Decisions

### D1：个人版确认手段 = 固定短语输入（方案 A），而非纯勾选框（方案 B）

个人版系统内不存在任何秘密，固定短语与密码不同，其价值是"刻意确认"仪式（挡手滑），而非身份验证。选 A 弃 B：A 的确认强度显著更高（必须逐字输入），实现成本相近。

- 短语定为 `恢复出厂设置`（与功能同名，用户无需记忆）。
- 后端常量精确匹配（trim 后），错误返回 422；前端输入完全匹配前禁用提交按钮（GitHub 删库式交互）。

### D2：个人版本机边界 = 回环地址校验（方案 C），而非两段式令牌（方案 D）

个人版对无令牌请求自动以管理员身份处理，若服务绑定非回环地址，局域网设备或恶意网页可代发请求。静态短语挡不住脚本，唯一相称的边界是网络层：`InetAddress.getByName(remoteAddr).isLoopbackAddress()`，非回环返回 403。

- 仅个人版附加该校验；团队版不加（远程管理员合法）。
- 基于直接连接的 `remoteAddr`；本地反向代理场景下代理地址通常仍为回环，可接受。
- **仅接受 IP 字面量判定（评审 MAJOR 修复）**：绝不触发 DNS——`InetAddress.getByName` 对非字面量（如 `127.0.0.1.nip.io` 这类解析到回环的主机名）会做真实 DNS 解析，一旦 `remoteAddr` 形态被影响（如启用 `forward-headers-strategy`）本机边界即被绕过。实现为字面量解析：IPv4 认可 127.0.0.0/8（逐八位组 ≤255），IPv6 认可 `::1`/全展开形/IPv4 映射回环；null、畸形值、主机名一律拒绝。
- 弃 D 理由：一次性令牌对单机个人版属过度设计，且引入 prepare/execute 两段状态，复杂度不成比例。

### D3：`reseedAdminUser` 按清表前读取的 edition 参数化

`executeFactoryReset` 清表会连带清空 `system_setting`（含 `system.edition`），因此必须在 `clearAllTables()` 之前读取 edition 并传入重建逻辑：个人版写 `NO_PASSWORD` 哨兵（与"个人版无密码概念"语义一致），团队版维持 `Admin@123` 真实哈希（行为不变；且团队版重新初始化的 `NO_PASSWORD` 前置条件本就由 `setup()` 无条件兜底，两种写法均安全，选行为不变的一侧）。

### D4：DTO 校验下沉到 service 按版本分支

`FactoryResetRequest` 两个字段均不加 bean-validation 必填（`password` 去除 `@NotBlank`），由 service 按 edition 强制：团队版校验密码非空+匹配，个人版校验短语非空+精确匹配。理由：同一 DTO 服务两个版本的互斥必填集，bean-validation 表达按版本条件必填过于别扭，且 service 分支本来就要存在。

### D5：前端版本未知时 fail-closed 到团队版（密码对话框）

`DangerSettingsPage` 挂载时若 `auth.edition` 为空则请求 `/init-wizard/status` 补齐；仍未知按团队版渲染。个人版误显示密码框的后果是"功能不可用"（现状即如此），团队版误显示短语框则削弱确认强度——fail-closed 方向必须是团队版。

## Risks / Trade-offs

- [个人版部署于 NAS/局域网直连接入时，非本机设备无法触发恢复出厂] → 有意取舍并写入规范；日常功能不受影响，仅该端点受限；用户可登录 NAS 本机执行。
- [`InetAddress.getByName` 对畸形地址抛异常] → try-catch 包裹，解析失败按非本机处理（拒绝方向）。
- [现有调用方/测试依赖 `@NotBlank password`] → `SystemControllerTest` 与 e2e 同步更新；影响面已在 proposal 中列为代码改动。

## Migration Plan

无数据迁移。纯行为变更：个人版由"不可用"恢复可用，团队版不变。回滚 = 还原提交。

## Open Questions

无。
