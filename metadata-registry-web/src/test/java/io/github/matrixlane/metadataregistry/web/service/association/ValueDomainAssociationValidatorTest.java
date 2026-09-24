package io.github.matrixlane.metadataregistry.web.service.association;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Datetime_Period;
import io.github.matrixlane.metadataregistry.dataspecification.DataType;
import io.github.matrixlane.metadataregistry.dataspecification.DescribedConceptualDomain;
import io.github.matrixlane.metadataregistry.dataspecification.DescribedValueDomain;
import io.github.matrixlane.metadataregistry.dataspecification.Dimensionality;
import io.github.matrixlane.metadataregistry.dataspecification.MeasureClass;
import io.github.matrixlane.metadataregistry.dataspecification.UnitOfMeasure;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

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

    // ── 维度一致性 ──────────────────────────────────────────────────────────

    private MeasureClass measureClass(java.util.UUID id) {
        MeasureClass mc = new MeasureClass();
        mc.setId(id);
        return mc;
    }

    @Test
    void validate_vdToCd_dimensionalityMismatch_throws() {
        MeasureClass length = measureClass(java.util.UUID.randomUUID());
        UnitOfMeasure uom = new UnitOfMeasure();
        uom.setMeasureClasses(new java.util.ArrayList<>(List.of(measureClass(java.util.UUID.randomUUID()))));
        DescribedValueDomain vd = new DescribedValueDomain();
        vd.setUnitOfMeasure(uom);
        DescribedConceptualDomain cd = new DescribedConceptualDomain();
        Dimensionality dimensionality = new Dimensionality();
        dimensionality.setMeasureClasses(new java.util.ArrayList<>(List.of(length)));
        cd.setDimensionality(dimensionality);

        assertThatThrownBy(() -> validator.validate(AssociationType.VALUE_DOMAIN_TO_CONCEPTUAL_DOMAIN, vd, cd))
                .isInstanceOf(MetadataAssociationValidationException.class)
                .hasMessageContaining("计量单位所属计量类与概念域维度不一致");
    }

    @Test
    void validate_vdToCd_dimensionalityConsistent_passes() {
        MeasureClass shared = measureClass(java.util.UUID.randomUUID());
        UnitOfMeasure uom = new UnitOfMeasure();
        uom.setMeasureClasses(new java.util.ArrayList<>(List.of(shared)));
        DescribedValueDomain vd = new DescribedValueDomain();
        vd.setUnitOfMeasure(uom);
        DescribedConceptualDomain cd = new DescribedConceptualDomain();
        Dimensionality dimensionality = new Dimensionality();
        dimensionality.setMeasureClasses(new java.util.ArrayList<>(List.of(shared)));
        cd.setDimensionality(dimensionality);

        assertThatNoException().isThrownBy(
                () -> validator.validate(AssociationType.VALUE_DOMAIN_TO_CONCEPTUAL_DOMAIN, vd, cd));
    }

    @Test
    void validate_vdToCd_noDimensionalitySpecified_passes() {
        // 约束以"指定时"为前提:CD 未指定维度时不校验
        DescribedValueDomain vd = new DescribedValueDomain();
        UnitOfMeasure uom = new UnitOfMeasure();
        uom.setMeasureClasses(new java.util.ArrayList<>(List.of(measureClass(java.util.UUID.randomUUID()))));
        vd.setUnitOfMeasure(uom);

        assertThatNoException().isThrownBy(
                () -> validator.validate(AssociationType.VALUE_DOMAIN_TO_CONCEPTUAL_DOMAIN, vd, new DescribedConceptualDomain()));
    }

    @Test
    void validate_vdToUom_inconsistentWithCurrentCd_throws() {
        MeasureClass shared = measureClass(java.util.UUID.randomUUID());
        DescribedConceptualDomain cd = new DescribedConceptualDomain();
        Dimensionality dimensionality = new Dimensionality();
        dimensionality.setMeasureClasses(new java.util.ArrayList<>(List.of(shared)));
        cd.setDimensionality(dimensionality);
        DescribedValueDomain vd = new DescribedValueDomain();
        vd.setConceptualDomain(cd);
        UnitOfMeasure newUom = new UnitOfMeasure();
        newUom.setMeasureClasses(new java.util.ArrayList<>(
                List.of(measureClass(java.util.UUID.randomUUID()))));

        assertThatThrownBy(() -> validator.validate(AssociationType.VALUE_DOMAIN_TO_UNIT_OF_MEASURE, vd, newUom))
                .isInstanceOf(MetadataAssociationValidationException.class)
                .hasMessageContaining("计量单位所属计量类与概念域维度不一致");
    }

    @Test
    void validate_vdToUom_consistentWithCurrentCd_passes() {
        MeasureClass shared = measureClass(java.util.UUID.randomUUID());
        DescribedConceptualDomain cd = new DescribedConceptualDomain();
        Dimensionality dimensionality = new Dimensionality();
        dimensionality.setMeasureClasses(new java.util.ArrayList<>(List.of(shared)));
        cd.setDimensionality(dimensionality);
        DescribedValueDomain vd = new DescribedValueDomain();
        vd.setConceptualDomain(cd);
        UnitOfMeasure newUom = new UnitOfMeasure();
        newUom.setMeasureClasses(new java.util.ArrayList<>(List.of(shared)));

        assertThatNoException().isThrownBy(
                () -> validator.validate(AssociationType.VALUE_DOMAIN_TO_UNIT_OF_MEASURE, vd, newUom));
    }

    @Test
    void validate_vdToUom_noCdDimensionality_passes() {
        DescribedValueDomain vd = new DescribedValueDomain();
        UnitOfMeasure uom = new UnitOfMeasure();
        uom.setMeasureClasses(new java.util.ArrayList<>());

        assertThatNoException().isThrownBy(
                () -> validator.validate(AssociationType.VALUE_DOMAIN_TO_UNIT_OF_MEASURE, vd, uom));
    }
}
