# Design: clickable-registrar-switcher

顶栏标签在 `identities.length > 1` 时包一层 click 触发 el-dropdown；菜单项即 identities（后端仅返回本人关联了注册机构的 Registrar 身份，天然满足"仅显示自己为其注册人员的机构"）。选中后调既有 `activeRegistrarApi.select`，后端会把所选身份同步为默认绑定，激活随默认（activation-follows-default）一致。单身份/无身份维持原只读/不展示。
