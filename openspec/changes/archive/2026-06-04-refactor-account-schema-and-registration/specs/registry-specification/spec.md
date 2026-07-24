## ADDED Requirements

### Requirement: 单注册表初始化与注册管理员联系人生成
在系统初始化单注册表向导中，超级管理员成为注册管理员后，系统仅应当为该超级管理员生成一条 `Registrar` 联系人记录，而不应当自动生成对应的 `Individual` 个人记录。该联系人记录在联系人列表中显示为“注册管理员”角色，关联对应的注册机构（organization）及业务角色，但其 `individual` 字段暂时为空。直到用户在“我的”页面中创建个人资料后，才会生成 `Individual` 记录并关联至该联系人。

#### Scenario: 单注册表初始化完成后的联系人与个人记录状态
- **WHEN** 超级管理员完成单注册表初始化并成为注册管理员，但尚未在“我的”中创建个人资料
- **THEN** 联系人列表中出现且仅出现一条该注册管理员的 `Contact` 记录
- **AND** 该 `Contact` 记录的业务角色为“注册管理员”，且关联对应的注册机构
- **AND** 该 `Contact` 记录的个人信息（`individual` 属性）为空
- **AND** 个人（`Individual`）列表中不会出现与此超级管理员相关的任何记录

### Requirement: 数据库 Schema 统一变更为 mdr
系统所有的数据库表及其数据对象必须统一使用 `mdr` 作为 Schema 名称。

#### Scenario: 数据库表访问验证
- **WHEN** 系统执行任何数据库查询或迁移
- **THEN** 所有的表（如 `account`, `individual`, `system_setting` 等）都在 `mdr` Schema 下进行读取和写入（如 `mdr.account`）
