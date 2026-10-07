package io.github.matrixlane.metadataregistry.web.dto.common;

import java.util.List;
import java.util.UUID;

/**
 * SubmissionRecord 概要（只读列表展示用，align-submission-record-to-iso）：
 * Item 编辑表单的"提交记录"只读面板行项。
 *
 * <p>联系人名 + 组织名为生产依赖，不入 ISO 标准字段。</p>
 */
public record SubmissionRecordSummary(
        UUID id,
        String contactLabel,
        String organizationLabel,
        /** 关联条目数（一个批次可挂多条 AdministeredItem）。 */
        int registeredItemCount,
        /** 关联条目 ID 列表，便于 UI 跳转。 */
        List<UUID> registeredItemIds) {
}
