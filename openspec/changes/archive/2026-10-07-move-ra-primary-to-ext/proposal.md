# Proposal: move-ra-primary-to-ext

## Why

`authority-contact-governance` 将隐式 Primary 标志实现为 `mdr.registration_authority.is_primary` 列，违反项目分层原则：**mdr schema 只装 ISO/IEC 11179 标准列，应用扩展字段一律 ext schema 卫星表**（判例：ext.account.avatar、ext_language_identification、ext.contact_account_binding）。需将 is_primary 迁出至 `ext.registration_authority` 卫星表（1:1 按 id），并补写三方言数据迁移。

## What Changes

- 新增扩展实体 `RegistrationAuthorityExtension`（metadata-registry-ext，id + isPrimary；orm-team.xml 登记 schema="ext"；orm-sqlite.xml 登记物理名 ext_registration_authority）。
- 迁移 `V20261008001__Move_ra_primary_to_ext`（sqlite/sqlserver/dm 同号）：建 ext 卫星表 → 从 mdr.registration_authority 拷贝 is_primary → 删 mdr 列（SQLite DROP COLUMN 可用；SQL Server/DM 按守卫惯例）。
- `RegistrationAuthority` 实体移除 isPrimary 字段；全部读写点改经 Extension 仓储：向导置位、启动回填、删除保护（deletable 计算/Primary 拒删）、级联删除补删卫星行、相关测试。

## Capabilities

### Modified Capabilities

- `registration-authority-lifecycle`: Primary 标志的存储位置调整为 ext schema 卫星表（行为不变）。

## Impact

- 实体/迁移/orm 映射三方言；InitWizardService、RegistryContextBootstrapService、RegistrationAuthorityService、RegistrationAuthorityDeletionService 及对应测试。
- AGENTS.md 已新增"数据模型分层规则"强制条款（本次教训固化）。
