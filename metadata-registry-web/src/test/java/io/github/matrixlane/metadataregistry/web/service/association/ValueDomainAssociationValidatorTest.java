package io.github.matrixlane.metadataregistry.web.service.association;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Datetime_Period;
import io.github.matrixlane.metadataregistry.dataspecification.DataType;
import io.github.matrixlane.metadataregistry.dataspecification.DescribedValueDomain;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 值域完整性校验（ISO/IEC 11179-31:2023 表 5）：datatype 与 valid_period 基数均为 1。
 * valid_period 须含 start_datetime（§7.4.2.8.5），结束日期可选（缺省表示仍有效）。
 */
class ValueDomainAssociationValidatorTest {

    private final ValueDomainAssociationValidator validator = new ValueDomainAssociationValidator();

    private DescribedValueDomain valueDomainWithDataType() {
        DescribedValueDomain vd = new DescribedValueDomain();
        vd.setDataType(new DataType());
        return vd;
    }

    @Test
    void validateCompleteness_missingValidPeriodStart_throws() {
        DescribedValueDomain vd = valueDomainWithDataType();

        assertThatThrownBy(() -> validator.validateCompleteness(vd))
                .isInstanceOf(MetadataAssociationValidationException.class)
                .hasMessageContaining("有效日期（起始时间）")
                .hasMessageContaining("ISO/IEC 11179-31:2023");
    }

    @Test
    void validateCompleteness_missingWholeValidPeriod_throws() {
        // validPeriod 整体缺失与 start 缺失同等拒绝
        DescribedValueDomain vd = valueDomainWithDataType();

        assertThatThrownBy(() -> validator.validateCompleteness(vd))
                .isInstanceOf(MetadataAssociationValidationException.class)
                .hasMessageContaining("有效日期（起始时间）");
    }

    @Test
    void validateCompleteness_startOnly_passes() {
        DescribedValueDomain vd = valueDomainWithDataType();
        Datetime_Period period = new Datetime_Period();
        period.setStartDateTime(OffsetDateTime.now());
        vd.setValidPeriod(period);

        assertThatNoException().isThrownBy(() -> validator.validateCompleteness(vd));
    }

    @Test
    void validateCompleteness_startAndEnd_passes() {
        DescribedValueDomain vd = valueDomainWithDataType();
        Datetime_Period period = new Datetime_Period();
        period.setStartDateTime(OffsetDateTime.now().minusDays(1));
        period.setEndDateTime(OffsetDateTime.now().plusDays(1));
        vd.setValidPeriod(period);

        assertThatNoException().isThrownBy(() -> validator.validateCompleteness(vd));
    }

    @Test
    void validateCompleteness_dataTypeCheckStaysFirst() {
        // dataType 在先：两者皆缺时报 datatype 错误
        DescribedValueDomain vd = new DescribedValueDomain();

        assertThatThrownBy(() -> validator.validateCompleteness(vd))
                .isInstanceOf(MetadataAssociationValidationException.class)
                .hasMessageContaining("数据类型");
    }
}
