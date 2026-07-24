## 1. 联邦节点与链接模型

- [x] 1.1 新增 `registry_node`、`item_link`、`sync_watermark` 表（双方言迁移脚本）
- [x] 1.2 实现 RegistryNode 管理服务与控制器（登记/列表/删除，凭据加密存储）
- [x] 1.3 定义全局逻辑身份模型（来源 RAI + 来源标识符）与 ItemLink 实体

## 2. 合并包与增量计算

- [x] 2.1 定义 federation bundle v1 格式（manifest.json + items/*.json + receipt-request.json）
- [x] 2.2 实现增量计算：基于 `sync_watermark` 与 `version_history` 生成变更集
- [x] 2.3 实现合并包导出（复用 MetadataExportService 的条目快照并扩展来源身份字段）

## 3. 合并请求生命周期与文件通道

- [x] 3.1 新增 `merge_request`、`merge_request_item` 表（双方言迁移脚本）
- [x] 3.2 实现合并请求状态机（DRAFT → SUBMITTED → UNDER_REVIEW → MERGED/REJECTED/FAILED）
- [x] 3.3 实现文件包导出（源端）与导入（目标端生成合并请求）

## 4. 评审工作台与并入执行

- [~] 4.1 接入既有审批流：合并请求到达即创建 ApprovalProcessInstance（默认单级模板）（v1 采用 MR 自带评审流，审批模板集成留待后续）
- [x] 4.2 实现评审工作台后端：条目分类（ADD/UPDATE/CONFLICT/MATCH_CANDIDATE）、逐项裁决接口、匹配候选确认
- [x] 4.3 实现并入执行器：单事务写入、MERGE 版本历史、链接建立、水位线更新、失败整体回滚
- [x] 4.4 评审工作台前端页面（复用匹配确认 UI 模式）

## 5. HTTP 推送通道与回执

- [x] 5.1 实现目标端接收端点 `/api/v1/federation/merge-requests`（预共享令牌校验）
- [x] 5.2 实现合并回执生成与投递（API 回调与回执文件导入两种方式）
- [x] 5.3 实现源端水位线与反向链接更新（凭回执）及重放幂等处理

## 6. 端到端验证

- [x] 6.1 PERSONAL → TEAM 全流程：个人版造数据 → 提交合并请求 → 团队版评审 → 并入 → 二次增量提交
- [~] 6.2 冲突场景：双侧变更 → 冲突标记 → 评审裁决（采用传入/保留现有）（三方合并基快照已建模，冲突自动判定为简化版，评审裁决可用）
- [x] 6.3 离线文件通道：导出文件包 → 目标端导入 → 等价合并请求 → 并入
- [x] 6.4 回执丢失重放：重复提交同一合并包验证幂等跳过
