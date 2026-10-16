package io.github.matrixlane.metadataregistry.web.dto.common;

import java.util.UUID;

/**
 * SubmissionRecord 概要（只读列表展示用，align-submission-record-to-iso）：
 * Item 编辑表单的"提交记录"只读面板行项。
 *
 * <p>联系人名 + 组织名为生产依赖，不入 ISO 标准字段。</p>
 *
 * <p><b>只返回关联条目数，不返回条目 ID 列表</b>（2026-10-09）：本 DTO 出现在**单条目**
 * 的响应里，接收方关心的只是"这条提交记录覆盖了多少条目"。而一条提交记录可关联
 * 全部条目（WS 363/364 基准数据补齐后为 11,182 条），一旦把 ID 列表内联进来，
 * 一页 20 条的响应体即膨胀到 8.7 MB——前端从未渲染该字段（全库无 .vue 引用），
 * 纯为无效负载。需要跳转时按提交记录 id 另行查询即可。</p>
 */
public record SubmissionRecordSummary(
        UUID id,
        String contactLabel,
        String organizationLabel,
        /** 关联条目数（一个批次可挂多条 AdministeredItem）。 */
        long registeredItemCount) {
}