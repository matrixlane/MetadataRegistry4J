package io.github.matrixlane.metadataregistry.web.dto.registration;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 注册状态变更历史条目 DTO。
 *
 * <p>用于 {@link io.github.matrixlane.metadataregistry.web.service.RegistrationLifecycleService#getStateHistory}
 * 返回状态变更历史数据。</p>
 *
 * @param stateId            状态记录 ID
 * @param administeredItemId 管理项 ID
 * @param status             注册状态
 * @param effectiveDate      生效日期
 * @param untilDate          失效日期
 * @param administrativeNote 管理备注
 */
public record RegistrationStateHistoryEntry(
        UUID stateId,
        UUID administeredItemId,
        RegistrationStatus status,
        LocalDateTime effectiveDate,
        LocalDateTime untilDate,
        String administrativeNote
) {
}
