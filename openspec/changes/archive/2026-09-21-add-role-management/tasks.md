# Tasks: add-role-management

## 1. 后端

- [x] 1.1 `RoleRepository` 增派生查询：`findByRoleNameContainingIgnoreCase`、`existsByRoleNameIgnoreCase`、`findByRoleNameIgnoreCase`
- [x] 1.2 `ContactRepository` 增 `countByRoleId(UUID)`
- [x] 1.3 DTO：`RoleRequest`（roleName `@NotBlank`）、`RoleResponse`（含 contactCount/inUse）、`RoleOption`
- [x] 1.4 `RoleService`：list 关键词搜索、create/update 唯一校验、delete 引用保护、options 排序、集合字段归一
- [x] 1.5 `RoleController`：`/api/v1/roles` 六个端点（list/get/options/create/update/delete）

## 2. 前端

- [x] 2.1 `api/resources.ts` 增 `roleApi`（crud + options）
- [x] 2.2 `views/role/RoleList.vue` 列表页（搜索/分页/多值邮箱电话录入/引用计数列）
- [x] 2.3 `router/index.ts`：`/roles` 指向 RoleList，routeTable 注册改 `implemented`

## 3. 测试与验证

- [x] 3.1 `RoleServiceTest`：CRUD、重名拒绝、删除保护、关键词列表、options 排序
- [x] 3.2 web 模块全量测试 1167 通过 0 失败（并行会话后端进程锁定 target DLL，lifecycle 的 copy-auth-dll 被阻断，改以 `compiler:compile compiler:testCompile jacoco:prepare-agent surefire:test` 直达目标执行，覆盖同一套测试）
- [x] 3.3 前端 `npm run build`（vue-tsc 类型检查）通过
- [x] 3.4 端到端验证：临时后端实例（personal profile + 临时 SQLite 库）+ `scratch/e2e_role_api.py` 15 项 API 断言全过；浏览器实测 /roles 页面——列表展示向导种子角色"注册管理员"（引用 1）、新建"数据管理员"成功、删除被引用角色被拒并提示"角色正被 1 个联系人引用，不能删除"
- [x] 3.5 `detect_changes` 已执行（critical 评级源自工作树中并行会话的在途改动与落后 30 提交的索引；本变更为新增文件+增量修改）；README 页面清单去"规划中"标注
