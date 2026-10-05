# Proposal: authority-contact-governance

## Why

当前注册机构（Registration_Authority）的创建/删除仅由 SINGLE/MULTI 运行模式粗粒度控制：个人版被锁定为单一机构且完全禁删，团队版 SINGLE 模式下同样禁删；机构删除无任何引用清理，删后遗留大量悬挂数据；初始化流程先建机构后建注册规范，与"注册规范描述注册系统本身、独立于注册机构"的 ISO/IEC 11179-3:2023 §9.4.10 语义不符；提交记录的组织不可设置、联系人可选任意身份，缺少"提交人必须是所选机构联系人"的前置约束。本次变更按部署版本（个人版/团队版）建立机构—身份—提交的完整治理规则。

## What Changes

- **个人版**：初始化创建的注册机构获得后台隐式 Primary 标志（不进前端、不进 DTO），不可删除；Admin 作为其注册人员的联系人身份不可删除。
- **个人版**：Admin 可新建注册机构（取消"仅允许一个"限制）；新建时自动成为该机构注册人员；该身份不可删除，除非机构被删除。
- **团队版**：任何用户可删除其全部联系人身份；非 Admin 用户不能为自己新增注册人员身份；Admin 可新增、编辑、删除注册机构，且可删除所有机构（含 Primary——团队版下 Primary 标志暂不发挥作用）。
- **机构级联删除**：删除注册机构时级联删除其下全部 Administered_Item 及关联数据（提交记录、主管记录、注册人员、命名空间、流转规则等），前端删除前弹出含计数与不可恢复提示的警告框（新增删除预览端点）。
- **初始化顺序**：注册规范先于注册机构与注册人员创建；向导步骤同步前移；注册规范语义明确为系统全局（每注册系统一条）。
- **提交约束**：提交组织可设置（选项 = 本人具有身份的组织）；提交联系人恒为提交用户本人在所选机构的身份；非所选机构联系人时后端拒绝提交，前端在提交入口弹提示引导先创建联系人身份。

## Capabilities

### New Capabilities

- `registration-authority-lifecycle`: 注册机构的创建/删除治理规则——Primary 隐式标志、按部署版本的删除保护、机构级联删除与删除预览。

### Modified Capabilities

- `deployment-edition`: 个人版"仅允许一个注册机构/隐藏新增入口"约束解除，改为"Primary 不可删、允许新增"。
- `system-initialization`: 治理对象创建顺序调整为注册规范 → 注册机构 → 注册人员 → 命名空间；初始化创建的注册机构置 Primary 标志。
- `registry-specification`: 向导中注册规范步骤位置前移（注册机构之前）；SINGLE 模式禁止机构增删两条需求迁移至 `registration-authority-lifecycle` 并按版本重写。
- `contact-identity-management`: 注册人员身份新增仅限超级管理员；身份删除规则按部署版本差异化（个人版保护注册人员身份，团队版放开全部自删）。
- `submission-record`: 提交组织可选且提交联系人必须是本人在所选机构的身份；无身份时前端提示、后端拒绝。

## Impact

- **后端**：`RegistrationAuthority` 实体新增 `is_primary` 列（sqlite/sqlserver/dm 三分支迁移 V20261005001）；`RegistrationAuthorityService`/`Controller`（版本化创建/删除、删除预览端点、自动注册人员）；新增机构级联删除服务；`ContactService.createMyContact/deleteMyContact` 版本分支；`WorkspaceService`/`ItemAdministrationService` 提交校验；`InitWizardService` 创建顺序调整。
- **前端**：`RegistrationAuthorityList.vue`（个人版新增入口+删除警告）、`SubmissionRecordPicker.vue`（组织下拉+联系人自动解析）、`WorkspaceView.vue`（提交入口预检提示）、`MyProfile.vue`（身份增删按版本/角色门控）、`InitWizard.vue`（步骤前移）、i18n 与 e2e。
- **数据**：既有已初始化库的 Primary 标志回填策略（由初始化流程与启动调和保证；详见 design.md）。
- **API**：新增 `GET /api/v1/registration-authorities/{id}/deletion-preview`；`POST /registration-authorities` 语义变更（个人版自动建注册人员）；`DELETE /registration-authorities/{id}` 语义变更（级联删除）。
