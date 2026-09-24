# 实施计划：add-uom-measure-class-association（2026-09-21）

依据：`openspec/changes/add-uom-measure-class-association/{proposal,design,tasks}.md`、`docs/superpowers/specs/2026-09-21-uom-measure-class-association-design.md`。

## 步骤

1. **后端 DTO**（包 `io.github.matrixlane.metadataregistry.web.dto.unitofmeasure`）
   - `UnitOfMeasureRequest(String sourceUri, List<TextLine> designations, List<TextLine> definitions, ItemRegistrationRequest registration, List<UUID> measureClassIds)`，注解风格照 `DimensionalityRequest`。
   - `UnitOfMeasureResponse(UUID id, String label, String sourceUri, List<TextLineResponse> designations, List<TextLineResponse> definitions, ItemMetaResponse meta, List<MeasureClassRef> measureClasses)`，复用 `web.dto.dimensionality.MeasureClassRef`。
2. **TDD**：`UnitOfMeasureServiceTest`（新，Mockito 风格照 `DimensionalityServiceTest`）→ 实现 `UnitOfMeasureService.applyMeasureClasses`（非空→422 `VALIDATION_FAILED`、缺 id→404、整组替换）；Controller 换 DTO。
3. **TDD**：`MeasureClassServiceTest`（新）删除保护用例 → `MeasureClassService.delete` 加 `unitOfMeasureRepository.countByMeasureClasses_Id` 检查（409）；`UnitOfMeasureRepository` 加该方法。
4. **前端**：`UnitOfMeasureList.vue` 照 `DimensionalityList.vue` 模式（SmartItemPicker 多选 + 计数列 + 回填）；检查 `api/resources.ts`。
5. **验证**：`mvn -pl metadata-registry-web test`（或模块内全量）、前端 `vitest run` + `build`、openapi.json 再生成、scratch 冒烟、`detect_changes`。

## 环境要点

- 后端构建：`cd metadata-registry-web && mvn test`（Windows Git Bash）。
- 前端：`cd metadata-registry-ui && npm run build` / `npx vitest run`。
- openapi.json 再生成链路：见 VERIFICATION_BASELINE.md / 既有脚本（实施时确认）。
