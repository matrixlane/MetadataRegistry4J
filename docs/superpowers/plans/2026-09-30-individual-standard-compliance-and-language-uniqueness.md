# 实施计划：2026-09-30 双变更（移除 Individual 非标准字段 + 语言标识唯一性）

依据：`openspec/changes/remove-individual-nonstandard-fields/tasks.md`（用户已确认设计）+ `openspec/changes/enforce-language-identification-uniqueness/tasks.md`（用户指令"继续下去"）。

## Stream A：remove-individual-nonstandard-fields

### A1. TDD 红——测试契约改写（先改测试看红）

1. `AuthServiceAvatarTest`：改为 Account 语义——mock `Account` 带 avatar；断言 `userRepository.save` 后账号 avatar 更新；新增"无 Individual 上传头像不创建 Individual"用例（`verify(individualRepository, never()).save(...)`）；头像删除/校验用例同步
2. `AuthServiceTest`：删 identifier 相关行（约 :248 `existing.setIdentifier("admin")` 及 366 附近的生成/回填断言）
3. `IndividualServiceIntegrationTest`：删 :115 `identifier()` 断言

验证：`./mvnw -pl metadata-registry-web -am test -Dtest='AuthServiceAvatarTest+AuthServiceTest+IndividualServiceIntegrationTest'` 编译失败（红）。

### A2. 实体与迁移

- `Individual` 删 `identifier`、`avatar`；`Account` 加 `@Lob @Column(name="avatar") String avatar`
- 三方言 `V20260930001__Remove_individual_nonstandard_fields.sql`：
  - sqlite：`alter table account add column avatar text;` → `update account set avatar=(select avatar from individual where individual.id=account.individual_id) where individual_id is not null;` → 两个裸 `drop column`（不带 IF EXISTS）
  - sqlserver：`ext.account` 加 `NVARCHAR(MAX)`（COL_LENGTH 守卫）→ 关联子查询搬迁（mdr.individual / ext.account 全限定）→ 两个 `DROP COLUMN`（COL_LENGTH 守卫）
  - dm：仿 sqlserver（DM8 字典视图适配）
- scratch 副本库实测（带 avatar 账号 + 孤儿 individual）

### A3. 服务/API 转绿

- `IndividualService`：搜索谓词/applyRequest/generateIdentifier/toResponse 去 identifier
- `AuthService`：updateMyIndividual 删 setIdentifier×3；头像三方法读写 `Account.avatar`（save Account）；删无 Individual 创建逻辑；:467 删
- `RegistrarService`：删 `individual.setIdentifier(...)`（约 :174）
- DTO 删字段；`AuthController` 端点 `/auth/me/avatar`；openapi 再生成

### A4. 前端

- `IndividualList.vue` 删标识符列/表单项；`MyProfile.vue` 3 处端点改 `/auth/me/avatar`

### A5. 验证

mvn 全量、vitest、build、冒烟、openspec validate、detect-changes、SQL Server 真实库应用；归档 + 记忆更新。

## Stream B：enforce-language-identification-uniqueness

### B1. 迁移脚本核对
三份 `V20260926001` 已存在（前会话遗留），逐字核对与 design/proposal 一致性（dedup 保留 MIN(id)、9 张 FK 表重定向、UNIQUE INDEX 名），不符则修。

### B2. TDD 红
- `LanguageInitializerTest`：`upsertLanguage` 0 行→创建 / 1 行→跳过 / 多行→保留 MIN(id)+WARN 不阻塞
- `LanguageIdentificationRepositoryTest`：`findAllByLanguageIdentifier` 多行场景

### B3. 绿
- `LanguageIdentificationRepository` 加 `findAllByLanguageIdentifier`
- `LanguageInitializer.upsertLanguage` 重写：List 分支处理

### B4. 收尾
KNOWN_FAILURES 新条目（mdr 自然键缺 UNIQUE 模式）、session journal、全量验证（dm 待实测按惯例记录）。

## 执行顺序

A1→A2→A3→A4（一条线串行）→ B1→B2→B3→B4 → 统一 A5 验证（含 detect-changes、归档两变更、记忆更新）。两 Stream 文件不相交；mvn 全量验证放最后统一跑。
