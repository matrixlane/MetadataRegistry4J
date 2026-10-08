# 实施计划：registry-specification-model-correction（2026-10-09）

任务清单以 `openspec/changes/registry-specification-model-correction/tasks.md` 为准（7 节 30 任务）。本计划只规定执行顺序、分工与验证门。

## 分工

| 切片 | 执行者 | 范围 |
|---|---|---|
| A 后端 | coder 子代理 | tasks 1（实体+守护测试）、2（服务/DTO/仓库/联邦/搜索）、5（后端测试）；TDD：守护测试先行看红 |
| B 前端 | coder 子代理（并行） | tasks 4.1-4.5（Vue/TS/spec 测试）；只做 vitest+vue-tsc+build，不同步 dist、不碰 mvn |
| C 迁移 | 主代理 | tasks 3（三方言 V20261009008） |
| D 规则与记忆 | 主代理 | tasks 6（AGENTS.md 实体继承规则、session journal、LEARNING_BACKLOG） |
| E 集成验证 | 主代理 | tasks 7（全量构建、测试基线对照、真实库迁移冒烟、重启实测、detect-changes、openspec validate）+ dist→static 同步与 jar 交付 |

## 顺序与门禁

1. A/B/C/D 并行 → 门禁：A 的 `-pl metadata-registry-web -am test` 目标类全绿 + B 的 vitest/vue-tsc 过。
2. E：全量 `mvn clean package` → 前端 build + dist→static → 杀旧后端 → 真实库备份 + 应用 V20261009008 → 重启冒烟（启动就绪探针、注册规范页读写、默认上下文、默认语言）→ `detect-changes --scope all` 无 partial → `openspec validate --all`。
3. 回滚预案：迁移执行前对真实库做备份副本；冒烟失败即还原副本 + 还原 jar（保留旧 jar.original）。

## 风险点（执行时盯防）

- `ObjectClassService.byKeyword` 泛型上限 `<T extends Item>`：A 切片必须整体替换调用，不得给 ItemAdministrationService 加非 Item 重载。
- `RegistrationAuthorityDeletionService:138` 的 `context_id=NULL` 语句必须原样保留。
- `createDefault` 签名不变（InitWizardService 调用点零改动）。
- jar 交付全程（10-08 事故教训）：同步 dist→static、重打胖 jar、解文件锁、重启实测。
