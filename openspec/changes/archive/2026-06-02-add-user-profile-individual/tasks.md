## 1. 后端数据库与实体模型修改

- [x] 1.1 创建 Flyway 迁移脚本添加 `individual_id` UNIQUEIDENTIFIER 字段及外键约束到 `app_user` 表
- [x] 1.2 在 `AppUser.java` 实体类中添加与 `Individual` 的 `@OneToOne` 单向懒加载关联
- [x] 1.3 更新 `AuthService.java` 中将 `AppUser` 映射为 `UserProfileResponse` 的逻辑以包含 `individualId`

## 2. 后端 API 接口与过滤逻辑实现

- [x] 2.1 在 `AuthController.java` 中实现获取当前用户个人信息 `GET /api/v1/auth/me/individual` 接口
- [x] 2.2 在 `AuthController.java` 中实现修改当前用户个人信息 `PUT /api/v1/auth/me/individual` 接口，若无关联实例则自动新建
- [x] 2.3 在 `SecurityConfig.java` 中配置授权规则，允许任何已登录用户访问 `GET/PUT /api/v1/auth/me/individual`
- [x] 2.4 修改 `IndividualService.java` 的 `list` 查询方法，通过 JPA Specification 排除已禁用用户关联的 `Individual` 实例

## 3. 前端菜单与路由配置

- [x] 3.1 在 `zh-CN.json` 翻译文件中添加对应的国际化配置（如 `nav.my` 等）
- [x] 3.2 在 `router/index.ts` 中注册 `/my-profile` 路由并更新路由表 `routeTable`
- [x] 3.3 在 `AppLayout.vue` 系统侧边栏菜单的最底部新增“我的”菜单选项

## 4. 前端个人中心页面与权限限制

- [x] 4.1 新建 `views/profile/MyProfile.vue` 页面，支持展示和保存当前登录用户的个人基本信息
- [x] 4.2 修改 `views/individual/IndividualList.vue` 页面，将列表的创建、编辑、删除动作及权限控制绑定至 `auth.isRegistrar`

## 5. 系统验证与联调

- [x] 5.1 验证超级管理员登录后默认无 Individual 实例，在“我的”中保存后能自动创建并呈现在“个人”界面中
- [x] 5.2 验证禁用用户后，该用户的个人信息在“个人”界面被隐藏
- [x] 5.3 验证只有注册管理员（REGISTRAR）及以上角色能在“个人”界面中创建或删除独立个人
