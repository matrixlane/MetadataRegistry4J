# 注册治理数据（governance）

本数据包承载元数据注册系统**注册工作流所需的治理基准**：注册规范、默认注册机构、CTX 命名空间与默认注册人员/角色/联系人。全新部署经 `init-baseline.bat` 导入后，注册模块（注册规范页、指称/定义创建、注册推进）即可正常工作；缺此包则注册规范页为空，创建指称/定义会被"未配置注册表默认语言"横幅拦截。

## 数据包组成

| 脚本 | 内容 | 依赖 |
| --- | --- | --- |
| `sqlserver/30-governance.sql` / `sqlite/30-governance.sql` | 注册规范（默认注册规范，standard=ISO/IEC 11179-3:2023，context=Registry Context，默认语言 zh-CN）、默认注册机构（mdr+ext、机构行与名称、文档语言）、CTX 命名空间（AUTO_WITH_OVERRIDE/SEMVER 设置、指称"默认命名空间"）、默认注册人员（ext.is_default=1，挂"注册管理员"角色与联系人）、Registry Context 补充双语指称/定义与 preferred 语境挂接 | 必须具备通用基准（`common`），且须在 `data-standards/ws` 的 `20-derived.sql` 之后执行（依赖其产出的 Registry Context） |

与 WS 标准数据一样，脚本**幂等**（存在性守卫，重复执行不产生重复数据）。执行顺序由 `init-baseline.bat` 按包序 `common` → `data-standards/ws` → `governance` 保证。

## 数据来源与判据

- 来源：线上部署治理配置（SQL Server 备份 `metadata_registry-pre-relink-20261029.bak`），2026-10-30 经 `scratch/baseline-rebuild/seed_governance.py` 注入金库，id 与线上一致（恢复旧备份无冲突）。
- **刻意不进本包**：`ext.contact`（账号绑定，基线无账号）、`ext.system_setting`（`system.initialized` 由初始化向导置位）、`ext.sequence_registry` / `ext.identifier_strategy_config`（运行时发号状态/策略）、`ext.registration_transition_rule`（审批流程配置，UI 管理）——属运行时/流程配置，由系统在运行期自建。
- 再生成：`scratch/baseline-rebuild/gen_scripts_sqlserver.py` 自金库同源产出双方言脚本（与 WS 层同一管线）。

## 验证

导入后至少核验：

- 注册规范页可见"默认注册规范"，默认语言为简体中文；
- 注册机构页含"默认注册机构"与 WJW 注册机构；
- 已标识条目的受管链闭环不受影响（治理包行均非注册项）。
