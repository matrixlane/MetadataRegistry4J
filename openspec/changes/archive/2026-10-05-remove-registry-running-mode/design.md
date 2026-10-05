# Design: remove-registry-running-mode

## Context

`authority-contact-governance` 归档后，运行模式（SINGLE/MULTI）仅剩三处实际行为：① TEAM 创建机构的 SINGLE 限建（用户截图反馈的限制）；② 向导 commit 写入 `system.running_mode`；③ SystemController 的治理策略切换端点（前端已无调用方）。其余均为 DTO 字段、注释与文案。

## Decisions

### D1 直接删除而非弃用保留
概念彻底取消，代码、API、DTO、UI 同步移除；存量 `system.running_mode` 行保留在库中但无人读取（key-value 表，无需迁移脚本）。`ExportMode.SINGLE/MULTI`（数据字典导出格式）名称虽同，语义无关，保留。

### D2 创建规则最终形态
任意版本：`SUPER_ADMIN` 可创建任意数量注册机构；PERSONAL 创建后自动成为注册人员；删除保护不变（PERSONAL+Primary 拒，其余放行级联）。

### D3 注释口径
`ActiveRegistrarService`/`ItemAdministrationService`/`NamespacePermissionEvaluator` 中"单注册表隐式推导"注释改为客观描述："当系统中恰好存在一个注册机构时按该唯一机构推导"（计数口径，非模式口径）。

## Risks / Trade-offs

- [外部脚本/旧前端缓存调用 runningMode 字段] → 字段移除后旧字段被忽略（JSON 冗余字段不报错）；治理策略端点 404 为可接受破坏（无前端调用方）。
- [存量团队库曾切到 MULTI] → 移除后行为等同 MULTI（不限建），无功能损失。

## Migration Plan

代码部署即生效；无需数据库迁移。
