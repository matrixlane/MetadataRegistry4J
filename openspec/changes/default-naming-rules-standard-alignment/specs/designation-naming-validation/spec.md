# designation-naming-validation Specification

## Purpose
指称（Designation value）保存时的命名校验语义：默认仅施加长度约束；字符构成约束是命名空间级 Naming_Convention 约定的可选项，显式启用才对关联指称生效。对齐 ISO/IEC 11179-3:2023——标准对 Sign 字符构成无强制限制，约定才约束。

## ADDED Requirements

### Requirement: 默认命名校验仅约束长度

系统 SHALL 对每条保存的指称施加默认命名校验，默认规则集 MUST 仅包含长度规则（1–255 字符）。默认规则集 MUST NOT 包含任何字符构成类限制（字符集白名单、字符构成正则）。指称除为空或超过 255 字符外，MUST NOT 因字符构成被拒绝。

#### Scenario: 标准特殊字符指称原样注册

- **WHEN** 用户经 UI 或 MCP `draft-item` 提交指称 `医嘱(处方)开立医师姓名`（含半角括号）
- **THEN** 系统 SHALL 接受该指称，SHALL NOT 报"非法字符"类校验错误

#### Scenario: 全角括号与斜杠指称原样注册

- **WHEN** 用户提交指称 `主任（副主任）医师姓名` 或 `患者/法定代理人姓名`
- **THEN** 系统 SHALL 接受该指称

#### Scenario: 罗马数字指称原样注册

- **WHEN** 用户提交指称 `Ⅰ助姓名` 或 `Ⅱ期`
- **THEN** 系统 SHALL 接受该指称

#### Scenario: 单位符号与希腊字母指称原样注册

- **WHEN** 用户提交指称 `尿铅检测值(ptmol/L)值域`、`舒张压（mmHg）值域` 或 `骶耻外径＜18cm`
- **THEN** 系统 SHALL 接受该指称

#### Scenario: 空指称仍被拒绝

- **WHEN** 用户提交空字符串或全空白指称
- **THEN** 系统 SHALL 拒绝（长度规则下限），返回校验错误

#### Scenario: 超长指称仍被拒绝

- **WHEN** 用户提交长度超过 255 字符的指称
- **THEN** 系统 SHALL 拒绝（长度规则上限），返回校验错误

### Requirement: 字符构成约束为命名空间约定的可选项

字符集规则 MUST 保留为实现（`CharsetNamingConventionRule`），但 MUST NOT 出现在默认规则集。字符构成约束仅当命名空间启用强制命名规范（`mandatoryNamingConventionIndicator=true`）且所关联 Naming_Convention 配置了机器规则（`rulesJson` 含 `type=charset`）时，才对该命名空间下指称执行校验。引擎 SHALL 复用 `validate(sign, convention)` 约定路径执行该校验。

#### Scenario: 启用强制约定且配置 charset 规则后特殊字符被拒

- **GIVEN** 命名空间启用强制命名规范，其 Naming_Convention 的 rulesJson 配置了 `type=charset`（如仅允许字母数字与汉字）
- **WHEN** 用户在该命名空间下提交指称 `舒张压（mmHg）值域`
- **THEN** 系统 SHALL 按约定拒绝并报 charset 校验错误

#### Scenario: 启用强制约定但未配置机器规则时不受字符限制

- **GIVEN** 命名空间启用强制命名规范，但其 Naming_Convention 未配置 rulesJson 机器规则
- **WHEN** 用户提交指称 `患者/法定代理人姓名`
- **THEN** 系统 SHALL 接受该指称（既有"必须关联约定"守卫仍生效，字符构成不做限制）

#### Scenario: 未启用强制约定的命名空间不受字符限制

- **GIVEN** 命名空间未启用强制命名规范
- **WHEN** 用户提交含任意标准符号的指称
- **THEN** 系统 SHALL 接受，仅执行默认长度校验

### Requirement: 强制命名规范命名空间的既有守卫不退化

命名空间启用强制命名规范时，既有守卫 MUST 保持：命名空间必须至少关联一个 Naming_Convention；该空间下指称必须关联命名规范。缺失时写入 MUST 仍被拒绝（422）。

#### Scenario: 强制命名空间缺少约定配置仍被拒

- **GIVEN** 命名空间启用强制命名规范但未关联任何 Naming_Convention
- **WHEN** 用户在该空间下创建含指称的条目
- **THEN** 系统 SHALL 拒绝并报"必须至少关联一个 NamingConvention"

#### Scenario: 强制命名空间指称未挂约定仍被拒

- **GIVEN** 命名空间启用强制命名规范且已关联 Naming_Convention
- **WHEN** 用户提交的指称未关联任何命名规范
- **THEN** 系统 SHALL 拒绝并报指称必须关联命名规范

### Requirement: 治理就绪评分与校验语义一致

数据治理就绪评分 MUST NOT 以硬编码字符白名单作为指称合规的判据；评分 SHALL 基于命名空间命名约定合规性（是否启用约定、指称是否按约定关联/满足所配规则）评估。

#### Scenario: 就绪评分不因标准符号指称扣分

- **GIVEN** 某命名空间未启用强制命名规范
- **WHEN** 系统计算该空间的治理就绪评分，空间内存在指称 `Ⅰ助姓名`
- **THEN** 评分 MUST NOT 因该指称的字符构成扣分

#### Scenario: 违反已启用 charset 约定的指称在评分中体现

- **GIVEN** 命名空间启用强制命名规范且配置了 charset 机器规则
- **WHEN** 空间内指称 `舒张压（mmHg）值域` 违反该约定
- **THEN** 就绪评分 SHALL 体现该指称未满足命名约定
