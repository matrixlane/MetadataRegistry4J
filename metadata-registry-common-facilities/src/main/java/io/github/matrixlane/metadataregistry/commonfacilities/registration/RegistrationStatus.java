package io.github.matrixlane.metadataregistry.commonfacilities.registration;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.StandardReference;

/**
 * ISO/IEC 11179 标准注册状态枚举。
 *
 * <p>取值与 ISO/IEC 11179-3:2023 Table 30(9.6.2)及 ISO/IEC 11179-6:2023 Table 1(4.3.2)
 * 完全一致:Incomplete、Candidate、Recorded、Qualified、Standard、Preferred Standard、
 * Superseded、Retired、Historical、Application。其中 {@link #PREFERRED_STANDARD} 对应标准
 * 状态名 "Preferred Standard";标准中不存在 "Submitted" 状态。</p>
 *
 * <p>标准生命周期(11179-6:2023, 4.3.2/4.3.3):</p>
 * <pre>
 * Incomplete → Candidate → Recorded → Qualified → Standard → Preferred Standard / Application
 *                                       ↓            ↓            ↓
 *                                     Retired     Superseded   Retired / Application
 *                                        ↓            ↓
 *                                      Historical  Historical
 * </pre>
 */
@StandardReference("ISO/IEC 11179-3:2023, 9.6.2, Table 30; ISO/IEC 11179-6:2023, 4.3.2, Table 1")
public enum RegistrationStatus {
    INCOMPLETE,
    CANDIDATE,
    RECORDED,
    QUALIFIED,
    STANDARD,
    PREFERRED_STANDARD,
    SUPERSEDED,
    RETIRED,
    HISTORICAL,
    APPLICATION
}
