# Tasks: default-registrar-auto-activation

## 1. 后端

- [x] 1.1 `ActiveRegistrarService.resolveActiveRegistrar` 默认驱动解析链（持久化有效→默认绑定 Registrar→首个兜底，派生时写回 active_registrar_id）；验证：服务测试（持久化有效沿用/默认绑定激活并持久化/默认绑定是 Contact 时回落首个/持久化失效重派生）
- [x] 1.2 `ContactService.listMyContacts` 默认置顶稳定排序；验证：listMyContacts 顺序断言测试

## 2. 前端

- [x] 2.1 `AppLayout.vue` 移除下拉切换器与身份选择对话框，保留只读 tag（testid 不变）与事件派发；验证：AppLayout.spec 更新/新增（无下拉、只读展示），全量 vitest 绿

## 3. 验证与归档

- [ ] 3.1 全量测试 + build + static 同步 + detect_changes + 归档
