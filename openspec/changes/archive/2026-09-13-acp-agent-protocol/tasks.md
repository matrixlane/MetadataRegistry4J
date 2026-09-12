# 任务：Coding Agent 接入切换为 ACP 协议

## 1. 后端

- [x] 1.1 `AcpConnection`：NDJSON stdio 客户端（按 id 配对请求/响应、通知按 sessionId 路由、防卫性应答对端反向请求、单行加锁写、读守护线程），Jackson 既有依赖
- [x] 1.2 `AcpAgentProvider`：实现 MetadataInferenceProvider（常驻进程/握手/session 生命周期/文本累积/取消/超时/崩溃重建），替代 CliAgentProvider
- [x] 1.3 注册表与探测：内置定义改 ACP 命令、probe 改 initialize 握手（详情含 agentInfo）、视图 kind="acp"；删除 CliAgentProvider 与 {prompt} 占位机制
- [x] 1.4 设置迁移 v1→v2：丢弃内置 command/args 覆盖、保留启用/显示名/默认提供方，下次保存写 v2；自定义条目原样保留
- [x] 1.5 后端单测：AcpConnection 帧解析/请求配对/通知路由/反向请求应答、AcpAgentProvider 全生命周期（假 ACP server 经 ProcessRunner stub 喂 NDJSON）、崩溃重建、取消与超时、探测握手、设置迁移；inference/assistant 全包回归全绿
- [x] 1.6 真实冒烟：以 scratch 方式（不起应用）对 kimi acp 与 codex-acp 各跑一次最小提示词端到端

## 2. 前端

- [x] 2.1 `api/inference.ts` kind 字面量 cli→acp；AI 辅助设置页 Agent 表单占位/说明文案改为「ACP server 启动命令」；残留「CLI」表述清理（探测详情等用户可见处）

## 3. 收尾

- [x] 3.1 openspec 同步：未归档四变更加 ACP 切换变更记录；本变更 validate --strict 通过
- [x] 3.2 部署：修复 npm 全局 shim（codex-acp/claude-agent-acp）、迁移用户 inference-agents.yml、重打包 jar + dist 同步 static、重启后端验证
- [x] 3.3 GitNexus：编辑前 impact、收尾 detect_changes（无 partial/truncated）
