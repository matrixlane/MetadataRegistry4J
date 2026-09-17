
## 2026-09-18 追加：改动被回滚事件

本变更的**后端全部改动**（Individual.avatar、AuthService/AuthController 头像方法、Avatar DTO、三份迁移、两个测试文件）曾被完全回滚（`git checkout`/stash 类操作，疑似另一会话的清理动作），前端 MyProfile.vue 也被回滚。`identifier` 扩展字段的并行改动同期反复出现/消失两次。

处置：逐一核对后完整重做全部修改（代码与上次一致），测试再绿（15 用例 exit 0），重新 build 前端、同步 static、打包并重启后端（javaw PID 变更，8081 监听正常，avatar 端点 401 已注册）。教训：**工作区被并行会话共享，大改动后应尽快 git commit 固化**。
