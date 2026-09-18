# Tasks: add-individual-identifier

## 1. 数据模型与迁移

- [x] 1.1 `Individual` 新增可空 `identifier`（注释标注应用扩展属性，无 `@StandardReference`）
- [x] 1.2 三份 `V20260917001__Individual_identifier.sql`（sqlite/sqlserver `varchar(255)`，dm `varchar(255)`）

## 2. 后端契约与生成规则

- [x] 2.1 `IndividualRequest`/`IndividualResponse` 增加 identifier 字段
- [x] 2.2 `IndividualService`：applyRequest 留空自动生成（`individual-{hex8}`，私有 `generateIdentifier`），toResponse 透传，list 搜索覆盖 identifier 列
- [x] 2.3 `AuthService.updateMyIndividual`：首次创建/为空时生成 `{username}-individual`；保存后回填账号绑定的空 individual Registrar（不覆盖已有引用）

## 3. 前端

- [x] 3.1 `IndividualList.vue`：identifier 表单项改选填（placeholder"留空自动生成"）；名称渲染 `row.name?.sign` → `row.name`
- [x] 3.2 `RegistrarList.vue`：名称列 `row.identifier` → `row.individualName || row.organizationName || row.identifier`

## 4. 测试与验证

- [x] 4.1 `AuthServiceTest` 新增：首次保存回填绑定 Registrar；已关联不覆盖
- [x] 4.2 `IndividualServiceIntegrationTest` 适配 identifier 透传
- [ ] 4.3 `mvn -pl metadata-registry-web -am test` 全量无回归（人工验收时一并执行）
