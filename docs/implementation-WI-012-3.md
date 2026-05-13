# WI-012-3 实现方案：FHIR 合规检查与 Profile 建议生成

## 1. 目标

实现 FHIR Resource 与本地元数据映射的**差距分析（Gap Analysis）**与 **FHIR Profile 建议生成**功能。

## 2. 子任务

| 子任务 | 输入 | 输出 | 验收标准 |
|--------|------|------|----------|
| 2.1 差距分析服务 | FHIR Resource ID + 可选 Mapping Scheme ID | ComplianceReportResponse | 覆盖率指标正确、已覆盖/未覆盖/本地额外字段清单完整 |
| 2.2 Profile 建议生成 | FHIR Resource ID + 可选现有 Report ID | ProfileSuggestionResponse | 生成有效 StructureDefinition JSON (differential)、标注 DRAFT 状态 |
| 2.3 REST API | HTTP 请求 | JSON 响应 | 三个端点正常工作、Swagger 文档完整 |
| 2.4 单元测试 | - | 测试报告 | 覆盖率 ≥ 80% |

## 3. 设计决策

### 3.1 映射数据载体
- 使用 `fhir_element.administered_item_id` 作为 WI-012-2 映射数据的载体
- 无需新建独立映射表

### 3.2 差距分析规则
- **排除 root element**：`parent != null` 的元素才参与覆盖率计算
- **必填元素**：`min > 0` 视为 required
- **优先级**：required → HIGH, `isSummary=true` → MEDIUM, 其他 → LOW
- **mustSupport 代理**：因 `FhirImportService` 暂未解析 `mustSupport`，当前使用 `isSummary` 作为优先级参考

### 3.3 类型兼容性
- 采用简单字符串映射表 `TYPE_COMPATIBILITY_MAP`（30+ FHIR 类型映射）
- 未知类型视为兼容（不标记为不兼容）
- 本地额外字段定义为"映射到其他 Resource 的本地项"

### 3.4 Profile 生成约束
- 仅输出 **differential** 层面的基础约束
- 不生成完整 snapshot
- 不涉及复杂的 slicing 或 invariant 定义
- 明确标注 **"草稿/需人工审核"** 状态

## 4. API 设计

### POST /api/v1/fhir/compliance/check
```json
{
  "resourceId": "uuid",
  "schemeId": "uuid (optional)"
}
```

### GET /api/v1/fhir/compliance/report/{id}
返回缓存的 ComplianceReport。

### POST /api/v1/fhir/profile/generate
```json
{
  "resourceId": "uuid",
  "existingReportId": "uuid (optional)",
  "includeExtensions": true,
  "mustSupportThreshold": 0.8
}
```

## 5. 新增/修改文件清单

### Entity
- `FhirElement.java` — 添加 `administeredItemId` 字段

### Repository
- `FhirElementRepository.java` — 添加合规查询方法

### Service
- `FhirComplianceService.java` — 差距分析核心逻辑
- `FhirProfileSuggestionService.java` — Profile 草稿生成

### Controller
- `FhirComplianceController.java` — REST API (3 个端点)

### DTO (11 个)
- `ComplianceCheckRequest`, `ComplianceReportResponse`, `CoverageMetrics`
- `ElementCoverage`, `ElementGap`, `LocalExtraField`
- `TypeCompatibilitySummary`, `TypeIncompatibility`
- `ProfileSuggestionRequest`, `ProfileSuggestionResponse`, `ExtensionSuggestion`

### Migration
- `V20260513007__Add_fhir_element_mapping_index.sql`

### Test
- `FhirComplianceServiceTest.java` (8 tests)
- `FhirProfileSuggestionServiceTest.java` (5 tests)
- `FhirComplianceControllerTest.java` (4 tests)

## 6. 测试覆盖率

| 类 | 指令覆盖率 | 分支覆盖率 |
|----|-----------|-----------|
| FhirComplianceService | 86% | 59% |
| FhirProfileSuggestionService | 84% | 50% |
| FhirComplianceController | 100% | n/a |

## 7. 风险与回退

| 风险 | 缓解措施 |
|------|----------|
| `fhir_element` schema 与实体不完全匹配 | 仅使用已确认存在的字段，避免 JPA 自动 DDL |
| `Map.of()` Java 9 限制（10 entries） | 改用 static block + `Collections.unmodifiableMap` |
| `Designation.getName()` 不存在 | 改用 `getSign().getValue()` |
| 项目预编译错误阻塞测试 | pom.xml 中排除已知损坏文件，不影响新代码 |
