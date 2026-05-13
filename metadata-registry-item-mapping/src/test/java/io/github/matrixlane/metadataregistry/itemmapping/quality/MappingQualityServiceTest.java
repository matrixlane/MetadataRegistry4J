package io.github.matrixlane.metadataregistry.itemmapping.quality;

import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMapping;
import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMappingObject;
import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMappingSubject;
import io.github.matrixlane.metadataregistry.itemmapping.entity.MappingScheme;
import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingType;
import io.github.matrixlane.metadataregistry.itemmapping.repository.ItemMappingRepository;
import io.github.matrixlane.metadataregistry.itemmapping.repository.MappingSchemeRepository;
import io.github.matrixlane.metadataregistry.itemmapping.service.ItemMappingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MappingQualityServiceTest {

    @Mock
    private ItemMappingRepository itemMappingRepository;

    @Mock
    private MappingSchemeRepository schemeRepository;

    @Mock
    private ItemMappingService mappingService;

    private MappingQualityService service;

    @BeforeEach
    void setUp() {
        service = new MappingQualityService(itemMappingRepository, schemeRepository, mappingService);
    }

    private ItemMapping createMapping(UUID id, UUID subjectId, UUID objectId,
                                       MappingType type, String description, String transformationRule) {
        ItemMappingSubject sub = ItemMappingSubject.forItem(subjectId);
        sub.setId(UUID.randomUUID());
        ItemMappingObject obj = ItemMappingObject.forItem(objectId);
        obj.setId(UUID.randomUUID());
        ItemMapping mapping = new ItemMapping(sub, obj, type, description, transformationRule, null);
        mapping.setId(id);
        return mapping;
    }

    // ---- generateReport tests ----

    @Test
    void generateReport_schemeNotFound_shouldThrow() {
        UUID schemeId = UUID.randomUUID();
        when(schemeRepository.findById(schemeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generateReport(schemeId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Mapping scheme not found");
    }

    @Test
    void generateReport_emptyMappings_shouldReturnZeroScores() {
        UUID schemeId = UUID.randomUUID();
        MappingScheme scheme = new MappingScheme("Test Scheme", "src", "tgt", "desc");
        scheme.setId(schemeId);

        when(schemeRepository.findById(schemeId)).thenReturn(Optional.of(scheme));
        when(itemMappingRepository.findByMappingSchemeId(schemeId)).thenReturn(List.of());

        MappingQualityReport report = service.generateReport(schemeId);

        assertThat(report.schemeId()).isEqualTo(schemeId);
        assertThat(report.metrics().totalMappings()).isZero();
        assertThat(report.metrics().overallScore()).isZero();
        assertThat(report.highRiskMappings()).isEmpty();
        assertThat(report.issues()).isEmpty();
        assertThat(report.improvementTips()).hasSize(1);
        assertThat(report.improvementTips().get(0)).contains("No mappings found");
    }

    @Test
    void generateReport_perfectMappings_shouldReturnHighScores() {
        UUID schemeId = UUID.randomUUID();
        MappingScheme scheme = new MappingScheme("Test Scheme", "src", "tgt", "desc");
        scheme.setId(schemeId);

        UUID s1 = UUID.randomUUID();
        UUID o1 = UUID.randomUUID();
        ItemMapping m1 = createMapping(UUID.randomUUID(), s1, o1, MappingType.EQUIVALENT,
                "Has description", null);

        // Add reverse equivalent for symmetry
        ItemMapping m2 = createMapping(UUID.randomUUID(), o1, s1, MappingType.EQUIVALENT,
                "Reverse desc", null);

        when(schemeRepository.findById(schemeId)).thenReturn(Optional.of(scheme));
        when(itemMappingRepository.findByMappingSchemeId(schemeId)).thenReturn(List.of(m1, m2));

        MappingQualityReport report = service.generateReport(schemeId);

        assertThat(report.metrics().descriptionRate()).isEqualTo(100.0);
        assertThat(report.metrics().consistencyScore()).isEqualTo(100.0);
        assertThat(report.metrics().issuesFound()).isZero();
        assertThat(report.highRiskMappings()).isEmpty();
    }

    @Test
    void generateReport_missingDescriptions_shouldFlagIssues() {
        UUID schemeId = UUID.randomUUID();
        MappingScheme scheme = new MappingScheme("Test Scheme", "src", "tgt", "desc");
        scheme.setId(schemeId);

        UUID s1 = UUID.randomUUID();
        UUID o1 = UUID.randomUUID();
        ItemMapping m1 = createMapping(UUID.randomUUID(), s1, o1, MappingType.EQUIVALENT,
                null, null);

        when(schemeRepository.findById(schemeId)).thenReturn(Optional.of(scheme));
        when(itemMappingRepository.findByMappingSchemeId(schemeId)).thenReturn(List.of(m1));

        MappingQualityReport report = service.generateReport(schemeId);

        assertThat(report.metrics().descriptionRate()).isZero();
        assertThat(report.issues()).hasSize(2); // missing description + symmetry violation

        boolean hasMissingDesc = report.issues().stream()
                .anyMatch(i -> i.issueType().equals("MISSING_DESCRIPTION"));
        assertThat(hasMissingDesc).isTrue();
    }

    @Test
    void generateReport_missingTransformationRule_shouldFlagIssue() {
        UUID schemeId = UUID.randomUUID();
        MappingScheme scheme = new MappingScheme("Test Scheme", "src", "tgt", "desc");
        scheme.setId(schemeId);

        UUID s1 = UUID.randomUUID();
        UUID o1 = UUID.randomUUID();
        ItemMapping m1 = createMapping(UUID.randomUUID(), s1, o1, MappingType.DERIVED,
                "Has desc", null); // DERIVED but no transformation rule

        when(schemeRepository.findById(schemeId)).thenReturn(Optional.of(scheme));
        when(itemMappingRepository.findByMappingSchemeId(schemeId)).thenReturn(List.of(m1));

        MappingQualityReport report = service.generateReport(schemeId);

        boolean hasMissingRule = report.issues().stream()
                .anyMatch(i -> i.issueType().equals("MISSING_TRANSFORMATION_RULE"));
        assertThat(hasMissingRule).isTrue();
        assertThat(report.metrics().transformationRate()).isZero();
    }

    @Test
    void generateReport_typeConflict_shouldFlagHighSeverity() {
        UUID schemeId = UUID.randomUUID();
        MappingScheme scheme = new MappingScheme("Test Scheme", "src", "tgt", "desc");
        scheme.setId(schemeId);

        UUID s1 = UUID.randomUUID();
        UUID o1 = UUID.randomUUID();
        ItemMapping m1 = createMapping(UUID.randomUUID(), s1, o1, MappingType.EQUIVALENT,
                "desc", null);
        ItemMapping m2 = createMapping(UUID.randomUUID(), s1, o1, MappingType.DERIVED,
                "desc2", null);

        when(schemeRepository.findById(schemeId)).thenReturn(Optional.of(scheme));
        when(itemMappingRepository.findByMappingSchemeId(schemeId)).thenReturn(List.of(m1, m2));

        MappingQualityReport report = service.generateReport(schemeId);

        boolean hasTypeConflict = report.issues().stream()
                .anyMatch(i -> i.issueType().equals("TYPE_CONFLICT") && i.severity() == IssueSeverity.HIGH);
        assertThat(hasTypeConflict).isTrue();
        assertThat(report.highRiskMappings()).isNotEmpty();
    }

    @Test
    void generateReport_symmetryViolation_shouldFlagMediumSeverity() {
        UUID schemeId = UUID.randomUUID();
        MappingScheme scheme = new MappingScheme("Test Scheme", "src", "tgt", "desc");
        scheme.setId(schemeId);

        UUID s1 = UUID.randomUUID();
        UUID o1 = UUID.randomUUID();
        // Only one direction of EQUIVALENT
        ItemMapping m1 = createMapping(UUID.randomUUID(), s1, o1, MappingType.EQUIVALENT,
                "desc", null);

        when(schemeRepository.findById(schemeId)).thenReturn(Optional.of(scheme));
        when(itemMappingRepository.findByMappingSchemeId(schemeId)).thenReturn(List.of(m1));

        MappingQualityReport report = service.generateReport(schemeId);

        boolean hasSymmetryIssue = report.issues().stream()
                .anyMatch(i -> i.issueType().equals("SYMMETRY_VIOLATION") && i.severity() == IssueSeverity.MEDIUM);
        assertThat(hasSymmetryIssue).isTrue();
    }

    @Test
    void generateReport_shouldIncludeImprovementTips() {
        UUID schemeId = UUID.randomUUID();
        MappingScheme scheme = new MappingScheme("Test Scheme", "src", "tgt", "desc");
        scheme.setId(schemeId);

        UUID s1 = UUID.randomUUID();
        UUID o1 = UUID.randomUUID();
        ItemMapping m1 = createMapping(UUID.randomUUID(), s1, o1, MappingType.EQUIVALENT,
                null, null);

        when(schemeRepository.findById(schemeId)).thenReturn(Optional.of(scheme));
        when(itemMappingRepository.findByMappingSchemeId(schemeId)).thenReturn(List.of(m1));

        MappingQualityReport report = service.generateReport(schemeId);

        assertThat(report.improvementTips()).isNotEmpty();
    }

    // ---- computeMetrics tests ----

    @Test
    void computeMetrics_withTransformationRules_shouldCalculateCorrectRate() {
        UUID schemeId = UUID.randomUUID();
        UUID s1 = UUID.randomUUID();
        UUID o1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();
        UUID o2 = UUID.randomUUID();

        ItemMapping m1 = createMapping(UUID.randomUUID(), s1, o1, MappingType.DERIVED,
                "desc", "rule1");
        ItemMapping m2 = createMapping(UUID.randomUUID(), s2, o2, MappingType.DERIVED,
                "desc", null); // missing rule

        MappingQualityMetrics metrics = service.computeMetrics(schemeId, List.of(m1, m2));

        assertThat(metrics.transformationRate()).isEqualTo(50.0);
    }

    @Test
    void computeMetrics_noTransformRelevantMappings_shouldReturn100Percent() {
        UUID schemeId = UUID.randomUUID();
        UUID s1 = UUID.randomUUID();
        UUID o1 = UUID.randomUUID();

        ItemMapping m1 = createMapping(UUID.randomUUID(), s1, o1, MappingType.EQUIVALENT,
                "desc", null);

        MappingQualityMetrics metrics = service.computeMetrics(schemeId, List.of(m1));

        assertThat(metrics.transformationRate()).isEqualTo(100.0);
    }

    // ---- detectIssues edge cases ----

    @Test
    void detectIssues_externalMapping_shouldBeIgnored() {
        UUID s1 = UUID.randomUUID();
        ItemMappingSubject sub = ItemMappingSubject.forItem(s1);
        sub.setId(UUID.randomUUID());
        ItemMappingObject obj = ItemMappingObject.forExternalUri("https://example.org/ext");
        obj.setId(UUID.randomUUID());
        ItemMapping m1 = new ItemMapping(sub, obj, MappingType.EQUIVALENT, null);
        m1.setId(UUID.randomUUID());

        List<QualityIssue> issues = service.detectIssues(List.of(m1));

        assertThat(issues).isEmpty();
    }

    @Test
    void detectIssues_cycle_shouldFlagHighSeverity() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();

        // A → B → C → A forms a cycle
        ItemMapping ab = createMapping(UUID.randomUUID(), a, b, MappingType.DERIVED, "desc", null);
        ItemMapping bc = createMapping(UUID.randomUUID(), b, c, MappingType.DERIVED, "desc", null);
        ItemMapping ca = createMapping(UUID.randomUUID(), c, a, MappingType.DERIVED, "desc", null);

        List<QualityIssue> issues = service.detectIssues(List.of(ab, bc, ca));

        boolean hasCycle = issues.stream()
                .anyMatch(i -> i.issueType().equals("CYCLE_DETECTED") && i.severity() == IssueSeverity.HIGH);
        assertThat(hasCycle).isTrue();
    }

    @Test
    void detectIssues_noCycle_shouldNotFlag() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();

        // A → B → C (no cycle)
        ItemMapping ab = createMapping(UUID.randomUUID(), a, b, MappingType.DERIVED, "desc", null);
        ItemMapping bc = createMapping(UUID.randomUUID(), b, c, MappingType.DERIVED, "desc", null);

        List<QualityIssue> issues = service.detectIssues(List.of(ab, bc));

        boolean hasCycle = issues.stream().anyMatch(i -> i.issueType().equals("CYCLE_DETECTED"));
        assertThat(hasCycle).isFalse();
    }

    @Test
    void detectIssues_equivalentShouldNotBeCheckedForCycles() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        // A ↔ B equivalent (not a cycle per WI-011-1)
        ItemMapping ab = createMapping(UUID.randomUUID(), a, b, MappingType.EQUIVALENT, "desc", null);
        ItemMapping ba = createMapping(UUID.randomUUID(), b, a, MappingType.EQUIVALENT, "desc", null);

        List<QualityIssue> issues = service.detectIssues(List.of(ab, ba));

        boolean hasCycle = issues.stream().anyMatch(i -> i.issueType().equals("CYCLE_DETECTED"));
        assertThat(hasCycle).isFalse();
    }

    @Test
    void generateReport_withCycle_shouldIncludeCycleTip() {
        UUID schemeId = UUID.randomUUID();
        MappingScheme scheme = new MappingScheme("Test Scheme", "src", "tgt", "desc");
        scheme.setId(schemeId);

        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();

        ItemMapping ab = createMapping(UUID.randomUUID(), a, b, MappingType.DERIVED, "desc", null);
        ItemMapping bc = createMapping(UUID.randomUUID(), b, c, MappingType.DERIVED, "desc", null);
        ItemMapping ca = createMapping(UUID.randomUUID(), c, a, MappingType.DERIVED, "desc", null);

        when(schemeRepository.findById(schemeId)).thenReturn(Optional.of(scheme));
        when(itemMappingRepository.findByMappingSchemeId(schemeId)).thenReturn(List.of(ab, bc, ca));

        MappingQualityReport report = service.generateReport(schemeId);

        boolean hasCycleTip = report.improvementTips().stream()
                .anyMatch(t -> t.contains("cycle") || t.contains("Cycle"));
        assertThat(hasCycleTip).isTrue();
    }

    @Test
    void identifyHighRiskMappings_withCycle_shouldIncludeCycleMappings() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();

        ItemMapping ab = createMapping(UUID.randomUUID(), a, b, MappingType.DERIVED, "desc", null);
        ItemMapping bc = createMapping(UUID.randomUUID(), b, c, MappingType.DERIVED, "desc", null);
        ItemMapping ca = createMapping(UUID.randomUUID(), c, a, MappingType.DERIVED, "desc", null);

        List<QualityIssue> issues = service.detectIssues(List.of(ab, bc, ca));
        List<UUID> highRisk = service.identifyHighRiskMappings(List.of(ab, bc, ca), issues);

        // Cycle issues have HIGH severity, so they should be flagged
        assertThat(highRisk).isNotEmpty();
    }
}
