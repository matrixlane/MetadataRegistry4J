package io.github.matrixlane.metadataregistry.web.service.dataquality;

import io.github.matrixlane.metadataregistry.web.dto.dataquality.DataQualityAssessmentResult.OutlierStat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class OutlierDetectorTest {

    private OutlierDetector detector;

    @BeforeEach
    void setUp() {
        detector = new OutlierDetector(5.0);
    }

    // ── IQR Method ───────────────────────────────────────────────

    @Test
    void shouldDetectOutliersUsingIQR() {
        // Normal values: 1-10, outliers: 100, 200
        List<Object> values = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 100, 200);
        List<Double> numeric = detector.extractNumeric(values);

        OutlierStat stat = detector.detectColumn("col1", numeric, values.size(), "IQR", 5.0);

        assertThat(stat.getColumnName()).isEqualTo("col1");
        assertThat(stat.getMethod()).isEqualTo("IQR");
        assertThat(stat.getTotalCount()).isEqualTo(12);
        assertThat(stat.getOutlierCount()).isGreaterThanOrEqualTo(2);
        assertThat(stat.getIqrLowerBound()).isNotNull();
        assertThat(stat.getIqrUpperBound()).isNotNull();
        assertThat(stat.getSigmaLowerBound()).isNull();
        assertThat(stat.getSigmaUpperBound()).isNull();
    }

    @Test
    void shouldDetectOutliersUsingIQRWithExtremeValues() {
        // Most values 1-10, extreme 100 and 200
        List<Object> values = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 100, 200);
        List<Double> numeric = detector.extractNumeric(values);

        OutlierStat stat = detector.detectColumn("col1", numeric, values.size(), "IQR", 5.0);

        assertThat(stat.getOutlierCount()).isGreaterThanOrEqualTo(2);
    }

    // ── 3σ Method ────────────────────────────────────────────────

    @Test
    void shouldDetectOutliersUsingThreeSigma() {
        // Mostly around 10, extreme: 1000
        List<Object> values = Arrays.asList(9, 10, 10, 11, 10, 9, 10, 11, 10, 9, 1000);
        List<Double> numeric = detector.extractNumeric(values);

        OutlierStat stat = detector.detectColumn("col1", numeric, values.size(), "THREE_SIGMA", 5.0);

        assertThat(stat.getColumnName()).isEqualTo("col1");
        assertThat(stat.getMethod()).isEqualTo("THREE_SIGMA");
        assertThat(stat.getOutlierCount()).isGreaterThanOrEqualTo(1);
        assertThat(stat.getIqrLowerBound()).isNull();
        assertThat(stat.getIqrUpperBound()).isNull();
        assertThat(stat.getSigmaLowerBound()).isNotNull();
        assertThat(stat.getSigmaUpperBound()).isNotNull();
    }

    @Test
    void shouldDetectOutlierInCenterData() {
        // Data with one massive outlier using IQR method
        List<Double> numeric = Arrays.asList(10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 1_000_000.0);

        OutlierStat stat = detector.detectColumn("col1", numeric, numeric.size(), "IQR", 5.0);

        assertThat(stat.getOutlierCount()).isGreaterThanOrEqualTo(1);
        assertThat(stat.getOutlierExamples()).isNotEmpty();
        assertThat(stat.getMethod()).isEqualTo("IQR");
    }

    // ── De-identified Examples ───────────────────────────────────

    @Test
    void shouldProvideDeidentifiedOutlierExamples() {
        List<Double> numeric = Arrays.asList(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0, 100.0, 200.0);

        OutlierStat stat = detector.detectColumn("col1", numeric, numeric.size(), "IQR", 5.0);

        List<String> examples = stat.getOutlierExamples();
        assertThat(examples).isNotEmpty();
        // Examples should be de-identified (row numbers, not real values)
        assertThat(examples).allMatch(s -> s.startsWith("row_"));
    }

    @Test
    void shouldLimitOutlierExamples() {
        // Many outliers
        List<Double> numeric = new java.util.ArrayList<>();
        for (int i = 0; i < 100; i++) {
            numeric.add((double) i);
        }
        // Add extreme values
        numeric.add(1000.0);
        numeric.add(2000.0);
        numeric.add(3000.0);
        numeric.add(4000.0);
        numeric.add(5000.0);
        numeric.add(6000.0);
        numeric.add(7000.0);
        numeric.add(8000.0);
        numeric.add(9000.0);
        numeric.add(10000.0);
        numeric.add(11000.0);
        numeric.add(12000.0);

        OutlierStat stat = detector.detectColumn("col1", numeric, numeric.size(), "IQR", 5.0);

        List<String> examples = stat.getOutlierExamples();
        assertThat(examples).hasSizeLessThanOrEqualTo(10);
    }

    // ── Empty / Edge Cases ───────────────────────────────────────

    @Test
    void shouldSkipNonNumericColumns() {
        Map<String, List<Object>> columnData = new HashMap<>();
        columnData.put("name", Arrays.asList("Alice", "Bob", "Charlie"));

        List<OutlierStat> results = detector.detect(columnData, null, null);
        assertThat(results).isEmpty();
    }

    @Test
    void shouldHandleEmptyColumnData() {
        Map<String, List<Object>> columnData = new HashMap<>();
        columnData.put("id", List.of());

        List<OutlierStat> results = detector.detect(columnData, null, null);
        assertThat(results).isEmpty();
    }

    @Test
    void shouldHandleMixedTypes() {
        List<Object> values = Arrays.asList(1, "not_a_number", 3, null, 5);
        List<Double> numeric = detector.extractNumeric(values);

        assertThat(numeric).hasSize(3); // 1, 3, 5
    }

    // ── Reproducibility ──────────────────────────────────────────

    @Test
    void shouldBeReproducible() {
        List<Double> numeric = Arrays.asList(1.0, 2.0, 3.0, 4.0, 5.0, 100.0);

        OutlierStat stat1 = detector.detectColumn("col1", numeric, numeric.size(), "IQR", 5.0);
        OutlierStat stat2 = detector.detectColumn("col1", numeric, numeric.size(), "IQR", 5.0);

        assertThat(stat1.getOutlierCount()).isEqualTo(stat2.getOutlierCount());
        assertThat(stat1.getIqrLowerBound()).isEqualTo(stat2.getIqrLowerBound());
        assertThat(stat1.getIqrUpperBound()).isEqualTo(stat2.getIqrUpperBound());
        assertThat(stat1.getOutlierExamples()).isEqualTo(stat2.getOutlierExamples());
    }

    // ── Percentile Calculation ───────────────────────────────────

    @Test
    void shouldCalculatePercentileCorrectly() {
        List<Double> sorted = Arrays.asList(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0);

        double q1 = detector.percentile(sorted, 25);
        assertThat(q1).isCloseTo(3.25, within(0.01d));

        double median = detector.percentile(sorted, 50);
        assertThat(median).isCloseTo(5.5, within(0.01d));

        double q3 = detector.percentile(sorted, 75);
        assertThat(q3).isCloseTo(7.75, within(0.01d));
    }

    @Test
    void shouldHandleSingleElementPercentile() {
        List<Double> single = List.of(5.0);
        assertThat(detector.percentile(single, 50)).isEqualTo(5.0);
    }

    @Test
    void shouldHandleEmptyPercentile() {
        assertThat(detector.percentile(List.of(), 50)).isZero();
    }

    // ── extractNumeric ───────────────────────────────────────────

    @Test
    void extractNumericShouldHandleNullInput() {
        assertThat(detector.extractNumeric(null)).isEmpty();
    }

    @Test
    void extractNumericShouldParseNumericStrings() {
        List<Object> values = Arrays.asList("1", "2.5", "3.14");
        List<Double> numeric = detector.extractNumeric(values);
        assertThat(numeric).containsExactly(1.0, 2.5, 3.14);
    }

}
