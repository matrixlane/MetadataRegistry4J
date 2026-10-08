package io.github.matrixlane.metadataregistry.web.dto.registrationauthority;

import java.util.UUID;

/**
 * 注册机构删除预览：统计级联删除将波及的数据量，供删除确认对话框展示。
 *
 * @param administeredItemCount  该注册机构名下的全部管理项（Administered_Item）数量
 * @param submissionRecordCount  将被一并删除的提交记录数量（含删除关联后无剩余关联的记录）
 * @param registrarCount         该注册机构名下的注册人员数量
 * @param namespaceCount         该注册机构自有的命名空间数量
 * @param stewardshipRecordCount 该注册机构名下的主管记录数量
 * @param transitionRuleCount    该注册机构作用域的注册流程迁移规则数量
 */
public record RegistrationAuthorityDeletionPreview(
        UUID registrationAuthorityId,
        long administeredItemCount,
        long submissionRecordCount,
        long registrarCount,
        long namespaceCount,
        long stewardshipRecordCount,
        long transitionRuleCount) {
}
