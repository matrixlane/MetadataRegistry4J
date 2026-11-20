# 2026-11-20 姓名类数据元概念链补齐 + 命名字符集缺陷 OpenSpec 提案

## 一、姓名类数据元（DE02.01.039.*）内容链补齐（MCP 实操作业）

- 起因：用户经 MCP 检索"血压/姓名"数据元，发现 search-metadata 为子串匹配（"血压"匹配不到"收缩压"，非分词问题，数据未丢失）。
- 诊断 `get-item-detail` 500：根因 = `AdministeredItemService.toResponse()` 第 138 行 `Hibernate.unproxy(item)` 触发 Item 继承树整树 eager join（31 子类表 + 级联 >64 表），SQLite "at most 64 tables in a join" 硬限制；SQL Server 无此限制（SQLite 方言特有，未修，建议另案）。
- 补齐作业（全部经 MCP，除挂链与身份绑定外）：
  - 前置修复：admin 账号绑定既有默认 Registrar（`PUT /api/v1/registrars/{id}`，本部署 ext_contact 为空系历史遗留）。
  - 新建 1 共享 Property「姓名」+ 49 ObjectClass + 94 DataElementConcept（复用既有 94 个 CD-VD02.01.039.*），全部 draft→submit→CANDIDATE（144/144）。
  - 94 个 DE 经业务面 `PUT /data-elements/{id}` 挂新 DEC（94/94，保留原 VD/指称/精度，计入版本快照）。
  - 命名字符集硬编码白名单导致 5 条标准指称被拒（患者/法定代理人、医嘱(处方)开立医师、主任（副主任）医师、Ⅰ助、Ⅱ助），被迫改写——直接引出下半场的 spec。
- 语义核验：DE→DEC→OC+P→CD→VD 全链 94/94 零缺陷。

## 二、默认命名规则字符集缺陷 OpenSpec 提案（本会话交付物）

- 变更：`openspec/changes/default-naming-rules-standard-alignment/`（proposal/design/specs/tasks 四件套，`openspec validate --changes --strict` ✓）。
- 设计稿：`docs/superpowers/specs/2026-11-20-default-naming-rules-standard-alignment-design.md`。
- 数据实证：`designation` 表 1,331 条去重指称违反默认白名单，55+ 种字符（全角括号 838/831、顿号 354、斜杠 236、罗马数字 35、希腊字母 36、单位符号 90+）。
- 机制定位：`NamingConventionEngine.buildDefaultRules()` 硬编码三规则（长度+字符集+默认正则），全局套用于 `ItemAdministrationService`/`DesignationService`/`DataGovernanceReadinessService`；命名空间 charset 机器规则（rulesJson type=charset）已实现但未接线。
- 方案 B（推荐）：默认仅长度规则（1-255）；字符构成限制下沉为命名空间 Naming_Convention 可选机器规则；就绪评分口径同步改为约定合规。
- 状态：待用户确认设计稿与 tasks 清单 → continue-dev。
- 注意：`baseline-data-init-rebuild`（并行活动变更）tasks.md 存在既有 "2.4 duplicated" 警告，非本次引入。

## 三、实施收官（同一日，continue-dev 后）

- 方案 B 落地：引擎默认规则集仅长度规则（1-255）；parseJsonRules 新增 `type=charset`；NamespaceConstraintValidator 接线约定机器规则（强制约定守卫后执行 validate(sign, convention)）；DataGovernanceReadinessService 评分改命名空间约定合规。
- 影响分析：NamingConventionEngine upstream CRITICAL（有意语义变更，签名不变）；NamespaceConstraintValidator 图谱 UNKNOWN 经文本搜索证伪（4 Service + 测试注入）。
- 测试：定向 126/126 绿；全量 1719 例 10F+10E 全部定性为既有基线/环境冲突（端口 8082 占用致 VersionHistoryIntegrationTest 上下文失败、基线版本号漂移、并发锁族、Workspace 族、并行会话 byKeyword 断言），零新增。
- 部署：stop.bat → mvnw clean package -DskipTests（224MB 胖 jar）→ supervisor 拉起（踩坑：restart 控制目录残留 stop.request 致首次静默不启动，删除后 OK，javaw 47180）。
- 命名恢复：8/8（3 OC + 5 DEC）经业务面 PUT 按行 id 就地更新，恢复 WS 363 原标准指称（含 （）/()/Ⅰ/Ⅱ//），DE↔DEC 指称一致性 5/5。
- 验收：openspec validate --changes --strict ✓；detect_changes 低风险无 partial。
- 遗留观察：search-metadata 检索词侧对罗马数字 Ⅰ 有过滤（库内数据正常、按"助姓名"可检），属检索分词独立问题，另行跟进。
