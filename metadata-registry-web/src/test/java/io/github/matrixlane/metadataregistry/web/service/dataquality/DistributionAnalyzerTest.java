package io.github.matrixlane.metadataregistry.web.service.dataquality;

import io.github.matrixlane.metadataregistry.web.dto.dataquality.DataQualityAssessmentResult.DistributionStat;
import io.github.matrixlane.metadataregistry.web.dto.dataquality.DataQualityAssessmentResult.ValueFrequency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DistributionAnalyzerTest {

    private DistributionAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new DistributionAnalyzer();
    }

    // ── Cardinality & Top Values ─────────────────────────────────

    @Test
    void shouldCalculateCardinality() {
        List<Object> values = Arrays.asList("a", "b", "a", "c", "b", "a", "d");
        DistributionStat stat = analyzer.analyzeColumn("col1", values, null);

        assertThat(stat.getCardinality()).isEqualTo(4);
    }

    @Test
    void shouldComputeTopValuesWithPercentages() {
        List<Object> values = Arrays.asList("a", "a", "a", "a", "b", "b", "c", "c", "d", "e");
        DistributionStat stat = analyzer.analyzeColumn("col1", values, null);

        List<ValueFrequency> topValues = stat.getTopValues();
        assertThat(topValues).hasSize(5);

        // 'a' appears 4/10 = 40%
        assertThat(topValues.get(0).getValue()).isEqualTo("a");
        assertThat(topValues.get(0).getCount()).isEqualTo(4);
        assertThat(topValues.get(0).getPercent()).isEqualTo(40.0);

        // 'b' appears 2/10 = 20%
        assertThat(topValues.get(1).getValue()).isEqualTo("b");
        assertThat(topValues.get(1).getPercent()).isEqualTo(20.0);
    }

    @Test
    void shouldLimitTopValuesToTen() {
        List<Object> values = Arrays.asList("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l");
        DistributionStat stat = analyzer.analyzeColumn("col1", values, null);

        assertThat(stat.getTopValues()).hasSize(10); // limited to 10
    }

    // ── Numeric Analysis ─────────────────────────────────────────

    @Test
    void shouldComputeNumericStats() {
        List<Object> values = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        DistributionStat stat = analyzer.analyzeColumn("col1", values, "numeric");

        assertThat(stat.getNumericMin()).isEqualTo(1.0);
        assertThat(stat.getNumericMax()).isEqualTo(10.0);
        assertThat(stat.getNumericMean()).isEqualTo(5.5);
        assertThat(stat.getNumericMedian()).isEqualTo(5.5);
        assertThat(stat.getNumericStdDev()).isNotNull();
    }

    @Test
    void shouldComputeNumericStatsWithOddCount() {
        List<Object> values = Arrays.asList(5, 1, 3, 7, 9);
        DistributionStat stat = analyzer.analyzeColumn("col1", values, "numeric");

        assertThat(stat.getNumericMin()).isEqualTo(1.0);
        assertThat(stat.getNumericMax()).isEqualTo(9.0);
        assertThat(stat.getNumericMean()).isEqualTo(5.0);
        assertThat(stat.getNumericMedian()).isEqualTo(5.0);
    }

    @Test
    void shouldHandleNumericStrings() {
        List<Object> values = Arrays.asList("10", "20", "30", "40", "50");
        DistributionStat stat = analyzer.analyzeColumn("col1", values, "numeric");

        assertThat(stat.getNumericMin()).isEqualTo(10.0);
        assertThat(stat.getNumericMax()).isEqualTo(50.0);
        assertThat(stat.getNumericMean()).isEqualTo(30.0);
    }

    @Test
    void shouldHandleMixedNumericAndNullValues() {
        List<Object> values = Arrays.asList(1, null, 2, null, 3, null, 4, null, 5);
        DistributionStat stat = analyzer.analyzeColumn("col1", values, "numeric");

        assertThat(stat.getNumericMin()).isEqualTo(1.0);
        assertThat(stat.getNumericMax()).isEqualTo(5.0);
        assertThat(stat.getNumericMean()).isEqualTo(3.0);
        assertThat(stat.getCardinality()).isEqualTo(5);
    }

    // ── Date Analysis ────────────────────────────────────────────

    @Test
    void shouldComputeDateStats() {
        List<Object> values = Arrays.asList(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 6, 15),
                LocalDate.of(2024, 12, 31)
        );
        DistributionStat stat = analyzer.analyzeColumn("col1", values, "date");

        assertThat(stat.getDateEarliest()).isEqualTo("2024-01-01");
        assertThat(stat.getDateLatest()).isEqualTo("2024-12-31");
        assertThat(stat.getDateSpanDays()).isEqualTo("365");
    }

    @Test
    void shouldHandleSingleDate() {
        List<Object> values = Arrays.asList(LocalDate.of(2024, 6, 15));
        DistributionStat stat = analyzer.analyzeColumn("col1", values, "date");

        assertThat(stat.getDateEarliest()).isEqualTo("2024-06-15");
        assertThat(stat.getDateLatest()).isEqualTo("2024-06-15");
        assertThat(stat.getDateSpanDays()).isEqualTo("0");
    }

    // ── String Analysis ──────────────────────────────────────────

    @Test
    void shouldComputeStringLengths() {
        List<Object> values = Arrays.asList("hello", "hi", "greetings", "yo");
        DistributionStat stat = analyzer.analyzeColumn("col1", values, "string");

        assertThat(stat.getMinLength()).isEqualTo(2);  // "hi", "yo"
        assertThat(stat.getMaxLength()).isEqualTo(9);   // "greetings"
    }

    // ── Empty / Null Handling ────────────────────────────────────

    @Test
    void shouldHandleEmptyList() {
        DistributionStat stat = analyzer.analyzeColumn("col1", List.of(), null);

        assertThat(stat.isNotApplicable()).isTrue();
    }

    @Test
    void shouldHandleAllNullValues() {
        List<Object> values = Arrays.asList(null, null, null);
        DistributionStat stat = analyzer.analyzeColumn("col1", values, null);

        assertThat(stat.isNotApplicable()).isTrue();
        assertThat(stat.getCardinality()).isZero();
    }

    // ── Type Detection ───────────────────────────────────────────

    @Test
    void shouldDetectNumericType() {
        List<Object> values = Arrays.asList(1, 2, 3, 4, 5);
        assertThat(analyzer.detectType(values, null)).isEqualTo("numeric");
    }

    @Test
    void shouldDetectDateType() {
        List<Object> values = Arrays.asList(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 2, 2)
        );
        assertThat(analyzer.detectType(values, null)).isEqualTo("date");
    }

    @Test
    void shouldDetectStringType() {
        List<Object> values = Arrays.asList("hello", "world", "foo", "bar");
        assertThat(analyzer.detectType(values, null)).isEqualTo("string");
    }

    @Test
    void shouldUseHintWhenProvided() {
        List<Object> values = Arrays.asList("hello", "world");
        assertThat(analyzer.detectType(values, "numeric")).isEqualTo("numeric");
    }

    // ── Multiple Columns ─────────────────────────────────────────

    @Test
    void shouldAnalyzeMultipleColumns() {
        Map<String, List<Object>> columnData = new HashMap<>();
        columnData.put("id", Arrays.asList(1, 2, 3, 4, 5));
        columnData.put("name", Arrays.asList("Alice", "Bob", "Charlie", "Diana", "Eve"));

        Map<String, String> hints = new HashMap<>();
        hints.put("id", "numeric");
        hints.put("name", "string");

        List<DistributionStat> results = analyzer.analyze(columnData, hints);

        assertThat(results).hasSize(2);

        DistributionStat idStat = results.stream().filter(s -> s.getColumnName().equals("id")).findFirst().orElseThrow();
        assertThat(idStat.getNumericMin()).isEqualTo(1.0);
        assertThat(idStat.getNumericMax()).isEqualTo(5.0);

        DistributionStat nameStat = results.stream().filter(s -> s.getColumnName().equals("name")).findFirst().orElseThrow();
        assertThat(nameStat.getCardinality()).isEqualTo(5);
        assertThat(nameStat.getMinLength()).isGreaterThan(0);
    }

    // ── computeTopValues ─────────────────────────────────────────

    @Test
    void computeTopValuesShouldReturnEmptyForEmptyInput() {
        assertThat(analyzer.computeTopValues(List.of(), 10)).isEmpty();
    }

    @Test
    void computeTopValuesShouldRespectLimit() {
        List<Object> values = Arrays.asList("a", "b", "c", "d", "e");
        List<ValueFrequency> result = analyzer.computeTopValues(values, 3);

        assertThat(result).hasSize(3);
    }
}
