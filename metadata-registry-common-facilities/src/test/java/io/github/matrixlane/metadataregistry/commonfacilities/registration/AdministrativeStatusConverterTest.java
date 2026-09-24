package io.github.matrixlane.metadataregistry.commonfacilities.registration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdministrativeStatusConverterTest {

    private final AdministrativeStatusConverter converter = new AdministrativeStatusConverter();

    @Test
    void shouldRoundTripLowerCaseStandardValue() {
        String dbValue = converter.convertToDatabaseColumn(AdministrativeStatus.PROVISIONALLY_QUALIFIED);
        assertThat(dbValue).isEqualTo("provisionally qualified");

        AdministrativeStatus entity = converter.convertToEntityAttribute(dbValue);
        assertThat(entity).isEqualTo(AdministrativeStatus.PROVISIONALLY_QUALIFIED);
    }

    @Test
    void shouldReadTitleCaseStandardValue() {
        AdministrativeStatus entity = converter.convertToEntityAttribute("Provisionally Qualified");
        assertThat(entity).isEqualTo(AdministrativeStatus.PROVISIONALLY_QUALIFIED);
    }

    @Test
    void shouldRejectUnknownValue() {
        assertThatThrownBy(() -> converter.convertToEntityAttribute("unknown status"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown administrative status");
    }

    @Test
    void shouldMapNullAndBlankToNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
        assertThat(converter.convertToEntityAttribute("")).isNull();
        assertThat(converter.convertToEntityAttribute("   ")).isNull();
    }
}
