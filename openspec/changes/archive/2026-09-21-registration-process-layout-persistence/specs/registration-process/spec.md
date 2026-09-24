# registration-process Delta

## ADDED Requirements

### Requirement: 布局持久化（Admin 保存与启动预加载）

注册流程页的布局（节点坐标与连线折线段微调）SHALL 支持持久化：持有 SUPER_ADMIN 角色的用户 SHALL 能够将当前画布布局保存到服务器本地 JSON 文件（`layout/registration-process-layout.json`，路径可经 `app.registration-process-layout-file` 配置覆盖）；保存操作 SHALL 校验提交体为 JSON 对象且体积不超过 64KB，并以原子写入生效。任何登录用户 SHALL 能够读取该布局文件用于页面渲染。应用启动时若该文件不存在，系统 SHALL 以打包的预置模板（`classpath:layout-defaults/registration-process-layout.json`）自动播种该文件（开放式脚本，可手工编辑）。

页面加载时 SHALL 按以下优先级合成初始布局：内置默认布局 < 已保存布局文件 < 本次会话内的用户拖动。重置布局 SHALL 恢复至上一次保存的布局（无保存记录时为内置默认）。非 SUPER_ADMIN 用户 MUST NOT 看到保存入口，且保存接口 MUST 拒绝非 SUPER_ADMIN 请求。

#### Scenario: Admin 保存当前布局并预加载恢复

- **GIVEN** 用户以 SUPER_ADMIN 身份拖动节点与连线折线段后点击"保存布局"
- **WHEN** 保存请求成功写入服务器本地 JSON 文件
- **THEN** 页面提示保存成功；刷新或重新打开页面后，节点位置与连线折线段恢复到保存时的状态

#### Scenario: 首启自动播种布局文件

- **GIVEN** 工作目录下不存在布局文件且应用首次启动
- **WHEN** 应用启动完成
- **THEN** `layout/registration-process-layout.json` 由预置模板生成，GET 端点可读取其内容

#### Scenario: 非 Admin 不能保存

- **GIVEN** 用户不具有 SUPER_ADMIN 角色
- **WHEN** 其直接调用保存接口
- **THEN** 系统拒绝该请求（403），且前端不展示保存入口

#### Scenario: 损坏的布局文件不导致白屏

- **GIVEN** 布局文件被手工编辑为缺字段或含非法坐标
- **WHEN** 页面加载并解析该文件
- **THEN** 缺失/非法项回落到下一优先级的默认布局，页面正常渲染
