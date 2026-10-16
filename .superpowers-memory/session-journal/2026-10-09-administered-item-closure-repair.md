# 2026-10-09 注册库内容缺失受管项实例闭环修复

> OpenSpec change：`repair-administered-item-closure`
> 设计稿：`docs/superpowers/specs/2026-10-09-administered-item-closure-repair-design.md`
> 实施计划：`docs/superpowers/plans/2026-10-09-administered-item-closure.md`

## 触发

用户发现数据元对话框「注册状态」恒为 `-`，并指出**这是重大失误**：已导入数据没有
`administered_item`，逻辑上根本没有完成闭环。

## 根因（标准原文核对）

ISO/IEC 11179-3:2023 §9.4.1.2：

> **As an abstract superclass**, Registered_Item shall be instantiated as **either an
> Administered_Item (9.4.2) or an Attached_Item (9.4.3) but not both.**

实测（真实库）：`item` 25,217 / `identified_item` 11,177 / `registered_item` 1 /
`administered_item` 1 / `attached_item` 0。11,177 条全部缺环。

§7.4.1/§7.4.2 的 item_identification / identification 链本库是对的（11,177 条都有
`identified_item` 与 `scoped_identifier`），断点在再往上两层。

## 关键标准判定（纠正了初稿的错误方向）

初稿曾把「具体子类应改继承到 Administered_Item」当作正解。经原文核对**该方向错误**：

- **ISO/IEC 11179-31:2023 §7.2.2.4.1**："Data_Element is a subclass of Item"
- **§7.3.1 Identified_Item 无 "Direct superclass" 子小节**，且未声明为抽象类

结论：**受管链与内容链在标准中是两条正交链，靠 §7.4.1 关联连接，不靠继承。**
故 `DataElement extends Item` 与 `IdentifiedItem extends Entity` 均是正确建模，
修法是**补实例**，不是改继承。强行把正交链拧成继承会制造新偏差，与
`registry-specification-model-correction`（孤儿行 → 启动挂起 13 分钟）同类。

## 四层根因

1. 导入路径绕过唯一建壳代码（裸 SQL 天然产不出 `administered_item`/`registration_state`）
2. 验收只比对字节与行数，无语义不变式断言——基准库本身导出自同样缺壳的机器
3. 守护测试只守继承边，不守跨表正交链不变式
4. AGENTS.md 实体继承规则单向：防"凭空发明继承"，未防"漏建正交关联链"

## 两个阻断项

- **CHECK 约束是旧值集**：`registration_status` 缺 `CANDIDATE`/`PREFERRED_STANDARD`，
  多出标准中不存在的 `SUBMITTED`/`PREFERRED`。实测 `PREFERRED_STANDARD` 写入 REJECTED。
  根因：反向纠偏脚本 `V20260901001` 因版本号 ≤ `baseline-version=20260918002` 被 Flyway 跳过，
  从未执行；方向错误的 `V20260517001` 却在基线前执行并加了 CHECK。
- **SQLite 64 表 join 上限**：`KNOWN_FAILURES` 中 `status: active` 的已知失败。
  补壳后 11,177 行在个人版全部不可编辑/删除。定点修法：`assertCanModifyItem` 改用
  只取 `registrationAuthority.id` 的投影查询，不加载受管项对象图。

## 生效日期

`reference_document.uri` 指向的 34 份本地 PDF，逐份 `pdftotext -f 1 -l 1` 提取首页：
**全部一致**——`2023-10-07 发布 / 2024-04-01 实施`。
（目录名「强制性行业标准」不副实，文件实为 WS/T 推荐性标准。）

## 交付

- `AdministeredItemClosureConformanceTest`（7 用例，红→绿守护）
- `V20261009010__Align_registration_status_check.sql`（sqlite/sqlserver/dm 三方言）
- `db-baseline/ws363-364-administered-item-{sqlite,sqlserver}.sql`（集合式、幂等、无硬编码行数）
- `scratch/ws-verify/gen_baseline_sql.py`：表清单补三张受管表 + `assert_closure` 前置断言
- `AdministeredItemRepository.findRegistrationAuthorityIdByItemId` 投影查询
- 前端移除注册状态推进流程提示（zh-CN/en-US i18n + spec 同步）
- AGENTS.md 新增「正交链完整性规则（强制）」
- KNOWN_FAILURES 标记 64 表 join 已解决；LEARNING_BACKLOG 记 4 项遗留

## 验证

后端 1499 用例 / 12 失败，**干净 HEAD 对照组同一 4 类同样 12 失败，零新增回归**；
前端 332/332 + build 通过；OpenSpec validate 通过；detect-changes 无 partial/truncated。

## 实施期自查出的 5 个自身缺陷

见 `openspec/changes/repair-administered-item-closure/tasks.md` 末节「实施期发现并修正的自身缺陷」——
其中迁移归位顺序错误与补壳配对顺序错误均已由构造数据实测暴露并修正。

## 真实库状态：**已应用**（用户 2026-10-09 确认）

备份 `scratch/closure-repair/pre-closure-apply-184743.bak`（25 MB）→ 应用 `V20261009010`
→ 补壳（11,184 未闭环 → 0，11,184 行 `PREFERRED_STANDARD` @ `2024-04-01`）→ 幂等重跑无变化
→ 临时实例（端口 8085 / team profile）启动时 Flyway 正式登记 `20261009010`
→ 端到端冒烟 `DE02.01.039.89` 返回 `PREFERRED_STANDARD` + `2024-04-01T00:00:00`
→ 停掉临时实例。补壳前后各留一次全库备份。

## db-baseline/ 签入：用户裁定暂缓

用户明确：`.gitignore` 忽略 `db-baseline/` 是有意为之，当前不需签入，
待本地数据验证完成后再另行要求。已记入 BL-2026-10-09-06（status: ON_HOLD）。
本次已同步 dist → web static 并打包 jar（221 MB）供验证实例使用。

## 待用户裁定

**BL-2026-10-09-06**：`db-baseline/` 被 `.gitignore:20` 整体忽略，`git ls-files` 返回空，
与其 README「本目录被 git 跟踪」的声明不符——本次两个补壳脚本同样不会被提交。
`.gitignore` 注释含「不得签入（需重新核准后再放行）」，未擅自 force-add。

## 复盘要点

上一轮我把「注册状态为 `-`」当���数据事实，设计了「怎么给它补上 Preferred Standard」，
锚定在症状上，从未回头质疑「注册库内容为什么在构造上就没有注册状态」。
用户点出的正是那个没被问出口的问题。**这类"某字段为空"的现象，先问结构是否成立，
再问值怎么填。**