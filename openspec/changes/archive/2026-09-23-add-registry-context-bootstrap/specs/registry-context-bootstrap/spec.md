# registry-context-bootstrap

## Purpose

定义注册系统默认上下文（Registry Context，ISO/IEC 11179-3:2023 附录 C 的 Context1："代表注册系统自身的默认上下文"）的引导创建行为：创建时机、命名空间与归属、标识/版本生成、自指指称与定义、幂等语义、注册规范默认上下文回填以及既有部署的启动补建。

## ADDED Requirements

### Requirement: 创建时机

系统 SHALL 在初始化向导完成（setup 并 commit、系统转入已初始化状态）之后创建 Registry Context，且其创建 MUST 晚于注册机构自有命名空间标识/版本生成策略的配置生效。Registry Context 的创建 MUST 在独立事务中执行：创建失败 MUST NOT 阻断或回滚向导的初始化提交。系统 MUST NOT 在未完成初始化向导的系统启动期创建 Registry Context。

#### Scenario: 向导完成后创建

- WHEN 管理员完成初始化向导（setup 提交且 commit 成功，注册机构、自有命名空间、标识/版本策略、注册规范均已创建）
- THEN 系统创建恰好一个 Registry Context

#### Scenario: setup 阶段未提交前不创建

- WHEN 管理员已提交 setup 但尚未 commit
- THEN 系统中不存在 Registry Context

#### Scenario: 创建失败不阻断初始化提交

- WHEN commit 中 Registry Context 的创建抛异常
- THEN 系统照常完成初始化（initialized 置真、密码生效），Registry Context 保持缺失待补建路径重试

#### Scenario: 未初始化系统启动不创建

- WHEN 全新系统首次启动且尚未运行初始化向导
- THEN 系统中不存在 Registry Context

### Requirement: 命名空间与归属

Registry Context SHALL 作为已注册条目创建，注册归属为初始化向导创建的注册机构；其标识所在命名空间 MUST 为该注册机构的自有命名空间，MUST NOT 使用系统默认（sys:）命名空间或任何其他命名空间。

#### Scenario: 归属与命名空间断言

- WHEN 系统创建 Registry Context
- THEN 该条目的注册机构为向导创建的注册机构，且其 scoped identifier 的命名空间为该注册机构的自有命名空间

### Requirement: 标识与版本随命名空间策略生成

Registry Context 的标识符与版本 MUST 与其所在命名空间的标识/版本生成策略一致：AUTO 类输入模式（AUTO_ONLY / AUTO_WITH_OVERRIDE）下标识符由段模板策略自动生成、版本按版本策略自动生成（如 `CTX.00001` / SEMVER 初版）；仅当输入模式为 MANUAL_ONLY 时，系统 SHALL 使用固定字符串 `registry-context` 作为标识符、`1.0` 作为版本。AUTO 类模式下生成失败 MUST NOT 静默回退到固定字符串。

#### Scenario: AUTO 模式策略生成

- WHEN 注册机构命名空间的标识输入模式为 AUTO_WITH_OVERRIDE（或 AUTO_ONLY）
- THEN Registry Context 的标识符与版本由该命名空间已配置的段模板与版本策略产出（如 `CTX.00001`、`1.0.0`），且生命周期记录为自动生成

#### Scenario: 手动模式固定字符串

- WHEN 注册机构命名空间的标识输入模式为 MANUAL_ONLY
- THEN Registry Context 的标识符为 `registry-context`、版本为 `1.0`，且生命周期记录为手工创建

### Requirement: 自指指称与定义

Registry Context 创建时系统 SHALL 自动填写指称与定义：英文指称 `Registry Context`、中文指称 `注册系统上下文`（均为首选），英文与中文定义各一条（忠实对应 ISO/IEC 11179-3:2023 8.2.2.2 对 context 的描述并体现"注册系统默认上下文"定位）。全部指称与定义的语境 MUST 为 Registry Context 自身（自指），以落实标准 8.5.1.1/8.5.2.1 注"条目是上下文时依然适用"且不造成无限回归。

#### Scenario: 内容与自指断言

- WHEN 系统创建 Registry Context
- THEN 其含中英文指称各一条、中英文定义各一条，且四条记录的语境关联均指向 Registry Context 自身

### Requirement: 幂等语义

Registry Context 的创建 MUST 幂等：同一系统内重复执行初始化向导 setup（含 abort 后重跑）、应用重启、补建入口重复触发，均 MUST NOT 产生第二个 Registry Context，也不得重复分配标识。

#### Scenario: 重复触发不重复创建

- WHEN Registry Context 已存在时再次执行创建逻辑
- THEN 直接复用既有记录，系统中 Registry Context 数量不变

### Requirement: 注册规范默认上下文回填

Registry Context 创建后，系统 SHALL 将注册规范的默认上下文（`Registry_Specification.context`，ISO/IEC 11179-3:2023 Table 28："代表注册系统自身的上下文"）指向该 Registry Context；已指向其他上下文时 MUST NOT 覆盖。

#### Scenario: 新建系统回填

- WHEN 初始化向导 setup 创建注册规范后创建 Registry Context
- THEN 注册规范的默认上下文指向该 Registry Context

#### Scenario: 既有值不覆盖

- WHEN 注册规范的默认上下文已指向某上下文时触发创建
- THEN 保持既有指向不变

### Requirement: 可接受性存储与数据库约束一致

指称/定义及其语境关联、配对上的可接受性（acceptability）SHALL 按 ISO/IEC 11179-3:2023 表 20 以小写存储（preferred/admitted/deprecated/superseded/obsolete）；数据库侧的 CHECK 约束 MUST 与之一致（大小写不敏感归一），MUST NOT 存在仅允许大写取值的 CHECK 约束使合法写入失败。

#### Scenario: 存量库设置可接受性成功

- WHEN 在应用了历史基线的存量数据库中为指称设置可接受性（如 preferred）
- THEN 写入成功，读取时正确解析为对应枚举值

### Requirement: 既有已初始化库启动补建

对已完成初始化但尚无 Registry Context 的既有部署，系统 SHALL 在应用启动时自动补建（含注册规范默认上下文回填，若为空）；补建 MUST NOT 阻止应用启动，失败时记录告警并在下次启动重试。

#### Scenario: 已初始化库升级补建

- WHEN 已初始化系统升级到新版本后首次启动且 Registry Context 缺失
- THEN 系统自动按本能力全部规则补建 Registry Context，注册规范默认上下文为空时一并补挂

#### Scenario: 补建失败不阻断启动

- WHEN 启动补建过程中发生异常（如注册机构缺少自有命名空间）
- THEN 应用正常启动完成，日志记录告警，Registry Context 保持缺失待下次启动重试

### Requirement: 向导内失败降级

初始化向导 setup 中 Registry Context 的创建失败 MUST NOT 阻断向导完成：失败 SHALL 仅记录告警，系统保持可初始化状态，由后续重启的补建路径再次尝试。

#### Scenario: 创建失败向导仍完成

- WHEN 向导 setup 中 Registry Context 创建抛异常
- THEN setup 其余业务数据照常创建，向导可正常 commit

### Requirement: 默认命名规范允许指称含空格

系统的默认命名规范正则规则 SHALL 允许指称符号包含空格，与默认字符集规则保持一致；命名空间级命名约定仍可施加更严格的格式约束。依据：ISO/IEC 11179-3:2023 对指称符号的字符构成无强制限制（6.2.10、3.2.10），格式约束属各命名空间可选的命名约定范畴（3.2.22）。

#### Scenario: 含空格的指称通过默认校验

- WHEN 为条目登记指称 `Registry Context`（含空格）且该命名空间未施加更严格的命名约定
- THEN 默认命名规范校验通过，指称正常保存
