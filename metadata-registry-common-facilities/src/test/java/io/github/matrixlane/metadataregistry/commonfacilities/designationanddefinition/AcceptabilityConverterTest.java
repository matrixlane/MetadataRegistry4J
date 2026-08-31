package io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ISO/IEC 11179-3:2023 表 20（8.7.1.2）规定可接受性取值为小写
 * preferred/admitted/deprecated/superseded/obsolete。转换器落库必须为小写，
 * 读取按大小写不敏感解析（兼容标准格式历史导入数据）。
 */
class AcceptabilityConverterTest {

    private final AcceptabilityConverter converter = new AcceptabilityConverter();

    @Test
    void toDatabase_lowercasesAllRatings() {
        assertThat(converter.convertToDatabaseColumn(Acceptability.PREFERRED)).isEqualTo("preferred");
        assertThat(converter.convertToDatabaseColumn(Acceptability.ADMITTED)).isEqualTo("admitted");
        assertThat(converter.convertToDatabaseColumn(Acceptability.DEPRECATED)).isEqualTo("deprecated");
        assertThat(converter.convertToDatabaseColumn(Acceptability.SUPERSEDED)).isEqualTo("superseded");
        assertThat(converter.convertToDatabaseColumn(Acceptability.OBSOLETE)).isEqualTo("obsolete");
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void toEntity_parsesStandardLowercase() {
        assertThat(converter.convertToEntityAttribute("preferred")).isEqualTo(Acceptability.PREFERRED);
        assertThat(converter.convertToEntityAttribute("admitted")).isEqualTo(Acceptability.ADMITTED);
        assertThat(converter.convertToEntityAttribute("obsolete")).isEqualTo(Acceptability.OBSOLETE);
    }

    @Test
    void toEntity_isCaseInsensitiveAndToleratesWhitespace() {
        assertThat(converter.convertToEntityAttribute("PREFERRED")).isEqualTo(Acceptability.PREFERRED);
        assertThat(converter.convertToEntityAttribute(" Preferred ")).isEqualTo(Acceptability.PREFERRED);
    }

    @Test
    void toEntity_nullAndBlankMapToNull() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
        assertThat(converter.convertToEntityAttribute("  ")).isNull();
    }
}
