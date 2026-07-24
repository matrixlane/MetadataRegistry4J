## 1. 后端 - 核心功能实现

- [x] 1.1 修改 `DataInitializer.java`，将初始化 `admin` 的密码哈希设为 `"NO_PASSWORD"`。
- [x] 1.2 在 `AuthService.java` 中实现应急登录码管理辅助方法（生成、写入文件、读取验证等）。
- [x] 1.3 在 `AuthService.java` 中引入系统启动时如果不存在 `emergency-code.txt` 则自动生成初始应急码的逻辑。
- [x] 1.4 修改 `AuthService.java` 中的 `login` 方法，支持 `admin` 首次免密登录和通过应急码登录逻辑，并在应急登录成功时自动生成并重写应急码文件。
- [x] 1.5 修改 `AuthService.java` 中的 `changePassword` 方法，当处于应急码登录强制改密状态下，校验并限制新旧密码一致性。
- [x] 1.6 在 `SystemSettingService.java` 中引入 `system.allow_user_registration` 注册配置字段及方法，并修改 `AuthService.register` 方法以校验该开关状态。
- [x] 1.7 修改 `AccountController.java`，在更新、删除和审核接口中增加校验，禁止当前登录用户对自己进行禁用或删除操作。
- [x] 1.8 在 `SystemController.java` 和 `AuthController.java` 中新增对应的允许注册查询和更新接口，并在 `SecurityConfig.java` 中将查询接口设为免登录。
- [x] 1.9 编写 Flyway 数据库迁移文件，在 `mdr.account` 表中新增 `force_password_reset` 和 `temp_password_used` 字段并将默认值设为 `0`。
- [x] 1.10 在 `Account.java` 实体中增加上述两个字段；在 `AccountController.java` 新增仅限管理员调用的重设用户密码接口，并在 `AuthService.java` 中实现重设逻辑、登录拦截逻辑（只允许使用临时密码成功登录一次）与改密状态清除逻辑。

## 2. 后端 - JWT 与安全拦截器配置

- [x] 2.1 修改 `JwtService.java` 的 `generateToken` 方法，写入 `forceChangePassword` 和 `emergencyLogin` claims。
- [x] 2.2 修改 `JwtAuthenticationFilter.java` 以解析新的 claims，并在 `AuthenticatedUser.java` 中新增对应属性。
- [x] 2.3 新建 `ForcePasswordResetInterceptor.java` 拦截器以拦截未完成改密用户的非白名单请求，并返回特定错误状态（如 `498`）。
- [x] 2.4 修改 `WebMvcConfig.java`，注册并启用 `ForcePasswordResetInterceptor`。

## 3. 前端 - 界面与交互功能实现

- [x] 3.1 修改前端 Axios 请求拦截逻辑，捕获 `498` 错误状态码。
- [x] 3.2 在前端主布局或登录后的根组件中实现不可关闭的“修改超级管理员密码”弹窗，强制要求用户完成改密。
- [x] 3.3 对接修改密码接口并配合后端返回状态，在改密成功后自动清理状态、更新本地 Token，使用户可正常操作系统。
- [x] 3.4 修改 `AppUserList.vue`，在用户表格中新增“上次登录时间”列展示，并根据 `auth.user.id` 禁用本账号的“禁用”和“删除”操作按钮。
- [x] 3.5 修改 `Login.vue`，挂载时查询系统注册允许状态，若为关闭状态则隐藏“注册”入口按钮。
- [x] 3.6 修改 `SystemSettings.vue`，增加“允许注册新用户”开关控制。
- [x] 3.7 在 `AppUserList.vue` 的表格行中，针对普通用户行新增“重置密码”操作按钮，调用重置 API，并在成功后通过弹窗一次性展示随机临时密码。

## 4. 验证与测试

- [x] 4.1 编写单元测试验证免密登录、应急码登录、应急码单次有效性、新密码限制，以及不能禁用/删除自己的逻辑。
- [x] 4.2 验证“允许注册新用户”开关的有效性，确保关闭时注册接口不可用、按钮隐藏。
- [x] 4.3 编写测试或手动验证普通用户临时密码单次使用有效性、登录后强制改密、改密后标志重置等完整闭环流程。
- [x] 4.4 手动进行端到端测试，确保从首次启动到首次登录改密，以及使用应急码登录改密的完整业务流程闭环。

