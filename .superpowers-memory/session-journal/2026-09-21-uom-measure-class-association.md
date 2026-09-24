# 2026-09-21 计量单位—计量类关联补齐（add-uom-measure-class-association）

## 做了什么

- 需求缘起：符合性审计发现 ISO/IEC 11179-31:2023 §7.5.3.2 `unit_of_measure_class` 要求 UoM 侧 1..\*（每个计量单位至少归属一个计量类），但实现为 0..\* 且关联无任何写入口（UoM API 只有通用 ConceptRequest），导致 §7.4.5.3 维度一致性校验永远走"不校验"旁路。标准原文经本地 PDF（`ISO/IEC 11179 标准原文目录\ISO_IEC 11179-31-2023.pdf` 第 54-59 页）逐条核实。维度↔计量类（§7.5.3.1）经核实已存在且符合，未重复实现。
- 走 superpowers-openspec-superpowers-workflow：设计 `docs/superpowers/specs/2026-09-21-uom-measure-class-association-design.md`（方案 A：只补 UoM 侧，零 schema 变更）→ OpenSpec 变更 `add-uom-measure-class-association`（proposal/design/specs/tasks，validate 通过）→ 计划 `docs/superpowers/plans/2026-09-21-uom-measure-class-association.md` → TDD 实施（后端/前端两个并行 coder 子代理）。
- 实现：
  - 后端新 DTO `UnitOfMeasureRequest/Response`（`web.dto.unitofmeasure`，复用 `MeasureClassRef`）；`UnitOfMeasureService.applyMeasureClasses`：null/空 → 422 VALIDATION_FAILED（"计量单位必须至少归属一个计量类"）、缺 id → 404、整组替换；`UnitOfMeasureRepository.countByMeasureClasses_Id`；`MeasureClassService.delete` 删除保护（被引用 → 409 含引用数）。
  - 前端 `UnitOfMeasureList.vue`：计量类 SmartItemPicker 多选 + "计量类数"列 + 编辑回填（照 DimensionalityList.vue 模式）；`api/resources.ts` 通用 crud 工厂无需改。
- 验证：web 全量 **1193 tests 0 failures**（新增 UnitOfMeasureServiceTest 7 + MeasureClassServiceTest 2）；vitest 22 文件 215 用例全绿；前端 build 通过；openapi.json 再生成（含 UnitOfMeasureRequest）；冒烟 `scratch/uom-measure-class-smoke/smoke.sh`（副本库 :18084）**12 断言全 PASS**；detect_changes 无 partial/truncated。

## 关键发现

1. **SQLite 64 表 join 上限影响面比既有记录更广**：不只 transitions/execute 和概念域 PUT——任何**带 AdministeredItem 的条目**（create 带 registration 即产生）在 update/delete 时经 `NamespacePermissionAspect` → `ActiveRegistrarService.assertCanModifyItem` → `administeredItemRepository.findByItem_Id` 触发 Item 根多态加载（~120 表 join），SQLite 直接 500。无 AdministeredItem 的条目不受影响（解释了维度冒烟 D2 PUT 为何通过）。**建议录入 KNOWN_FAILURES**：`assertCanModifyItem` 的 Item 多态加载需改写（如按 administered_item 表直查 + 仅取 registration_authority_id）才能在 SQLite 支持带注册条目的编辑。
2. 冒烟环境两坑（此前 journal 已有同类记录）：命名规范正则拒绝带空格/中文的 designation 值（Git Bash 中文 JSON 会坏 UTF-8，载荷须纯 ASCII 且无空格）；mvn spring-boot:run 的 forked JVM 在脚本 kill 后可能孤儿占端口。
3. 冒烟 DELETE 返回业务错误时 HTTP 状态恒为 400，业务码在 body（如删除保护 409 体现为 HTTP 400 + code 409）——断言须看 body code。

## 状态

- 实现与验证完成，**待 `continue-review` 代码评审 → `continue-archive` 归档**。未提交（工作区仍有大量历史未提交变更，提交需按变更分组）。

## 遗留

- 计量类页面反向只读视图（已关联维度/计量单位）——后续变更（方案 B）。
- 存量无关联计量单位的数据巡检/回填——不追溯，写路径强制即可。
