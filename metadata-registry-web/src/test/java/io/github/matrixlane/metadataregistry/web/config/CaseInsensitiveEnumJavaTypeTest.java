package io.github.matrixlane.metadataregistry.web.config;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ISO/IEC 11179-6:2023 的注册状态取值为首字母大写（Incomplete/…/Standard/…），
 * 与 Java 枚举常量（大写）不一致；历史按标准格式导入的数据必须能读回，
 * 不能抛 No enum constant。
 */
class CaseInsensitiveEnumJavaTypeTest {

    private final CaseInsensitiveEnumJavaType<RegistrationStatus> javaType =
            new CaseInsensitiveEnumJavaType<>(RegistrationStatus.class);

    @Test
    void fromName_readsStandardCapitalizedForm() {
        // 库中按 11179-6 存储的 "Standard" 必须映射为 RegistrationStatus.STANDARD
        assertThat(javaType.fromName("Standard")).isEqualTo(RegistrationStatus.STANDARD);
        assertThat(javaType.fromName("Incomplete")).isEqualTo(RegistrationStatus.INCOMPLETE);
        assertThat(javaType.fromName("Qualified")).isEqualTo(RegistrationStatus.QUALIFIED);
    }

    @Test
    void fromName_keepsExactMatchAndToleratesLowercaseAndWhitespace() {
        assertThat(javaType.fromName("STANDARD")).isEqualTo(RegistrationStatus.STANDARD);
        assertThat(javaType.fromName("standard")).isEqualTo(RegistrationStatus.STANDARD);
        assertThat(javaType.fromName(" Recorded ")).isEqualTo(RegistrationStatus.RECORDED);
    }

    @Test
    void fromName_nullMapsToNull_unknownValueStillFails() {
        assertThat(javaType.fromName(null)).isNull();
        assertThatThrownBy(() -> javaType.fromName("NotAStatus"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromString_isCaseInsensitive() {
        assertThat(javaType.fromString("Superseded")).isEqualTo(RegistrationStatus.SUPERSEDED);
        assertThat(javaType.fromString("PREFERRED_STANDARD")).isEqualTo(RegistrationStatus.PREFERRED_STANDARD);
        assertThat(javaType.fromString(null)).isNull();
    }

    @Test
    void fromString_normalizesStandardWrittenFormWithSpaces() {
        // 11179-3:2023 Table 30 / 11179-6:2023 Table 1 的标准书写格式是 "Preferred Standard"(含空格),
        // 按标准格式导入的历史数据必须能读回 PREFERRED_STANDARD,不抛 No enum constant。
        assertThat(javaType.fromString("Preferred Standard")).isEqualTo(RegistrationStatus.PREFERRED_STANDARD);
        assertThat(javaType.fromString("preferred standard")).isEqualTo(RegistrationStatus.PREFERRED_STANDARD);
        assertThat(javaType.fromString(" preferred  standard ")).isEqualTo(RegistrationStatus.PREFERRED_STANDARD);
        assertThat(javaType.fromName("Preferred Standard")).isEqualTo(RegistrationStatus.PREFERRED_STANDARD);
    }
}
