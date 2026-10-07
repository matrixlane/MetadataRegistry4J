# Proposal: clickable-registrar-switcher

## Why

`default-registrar-auto-activation` 将顶栏活动机构标识改为只读。现要求恢复切换能力：点击左上角机构标签弹出选择菜单切换当前注册机构；菜单只列出当前用户担任注册人员的注册机构（无对应注册人员身份的机构不显示）。

## Capabilities

### Modified Capabilities

- `active-registrar-ownership`: "活动身份只读展示"改为"可点击切换展示"。

## Impact

- 前端：`AppLayout.vue`（标签包 el-dropdown click 菜单，选项来自 `activeRegistrar.identities`，选中调 `PUT /active-registrar`）+ 组件测试。单身份时仍为纯只读标签。
- 后端：无改动（`selectActiveRegistrar` 已具备且同步默认绑定，与 activation-follows-default 自洽）。
