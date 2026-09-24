package io.github.matrixlane.metadataregistry.commonfacilities.registration;

/**
 * SubmissionRecord 来源（应用扩展属性，非 ISO/IEC 11179-3 标准字段）：
 * 区分"工作空间提交产生的 SR"与"编辑保存历史脏数据已迁移标注的 SR"。
 *
 * <p>{@link #USER_SUBMISSION}：工作空间提交（单条 / 批量 / request-candidate）创建的标准 SR。</p>
 * <p>{@link #SYSTEM_DRAFT}：迁移 {@code V20260924002__Submission_record_source.sql} 一次性回填的历史
 * SubmissionRecord（由原"编辑保存生成 SR"行为产生）；保留可追溯，不再产生新实例。</p>
 */
public enum SubmissionRecordSource {
    USER_SUBMISSION,
    SYSTEM_DRAFT
}