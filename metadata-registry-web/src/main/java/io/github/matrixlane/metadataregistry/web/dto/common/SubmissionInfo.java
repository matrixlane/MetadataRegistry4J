package io.github.matrixlane.metadataregistry.web.dto.common;

import java.util.UUID;

/**
 * 提交信息（SubmissionRecord 内容，add-submitter-contact-identity）：
 * 条目保存时生成 Submission_Record 的联系人与所属组织。两者均可缺省——
 * 缺省时服务端以提交者的默认联系人及其所属组织预填。
 */
public record SubmissionInfo(UUID contactId, UUID organizationId) {
}
