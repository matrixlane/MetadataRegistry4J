## 1. 数据库结构迁移与表重构

- [x] 1.1 编写 Flyway 数据库迁移脚本，创建 `mdr` 模式（如果不存在）
- [x] 1.2 在迁移脚本中，将系统所有的表 and 序列迁移至 `mdr` Schema
- [x] 1.3 将 `app_user` 表重构为 `mdr.account` 表，移除 `email` 和 `display_name` 字段，增加系统角色 `sys_role` 字段
- [x] 1.4 删除 `registry_user` 表，并在 `mdr.account` 表 and `mdr.individual` 表之间建立一对一关联或外键
- [x] 1.5 清理种子数据/出厂设置服务中的测试数据注入，仅保留运行所必需的初始化数据

## 2. 后端 Java 实体与认证重构

- [x] 2.1 重命名并重构 `AppUser` 实体为 `Account`，更新 `@Table(name = "account", schema = "mdr")` 并精简字段
- [x] 2.2 在所有的实体类中，显式指定 `@Table(..., schema = "mdr")` 以使用新的数据库模式
- [x] 2.3 移除 `User` 实体类，并在代码中所有引用 `User`、`AppUser`、`AppUserRole` 的地方重构为使用新实体 `Account` 与其角色
- [x] 2.4 更新 Spring Security 及 JWT (JwtAuthenticationFilter) 的解析与鉴权逻辑，以适配新的 `Account` 实体
- [x] 2.5 调整 `FactoryResetService` 中对表的重置与初始化写入逻辑，适配 `mdr` Schema 和新表结构

## 3. 注册审核开关与自主注册功能

- [x] 3.1 在 `system_setting` 表中定义控制开关（如 `new_user_require_audit` 字段）并在系统设置 Service 中提供存取 API
- [x] 3.2 实现 `POST /api/auth/register` 用户自助注册 API，基于开关控制新建的 `Account` 的 `enabled` 状态
- [x] 3.3 实现 `PUT /api/accounts/{id}/audit` 账号审核与启用接口，仅允许超级管理员（SUPER_ADMIN）调用
- [x] 3.4 调整 `NamespacePermissionEvaluator` 以及相关的鉴权检查，确保能够根据新的系统角色进行正确的权限控制

## 4. 联系人与个人资料关联逻辑调整

- [x] 4.1 调整单注册表初始化引导服务（`InitWizardService`），注册管理员只生成 `Contact` 记录且 `individual` 字段留空，并将联系人业务角色设置为“注册管理员”
- [x] 4.2 重构个人资料（"我的"）保存服务接口：若当前登录账户未关联 `Individual`，则新建 `Individual` 并与该 `Account` 进行一对一绑定
- [x] 4.3 绑定 `Individual` 时，查询此账户关联的 `Contact` 记录并回填该 `Individual`，确保能同步在联系人列表中建立绑定
- [x] 4.4 更新“个人列表”展示逻辑，隐藏 `enabled = false` 的账户对应的 `Individual`

## 5. 前端界面调整

- [x] 5.1 登录页面添加注册链接，点击可弹出注册表单框
- [x] 5.2 系统设置页面中，添加“新用户注册是否需要审核”的开关组件
- [x] 5.3 用户管理页面中，添加新用户审核与激活的操作按钮，且当开关关闭时审核功能和待审核状态在界面上隐藏
- [x] 5.4 调整“我的”个人资料提交界面，支持首次创建及后续修改，并更新联系人列表中的“角色”列在未创建个人资料时正确显示“注册管理员”
- [x] 5.5 全面移除前端多余的用于仿真业务展示的菜单或硬编码测试数据
