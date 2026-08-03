> **状态：已立项，未启动。** 本变更与 `2026-08-02-governance-init-all-editions` 由同一份需求拆分而来，待第一个变更完成后再开展；开展前需补充 design、tasks 与 spec delta。

## Why

个人或团队创建的注册机构及其数据标准，今后需要与其他注册表（其他个人的、或其他团队的）进行合并。现有 `registry-federation` 与 `merge-request` 能力解决了条目级的增量合并与身份匹配，但未定义注册表层面（注册机构、注册规范、命名空间）的合并策略：两个注册表合并时，各自的注册机构、注册规范、命名空间如何归并，标识符如何处理，目前没有任何规约。

## What Changes（初步方向，启动时细化）

- **合并依据**：注册表合并主要以注册机构标识（Registration_Authority_Identifier）一致来进行合并。
- **合并策略与 Git 对齐**：合并基、三方比对、冲突裁决等语义设计与 Git 的策略保持一致（与 `merge-request` 既有三方比对语义衔接）。
- **治理对象唯一化**：合并后，注册机构、注册规范、命名空间只会存在一个。
- **标识符处理**：合并时可以选择每一个 Scoped_Identifier 是否需要重新生成。

## Capabilities

### Modified Capabilities（预估，启动时确认）

- `registry-federation`：注册表级合并的节点与身份规则。
- `merge-request`：合并策略、治理对象归并与 Scoped_Identifier 重新生成选项。

## Impact

待启动时评估。预期涉及：合并评审流程、治理对象（注册机构/注册规范/命名空间）的唯一化归并逻辑、Scoped_Identifier 重新生成策略，以及与既有 `item_link` 合并基机制的衔接。
