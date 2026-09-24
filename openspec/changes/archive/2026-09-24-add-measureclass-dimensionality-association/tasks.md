# Tasks: add-measureclass-dimensionality-association

## 1. 后端 DTO 与仓储

- [x] 1.1 新增 `web.dto.dimensionality.DimensionalityRef`（UUID id + String label，风格照 `MeasureClassRef`），编译通过
- [x] 1.2 新增 `web.dto.measureclass.MeasureClassRequest`（sourceUri/designations/definitions/registration/dimensionalityIds），编译通过
- [x] 1.3 新增 `web.dto.measureclass.MeasureClassResponse`（同构字段 + `List<DimensionalityRef> dimensionalities`），编译通过
- [x] 1.4 `DimensionalityRepository` 新增 `List<Dimensionality> findByMeasureClasses_Id(UUID id)`，编译通过

## 2. 后端服务与控制器（TDD）

- [x] 2.1 扩展 `MeasureClassServiceTest`（先写失败用例）：create 带 [d1,d2] → 响应含 dimensionalities 且各维度 measureClasses 含本计量类；create/update 空集 → 清空（维度侧同步移除）；含不存在 id → 404；update 整组替换；内存双向集合同步
- [x] 2.2 实现 `MeasureClassService`：换专用 DTO；`applyDimensionalities` 差异调和（拥有方在 Dimensionality：增删各维度 measureClasses、同步 entity.dimensionalities、保存变更维度）；create 先 save 取 id 再调和；测试转绿
- [x] 2.3 `MeasureClassController` 换用 `MeasureClassRequest/Response`，编译通过且测试保持绿

## 3. 前端

- [x] 3.1 `MeasureClassList.vue`：`#fields` 槽加"维度"SmartItemPicker 多选（`item-types=['Dimensionality']`）、新增"维度数"列、handleCreate/handleEdit 回填 `dimensionalityIds`（照 UnitOfMeasureList.vue 模式）
- [x] 3.2 `npm run build` 通过（vue-tsc + vite 无错误）；`npx vitest run` 22 文件 215 用例全绿

## 4. 验证

- [x] 4.1 `metadata-registry-web` 全量 `mvn test` 通过（1198 tests, 0 failures, 0 errors；MeasureClassServiceTest 7 用例）
- [x] 4.2 前端 build + vitest 通过
- [x] 4.3 openapi.json 再生成（含 MeasureClassRequest/Response 与 dimensionalityIds）
- [x] 4.4 scratch 副本库冒烟（`scratch/mc-dimensionality-smoke/smoke.sh`，:18085，10 断言全 PASS）：创建空集/未知 id 404/创建带 [D1,D2]/维度侧视角互通（D1.measureClasses 含 MC1）/更新整组替换（D1 视角同步移除）/更新清空（D2 视角同步移除）/详情列表携带 dimensionalities。计量类无注册块创建以绕开 SQLite 64 表 join 既有平台限制（见 UoM 变更 journal）
- [x] 4.5 提交前 `detect_changes` 无 partial/truncated（全量 784 符号变更主要来自工作区历史未提交变更）
