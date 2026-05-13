package io.github.matrixlane.metadataregistry.web.service.dataquality;

import io.github.matrixlane.metadataregistry.web.dto.dataquality.DataQualityAssessmentRequest;
import io.github.matrixlane.metadataregistry.web.dto.dataquality.DataQualityAssessmentResult.SamplingInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SamplingStrategyTest {

    private SamplingStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new SamplingStrategy(10_000);
    }

    @Test
    void shouldUseFullScanForSmallTable() {
        DataQualityAssessmentRequest req = DataQualityAssessmentRequest.forTable("test", "ds1");
        req.setForceFullScan(false);

        assertThat(strategy.shouldSample(5000, req)).isFalse();
    }

    @Test
    void shouldUseSamplingForLargeTable() {
        DataQualityAssessmentRequest req = DataQualityAssessmentRequest.forTable("test", "ds1");
        req.setForceFullScan(false);

        assertThat(strategy.shouldSample(50_000, req)).isTrue();
    }

    @Test
    void shouldUseFullScanWhenForceFullScan() {
        DataQualityAssessmentRequest req = DataQualityAssessmentRequest.forTable("test", "ds1");
        req.setForceFullScan(true);

        assertThat(strategy.shouldSample(1_000_000, req)).isFalse();
    }

    @Test
    void shouldUseFullScanAtExactThreshold() {
        DataQualityAssessmentRequest req = DataQualityAssessmentRequest.forTable("test", "ds1");
        req.setForceFullScan(false);

        assertThat(strategy.shouldSample(10_000, req)).isFalse();
    }

    @Test
    void shouldReturnCorrectEffectiveRowsForFullScan() {
        DataQualityAssessmentRequest req = new DataQualityAssessmentRequest();
        assertThat(strategy.effectiveRows(5000, false, req)).isEqualTo(5000);
    }

    @Test
    void shouldReturnCorrectEffectiveRowsForSampling() {
        DataQualityAssessmentRequest req = new DataQualityAssessmentRequest();
        req.setSampleSize(10000);
        assertThat(strategy.effectiveRows(50000, true, req)).isEqualTo(10000);
    }

    @Test
    void shouldCapEffectiveRowsToTotalRowsWhenSampling() {
        DataQualityAssessmentRequest req = new DataQualityAssessmentRequest();
        req.setSampleSize(20000);
        assertThat(strategy.effectiveRows(5000, true, req)).isEqualTo(5000);
    }

    @Test
    void shouldResolveRandomMethodForModerateTables() {
        DataQualityAssessmentRequest req = new DataQualityAssessmentRequest();
        assertThat(strategy.resolveMethod(50000, true, req)).isEqualTo("random");
    }

    @Test
    void shouldResolveTablesampleForLargeTables() {
        DataQualityAssessmentRequest req = new DataQualityAssessmentRequest();
        assertThat(strategy.resolveMethod(600_000, true, req)).isEqualTo("tablesample");
    }

    @Test
    void shouldResolveFullForNoSampling() {
        DataQualityAssessmentRequest req = new DataQualityAssessmentRequest();
        assertThat(strategy.resolveMethod(10000, false, req)).isEqualTo("full");
    }

    @Test
    void shouldBuildFullSamplingInfo() {
        DataQualityAssessmentRequest req = new DataQualityAssessmentRequest();
        SamplingInfo info = strategy.buildSamplingInfo(5000, false, 5000, req);

        assertThat(info.getMethod()).isEqualTo("full");
        assertThat(info.getSampledRows()).isEqualTo(5000);
        assertThat(info.getTotalRows()).isEqualTo(5000);
        assertThat(info.getSamplingRate()).isEqualTo(1.0);
        assertThat(info.getConfidenceNote()).contains("全量统计");
    }

    @Test
    void shouldBuildSamplingInfo() {
        DataQualityAssessmentRequest req = new DataQualityAssessmentRequest();
        SamplingInfo info = strategy.buildSamplingInfo(50000, true, 10000, req);

        assertThat(info.getMethod()).isEqualTo("random");
        assertThat(info.getSampledRows()).isEqualTo(10000);
        assertThat(info.getTotalRows()).isEqualTo(50000);
        assertThat(info.getSamplingRate()).isCloseTo(0.2, within(0.01d));
        assertThat(info.getConfidenceNote()).contains("采样统计");
        assertThat(info.getConfidenceNote()).contains("95%");
    }
}
