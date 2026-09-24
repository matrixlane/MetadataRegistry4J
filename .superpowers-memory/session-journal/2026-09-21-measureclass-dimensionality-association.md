# 2026-09-21 计量类→维度关联维护（add-measureclass-dimensionality-association）

## 做了什么

- 需求缘起：用户要求补齐计量类→维度方向关联维护。`dimensionality_measure_class`（11179-31:2023 §7.5.3.1，0..*↔0..*）此前只能从维度页维护（拥有方在 Dimensionality），计量类页无任何入口。
- 走 superpowers-openspec-superpowers-workflow：设计 `docs/superpowers/specs/2026-09-21-measureclass-dimensionality-association-design.md`（方案 A：专用 DTO + 反向调和 + 前端多选/计数列；删除保护对称化与 UoM 反向视图明确排除）→ OpenSpec 变更 `add-measureclass-dimensionality-association`（validate 通过）→ 计划 `docs/superpowers/plans/2026-09-21-measureclass-dimensionality-association.md` → 后端/前端并行 coder 子代理 TDD 实施。
- 实现：
  - 后端 `MeasureClassRequest/Response`（`web.dto.measureclass`）+ `DimensionalityRef`；`MeasureClassService.applyDimensionalities` 差异调和——因 JPA 拥有方在 Dimensionality，从计量类侧写入按"当前已关联（`findByMeasureClasses_Id`）vs 目标集"差异增删各维度 `measureClasses` 并保存，内存双向集合同步；null/空集=清空（0..* 与 UoM 1..* 的关键差异）；create 先 save 取 id 再调和。
  - 前端 `MeasureClassList.vue`：维度 SmartItemPicker 多选 + "维度数"列 + 回填。
- 验证：web 全量 **1198 tests 0 failures**（MeasureClassServiceTest 扩至 7 用例）；vitest 215/215、build 通过；openapi.json 再生成；冒烟 `scratch/mc-dimensionality-smoke/smoke.sh`（:18085）**10/10 PASS 一次通过**（含维度侧视角互通双向校验）；detect_changes 无 partial/truncated。

## 关键发现

- 无新坑。沿用 UoM 变更冒烟经验（纯 ASCII 无空格载荷、无注册块绕开 SQLite 64 表 join 限制、DELETE 业务错误看 body code）后一次通过。
- AGENTS.md 当日改版新增"标准依据表述规则"（UI 解释性文案不得引用条款号）：本次前端注释仅写 `dimensionality_measure_class(0..*)`，合规。

## 状态

- 实现与验证完成，**待 `continue-review` 代码评审 → `continue-archive` 归档**。未提交。
- 与本会话另一变更 `add-uom-measure-class-association`（也待评审归档）相互独立，可分别或一起评审/归档。

## 遗留

- 计量类被维度引用时删除仍静默清理关联（既有行为，如需对称化删除保护另立变更）。
- 计量单位在计量类页的反向视图（方案 C）留作后续。
