package io.github.matrixlane.metadataregistry.commonfacilities.registration;

/**
 * ISO/IEC 11179-6:2023 标准注册状态枚举。
 *
 * <p>标准生命周期：</p>
 * <pre>
 * Incomplete → Submitted → Recorded → Qualified → Standard → Preferred
 *                                     ↓            ↓            ↓
 *                                   Retired     Superseded   Retired
 *                                      ↓            ↓
 *                                    Historical  Historical
 * </pre>
 *
 * <p>WI-019 变更：移除非标准状态 {@code CANDIDATE} 和 {@code APPLICATION}，
 * 新增标准状态 {@code SUBMITTED}，{@code PREFERRED_STANDARD} 重命名为 {@code PREFERRED}。</p>
 */
public enum RegistrationStatus {
    INCOMPLETE,
    SUBMITTED,
    RECORDED,
    QUALIFIED,
    STANDARD,
    PREFERRED,
    SUPERSEDED,
    RETIRED,
    HISTORICAL
}
