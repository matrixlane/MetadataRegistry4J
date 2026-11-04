# 2026-10-22 administered-item-reference-documents-ui 真实库收官会话

## 背景

该变更仅剩真实库相关任务（1.3/2.4/3.4/5.7/5.8/6.x）。在装有 SQL Server 2022 的原开发机
（Lane-Desktop）继续推进。真实库 `metadata_registry`：10,708 受管项、10,717 提交关联
（10,707 条挂同一共享提交记录）、10,706 参考文档关联。设计文档记载的
11,177/11,182/23,449 为另一批次基准快照，本次全部口径以实测为准。

## 执行结果（全部完成）

| 任务 | 结果 |
| --- | --- |
| 4.5 | vitest 371/371（37 文件）+ build（vue-tsc+vite）通过 |
| 5.7 | 三个 db-baseline 脚本 identified_item 表引用清零；`-f 65001` NOEXEC 解析通过；sqlite 版 dummy 库验证语法/行为/幂等；真实库闭环校验 remaining=0 |
| 1.3 | 回退 D1 JPQL 红绿对照实测：修复后 size=20 0.44s/0.18s；旧查询不再复现 40s+（Hibernate 6 join elimination，SQL 不再触 registered_item）；证据口径已改写 |
| 2.4 | 按受管项查两表各 3 逻辑读取（全表 42 页）；STATISTICS PROFILE 确认 Index Seek |
| 5.8 | 五项全库不变式全 0；三样本双向追踪同对；API 交叉复核一致 |
| 6.1 | BACKUP DATABASE + RESTORE VERIFYONLY（28MB → scratch/admin-item-refdocs-20261022/） |
| 6.2 | 真空态基线 0.153s / 0.163s |
| 6.3 | 快照→清空→不变式回填→快照补回；终态 10,718 行（= 快照 +1 共享关联补齐），unlinked=0、dup=0、EXCEPT 双向仅差该 1 行 |
| 6.4 | 回填后 0.144s / 0.199s / 0.048s（workspace），全亚秒；size=1000 1.88s |
| 3.4 | size=20 返回 20 条，meta.referenceDocumentIds 与库 20/20 一致 |
| 6.5 | 真实 Chrome：页签展示 WST 363.2 及其标题；搜索新增 WST 364.2 即时落库（1→2）；行内移除即时生效（2→1）；数据恢复原状；截图 5 张 |

## 关键证据位置

- `scratch/admin-item-refdocs-20261022/measure-results.txt` — 全部计时与校验输出
- `scratch/admin-item-refdocs-20261022/ui/*.png` — 6.5 截图
- `scratch/admin-item-refdocs-20261022/backfill_partA_snapshot_clear.sql` /
  `backfill_partB_restore.sql` — 6.3 幂等回填（集合式，事务包裹）
- `scratch/admin-item-refdocs-20261022/{green,red}.jar` — 红绿对照构建产物

## 环境备忘（下次别再踩）

- sqlcmd 读 UTF-8 脚本必须 `-f 65001`；Git Bash 传文件路径必须 `cygpath -w`
  （裸传会「拒绝访问」或 GBK 误读中文注释报 102）。
- 应用连 SQL Server 靠 `~/.metadata-registry/application-local.yml`（personal profile
  + 该覆盖文件），pool=10；启动 `java -jar ... --spring.profiles.active=personal`。
- vite dev 必须落在 5173（CORS 白名单）；5173 被占时先查 netstat，别直接换端口。
- 数据脚本改动 DB 时，应用无需重启（查询实时读数据）。
