package io.github.matrixlane.metadataregistry.commonfacilities.registration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 管理状态（administrative status）枚举。
 *
 * <p>ISO/IEC 11179-3:2023 表 22 规定 {@code administrative_status} 为 0..1 String，
 * 取值由注册机构自行决定。本枚举收录 ISO/IEC 11179-6:2023 附录 D.3 Table D.1 示例词表
 * 及注册机构扩充值，共 16 个常量；落库与 JSON 序列化均使用标准原文小写串。
 */
public enum AdministrativeStatus {

    DRAFT("draft"),
    INITIAL("initial"),
    PROVISIONALLY_CANDIDATE("provisionally candidate"),
    PROVISIONALLY_RECORDED("provisionally recorded"),
    APPROVED_AS_RECORDED("approved as recorded"),
    PROVISIONALLY_QUALIFIED("provisionally qualified"),
    APPROVED_AS_QUALIFIED("approved as qualified"),
    PROVISIONALLY_STANDARD("provisionally standard"),
    APPROVED_AS_STANDARD("approved as standard"),
    PROVISIONALLY_PREFERRED_STANDARD("provisionally preferred standard"),
    APPROVED_AS_PREFERRED_STANDARD("approved as preferred standard"),
    PROVISIONALLY_SUPERSEDED("provisionally superseded"),
    APPROVED_AS_SUPERSEDED("approved as superseded"),
    PROVISIONALLY_RETIRED("provisionally retired"),
    WITHDRAWN("withdrawn"),
    REJECTED("rejected");

    private final String standardValue;

    AdministrativeStatus(String standardValue) {
        this.standardValue = standardValue;
    }

    /**
     * 标准取值（小写原文），落库与 JSON 均使用该值。
     */
    @JsonValue
    public String standardValue() {
        return standardValue;
    }

    /**
     * 按标准值解析（大小写不敏感，trim 容忍）。
     *
     * @param value 标准小写值（如 "provisionally qualified"）或标准 Title Case 书写形式
     * @return 对应枚举；空值返回 null
     * @throws IllegalArgumentException 无法识别时
     */
    @JsonCreator
    public static AdministrativeStatus fromStandardValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        for (AdministrativeStatus status : values()) {
            if (status.standardValue.equalsIgnoreCase(v)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown administrative status: " + value);
    }
}
