# Tasks: activation-follows-default

- [x] 1.1 `resolveActiveRegistrar` 改为目标恒随默认注册人员身份（回退首个），不一致写回同步缓存；`selectActiveRegistrar` 同步 markDefault
- [x] 1.2 测试改写（跟随默认并写回/已一致零写库/默认绑定非 Registrar 回退）；ActiveRegistrarServiceTest 21/21
- [ ] 1.3 全量回归 + 归档
