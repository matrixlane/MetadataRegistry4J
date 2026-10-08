# 个人版恢复出厂设置确认机制改造 — 设计文档

- 日期：2026-10-08
- 状态：已确认（用户批准方案 A + C，豁免后续确认）
- OpenSpec 变更：`personal-factory-reset-confirmation`

## 背景与问题

恢复出厂设置的确认链路为：UI 第二步强制输入密码 → `POST /api/v1/system/factory-reset`（`FactoryResetRequest.password` `@NotBlank`）→ `FactoryResetService` 用 `passwordEncoder.matches(rawPassword, admin.passwordHash)` 校验。

个人版（PERSONAL）初始化时 `InitWizardService.commit()` 明确不接收、不落库任何密码，admin 的 `password_hash` 为明文哨兵 `NO_PASSWORD`（`InitWizardService.java:337`）。因此：

- 用户没有任何可知密码，功能对个人版完全不可用；
- 即使输入 `NO_PASSWORD` 哨兵，它不是合法 BCrypt 哈希，`matches` 异常/恒 false。

按 `deployment-edition` 规范，恢复出厂设置必须保留（版本切换的唯一途径），不能砍功能。

## 已澄清的事实（探索结论）

1. **团队版无 NO_PASSWORD 缺口**：`InitWizardService.setup()` 第 133-137 行无条件调用 `resetSuperAdminToNoPassword()`，注释明确兜住"恢复出厂后重建真实哈希"的情形。此前怀疑的缺口不存在。
2. **威胁模型**：个人版是免登录单机应用，系统内不存在任何用户秘密。风险分两类：
   - 防用户自己的误操作 → 需要"刻意确认"仪式；
   - 防脚本化远程触发（个人版对无令牌请求自动以管理员身份处理，若服务绑定非回环地址，局域网设备/恶意网页可代发请求）→ 任何静态确认都挡不住，只能靠网络边界。
3. **影响分析**（GitNexus upstream，`executeFactoryReset`）：risk LOW，唯一直接调用方为 `SystemController.factoryReset`；3 个 receiver-typing 丢弃点为测试 mock。
4. **`ApiCodes.FORBIDDEN = 403` 已存在**，可直接用于本机边界校验失败。

## 方案（用户已批准：A + C）

### 后端

**`FactoryResetRequest`**：`password` 去除 `@NotBlank`（改为可空）；新增可空字段 `confirmationPhrase`。

**`FactoryResetService.executeFactoryReset`**：签名扩展为接收 `FactoryResetRequest` 与 `remoteAddr`，按 `SystemSettingService.getEdition()` 分支：

- **PERSONAL**：
  1. 本机边界校验：`InetAddress.getByName(remoteAddr).isLoopbackAddress()`，否则 `BusinessException(FORBIDDEN, "个人版恢复出厂设置仅允许从本机发起")`；
  2. 确认短语校验：`confirmationPhrase` trim 后必须等于常量 `"恢复出厂设置"`，否则 `BusinessException(VALIDATION_FAILED, "确认短语不正确")`；
  3. 跳过密码校验。
- **TEAM / edition 为 null**：维持现状——`password` 必填且 `passwordEncoder.matches` 校验；不附加本机限制（团队版远程管理员可能合法）。

**`reseedAdminUser`**：`executeFactoryReset` 在 `clearAllTables()` 之前先读取 edition 并传入：

- PERSONAL → 重建 admin 时 `password_hash` 写 `NO_PASSWORD` 哨兵（与"个人版无密码概念"语义一致）；
- TEAM → 维持 `Admin@123` 真实哈希，行为不变。

（edition 在清表前读取，因为 `system_setting` 会被清空。）

**`SystemController.factoryReset`**：注入 `HttpServletRequest`，将 `request.getRemoteAddr()` 传入 service。

### 前端

**`api/system.ts`**：`factoryReset(payload: { password?: string; confirmationPhrase?: string })`。

**`DangerSettingsPage.vue`**：

- 挂载时若 `auth.edition` 为空则请求 `/init-wizard/status` 补齐；仍无法确定时按 TEAM 渲染（fail-closed 到更强的密码分支）。
- **PERSONAL**：第二步对话框改为"确认短语"对话框——醒目展示需输入的短语「恢复出厂设置」，输入完全匹配（trim）后才可点击"确认重置"（GitHub 删库式）。
- **TEAM**：维持现有密码对话框，行为不变。

### 明确不做

- 不引入倒计时/两段式令牌（方案 D）：对个人版威胁模型属过度设计。
- 不改 `PersonalEditionAuthenticationFilter` 的自动认证语义。
- 不动团队版任何既有行为。

## 测试策略（TDD）

- **后端** `FactoryResetServiceTest` 新增：PERSONAL 短语正确→执行清空+重建 NO_PASSWORD；短语错误→422；非 loopback→403；loopback 含 `127.0.0.1`/`::1`；TEAM 密码错误→401 行为不变。
- **后端** `SystemControllerTest`：DTO 两字段序列化；`remoteAddr` 透传断言；空 payload 在 PERSONAL 下不再触发 422（改由 service 校验）。
- **前端** 新建 `DangerSettingsPage.spec.ts`：PERSONAL 渲染短语框且匹配前按钮禁用、匹配后启用；TEAM 渲染密码框。
- **e2e**：检查现有 factory-reset 用例的 edition 环境并按需补充 PERSONAL 路径断言。

## 风险与兼容

- 个人版此前功能完全不可用，不存在"改坏"问题；团队版行为保持不变。
- 本机边界校验基于直接连接的 `remoteAddr`；若个人版经本地反向代理访问，`remoteAddr` 为代理地址（通常仍是回环）。部署在 NAS/局域网且直连接入的场景下，用户需在本机执行恢复出厂——这是方案 C 的有意取舍，写进 spec。
