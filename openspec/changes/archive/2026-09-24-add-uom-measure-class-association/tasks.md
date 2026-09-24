# Tasks: add-uom-measure-class-association

## 1. 后端 DTO 与仓储

- [x] 1.1 新增 `web/dto/unitofmeasure/UnitOfMeasureRequest.java`（sourceUri/designations/definitions/registration/measureClassIds），编译通过
- [x] 1.2 新增 `web/dto/unitofmeasure/UnitOfMeasureResponse.java`（同构字段 + `List<MeasureClassRef> measureClasses`，复用 `web.dto.dimensionality.MeasureClassRef`），编译通过
- [x] 1.3 `UnitOfMeasureRepository` 新增 `long countByMeasureClasses_Id(UUID id)`，编译通过

## 2. 后端服务与控制器（TDD）

- [x] 2.1 先写 `UnitOfMeasureServiceTest` 失败用例：create 带 [mc1,mc2] 成功且响应含 measureClasses；create/update 空集 → 422（VALIDATION_FAILED）；含不存在 id → 404；update 整组替换；update 清空 → 422
- [x] 2.2 实现 `UnitOfMeasureService`：换用专用 DTO，`applyMeasureClasses`（非空校验 + 404 语义 + 整组替换），测试转绿
- [x] 2.3 `UnitOfMeasureController` 换用 `UnitOfMeasureRequest/Response`，编译通过且 2.1 测试保持绿
- [x] 2.4 先写 `MeasureClassServiceTest` 失败用例：被计量单位引用删除 → 409（CONFLICT）且未删除；无引用删除成功
- [x] 2.5 实现 `MeasureClassService.delete` 删除保护（countByMeasureClasses_Id > 0 → 业务错误含引用数），测试转绿

## 3. 前端

- [x] 3.1 `UnitOfMeasureList.vue`：`#fields` 槽加计量类 `SmartItemPicker`（MeasureClass 多选）、新增"计量类数"列、handleCreate/handleEdit 回填 `measureClassIds`（照 DimensionalityList.vue 模式）
- [x] 3.2 检查 `api/resources.ts` 中 unitOfMeasureApi 载荷类型（通用 crud 工厂，类型宽松，无需改）
- [x] 3.3 `npm run build` 通过（13.05s）；vitest 22 文件 215 用例全绿

## 4. 验证

- [x] 4.1 `metadata-registry-web` 全量 `mvn test` 通过（1193 tests, 0 failures, 0 errors）
- [x] 4.2 前端 `vitest run` 全绿 + `npm run build` 通过
- [x] 4.3 重新生成 openapi.json（含 UnitOfMeasureRequest/Response）
- [x] 4.4 scratch 副本库冒烟（`scratch/uom-measure-class-smoke/smoke.sh`，:18084，12 断言全 PASS）：空集/缺省 422、未知计量类 404、创建带 [MC1,MC2]、更新整组替换、更新清空 422、详情/列表携带 measureClasses、被引用计量类删除 409（含引用数）、无引用删除成功。注：带 AdministeredItem 的条目 PUT 在 SQLite 上触发 64 表 join 上限（既有平台限制，与维度冒烟中概念域 PUT 500 同源，`ActiveRegistrarService.assertCanModifyItem` 多态加载），SQL Server 无此限制；冒烟 UoM 以无注册块创建绕开并完整验证更新语义。§7.4.5.3 一致性校验器未改动，由既有单测覆盖
- [x] 4.5 提交前 `detect_changes` 无 partial/truncated（全量 687 符号变更主要来自工作区此前未提交变更，risk critical 由历史变更驱动）
