package io.github.matrixlane.metadataregistry.web.service.dataquality;

import io.github.matrixlane.metadataregistry.web.dto.dataquality.DataQualityAssessmentResult.NullValueStat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class NullValueAnalyzerTest {

    private NullValueAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new NullValueAnalyzer(50.0);
    }

    @Test
    void shouldCalculateNullPercentCorrectly() {
        List<Object> values = Arrays.asList("a", null, "b", null, "c", null, "d", null, "e", "f");
        NullValueStat stat = analyzer.analyzeColumn("col1", values, 50.0);

        assertThat(stat.getColumnName()).isEqualTo("col1");
        assertThat(stat.getNullCount()).isEqualTo(4);
        assertThat(stat.getTotalCount()).isEqualTo(10);
        assertThat(stat.getNullPercent()).isEqualTo(40.0);
        assertThat(stat.isAboveThreshold()).isFalse();
        assertThat(stat.getThresholdPercent()).isEqualTo(50.0);
    }

    @Test
    void shouldFlagFieldAboveNullThreshold() {
        List<Object> values = Arrays.asList(null, null, null, null, "a", "b", "c", "d");
        NullValueStat stat = analyzer.analyzeColumn("col1", values, 50.0);

        assertThat(stat.getNullCount()).isEqualTo(4);
        assertThat(stat.getTotalCount()).isEqualTo(8);
        assertThat(stat.getNullPercent()).isEqualTo(50.0);
        // 50% is not > 50%, so should be false
        assertThat(stat.isAboveThreshold()).isFalse();
    }

    @Test
    void shouldFlagFieldAboveNullThresholdWhenExceeds() {
        List<Object> values = Arrays.asList(null, null, null, null, null, "a");
        NullValueStat stat = analyzer.analyzeColumn("col1", values, 50.0);

        assertThat(stat.getNullPercent()).isCloseTo(83.33, within(0.1));
        assertThat(stat.isAboveThreshold()).isTrue();
    }

    @Test
    void shouldHandleEmptyList() {
        NullValueStat stat = analyzer.analyzeColumn("col1", List.of(), 50.0);

        assertThat(stat.getNullCount()).isZero();
        assertThat(stat.getTotalCount()).isZero();
        assertThat(stat.getNullPercent()).isZero();
        assertThat(stat.isAboveThreshold()).isFalse();
    }

    @Test
    void shouldHandleAllNulls() {
        List<Object> values = Arrays.asList(null, null, null);
        NullValueStat stat = analyzer.analyzeColumn("col1", values, 50.0);

        assertThat(stat.getNullCount()).isEqualTo(3);
        assertThat(stat.getTotalCount()).isEqualTo(3);
        assertThat(stat.getNullPercent()).isEqualTo(100.0);
        assertThat(stat.isAboveThreshold()).isTrue();
    }

    @Test
    void shouldHandleAllNonNull() {
        List<Object> values = Arrays.asList("a", "b", "c");
        NullValueStat stat = analyzer.analyzeColumn("col1", values, 50.0);

        assertThat(stat.getNullCount()).isZero();
        assertThat(stat.getNullPercent()).isZero();
        assertThat(stat.isAboveThreshold()).isFalse();
    }

    @Test
    void shouldAnalyzeMultipleColumns() {
        Map<String, List<Object>> columnData = new HashMap<>();
        columnData.put("col_a", Arrays.asList("x", "y", "z"));
        columnData.put("col_b", Arrays.asList(null, null, "a", "b", "c"));

        List<NullValueStat> results = analyzer.analyze(columnData, null);

        assertThat(results).hasSize(2);

        NullValueStat colA = results.stream().filter(s -> s.getColumnName().equals("col_a")).findFirst().orElseThrow();
        assertThat(colA.getNullPercent()).isZero();

        NullValueStat colB = results.stream().filter(s -> s.getColumnName().equals("col_b")).findFirst().orElseThrow();
        assertThat(colB.getNullPercent()).isEqualTo(40.0);
    }

    @Test
    void shouldSupportColumnSpecificThresholds() {
        List<Object> values = Arrays.asList(null, null, "a");
        Map<String, Double> thresholds = new HashMap<>();
        thresholds.put("col1", 30.0);

        // Using analyze(Map, Map) to test per-column thresholds
        Map<String, List<Object>> columnData = new HashMap<>();
        columnData.put("col1", values);

        List<NullValueStat> results = analyzer.analyze(columnData, thresholds);
        NullValueStat stat = results.get(0);

        assertThat(stat.getNullPercent()).isCloseTo(66.67, within(0.1d));
        assertThat(stat.isAboveThreshold()).isTrue(); // 66.67 > 30
    }

}
