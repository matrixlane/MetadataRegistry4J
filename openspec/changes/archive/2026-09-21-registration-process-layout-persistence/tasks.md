# Tasks: registration-process-layout-persistence

## 1. 后端

- [x] 1.1 `RegistrationProcessLayoutService`：播种（首启从 classpath 模板复制）、读取、原子写入、JSON 对象与 64KB 校验；验证：新增单测覆盖播种/读/写/校验
- [x] 1.2 `RegistrationProcessLayoutController`：GET/PUT 端点 + `ApiResponse` 包装；验证：编译与单测通过
- [x] 1.3 SecurityConfig 显式规则：PUT 限 SUPER_ADMIN（置于通用写规则之前）；验证：规则行静态审查
- [x] 1.4 预置模板 `layout-defaults/registration-process-layout.json`（含已确认的初始排布）；验证：资源打包入 jar

## 2. 前端

- [x] 2.1 resources.ts 增加 `registrationProcessLayoutApi`（get/save）；验证：编译通过
- [x] 2.2 视图：挂载预加载 savedLayout 并按 内置默认 < savedLayout < 会话覆盖 合成（节点坐标 + 连线 sx/laneY/tx）；重置布局以 savedLayout/默认重建；验证：单测 mock 更新后全量通过
- [x] 2.3 页头"保存布局"按钮（仅 isAdmin），提交全量快照；i18n zh/en；验证：新增单测（Admin 可见/提交体形状）
- [x] 2.4 前端全量 vitest + build 通过

## 3. 部署与验证

- [x] 3.1 后端打包重启（-am，跳测试编译），确认布局文件播种与 GET 端点 200；验证：curl
- [x] 3.2 前端产物随 jar 部署；vite dev 页重载后经代理恢复布局；验证：用户页面目视确认
