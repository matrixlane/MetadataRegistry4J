package io.github.matrixlane.metadataregistry.web.service.compliancereport;

import io.github.matrixlane.metadataregistry.datasetregistration.entity.InteropDataElement;
import io.github.matrixlane.metadataregistry.datasetregistration.entity.InteropDataSet;
import io.github.matrixlane.metadataregistry.datasetregistration.repository.InteropDataElementRepository;
import io.github.matrixlane.metadataregistry.datasetregistration.repository.InteropDataSetRepository;
import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMapping;
import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMappingObject;
import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMappingSubject;
import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingType;
import io.github.matrixlane.metadataregistry.itemmapping.repository.ItemMappingRepository;
import io.github.matrixlane.metadataregistry.web.dto.compliancereport.ComplianceReport;
import io.github.matrixlane.metadataregistry.web.dto.compliancereport.DataSetReport;
import io.github.matrixlane.metadataregistry.web.dto.compliancereport.ExportConfig;
import io.github.matrixlane.metadataregistry.web.dto.compliancereport.MappingEntry;
import io.github.matrixlane.metadataregistry.web.dto.compliancereport.OverallScore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * {@link ComplianceReportService} 单元测试.
 */
@ExtendWith(MockitoExtension.class)
class ComplianceReportServiceTest {

    @Mock
    private InteropDataSetRepository dataSetRepository;

    @Mock
    private InteropDataElementRepository dataElementRepository;

    @Mock
    private ItemMappingRepository mappingRepository;

    @Mock
    private Principal principal;

    private ComplianceScoreWeightConfig scoreWeightConfig;
    private ComplianceReportService service;

    private InteropDataSet dataSet;

    @BeforeEach
    void setUp() {
        scoreWeightConfig = new ComplianceScoreWeightConfig();
        scoreWeightConfig.setExact(1.0);
        scoreWeightConfig.setPartial(0.5);
        scoreWeightConfig.setNone(0.0);

        service = new ComplianceReportService(
                dataSetRepository, dataElementRepository,
                mappingRepository, scoreWeightConfig);

        dataSet = new InteropDataSet();
        dataSet.setId(UUID.randomUUID());
        dataSet.setCode("HDSB01.01");
        dataSet.setName("卫生服务数据集");
        dataSet.setVersion("1.0");

        lenient().when(principal.getName()).thenReturn("testuser");
    }

    // =========================================================================
    // 1. 报告生成：正常流程
    // =========================================================================

    @Test
    @DisplayName("生成报告 - 应包含正确的元信息")
    void generateReport_shouldContainMetadata() {
        when(dataSetRepository.findAll()).thenReturn(List.of(dataSet));
        when(dataElementRepository.findByDataSetId(dataSet.getId())).thenReturn(List.of());

        ComplianceReport report = service.generateReport(ExportConfig.defaults(), principal);

        assertThat(report).isNotNull();
        assertThat(report.title()).isEqualTo("互联互通标准化成熟度测评合规报告");
        assertThat(report.generatedBy()).isEqualTo("testuser");
        assertThat(report.generatedAt()).isNotNull();
    }

    @Test
    @DisplayName("生成报告 - 数据集为空时返回空报告")
    void generateReport_withNoDataSets_shouldReturnEmptyReport() {
        when(dataSetRepository.findAll()).thenReturn(List.of());

        ComplianceReport report = service.generateReport(ExportConfig.defaults(), principal);

        assertThat(report).isNotNull();
        assertThat(report.dataSetReports()).isEmpty();
        assertThat(report.overallScore().overallScore()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("生成报告 - 有匹配的数据元应正确统计")
    void generateReport_withMappedElements_shouldCountCorrectly() {
        // 创建一个有 mappedItemId 的数据元
        InteropDataElement element = createDataElement("DE01.01.001", "患者姓名", null);
        element.setMappedItemId(UUID.randomUUID());

        when(dataSetRepository.findAll()).thenReturn(List.of(dataSet));
        when(dataElementRepository.findByDataSetId(dataSet.getId())).thenReturn(List.of(element));

        // Mock mapping: EQUIVALENT → 已匹配
        ItemMapping mapping = createMapping(element.getId(), element.getMappedItemId(), MappingType.EQUIVALENT);
        when(mappingRepository.findByItemId(element.getId())).thenReturn(List.of(mapping));

        ComplianceReport report = service.generateReport(ExportConfig.defaults(), principal);

        assertThat(report.dataSetReports()).hasSize(1);
        DataSetReport ds = report.dataSetReports().get(0);
        assertThat(ds.mappings()).hasSize(1);
        assertThat(ds.mappings().get(0).matchStatus()).isEqualTo(ComplianceReportService.STATUS_MATCHED);
        assertThat(ds.missingEntries()).isEmpty();
    }

    @Test
    @DisplayName("生成报告 - 未匹配的数据元应归入缺失项")
    void generateReport_withUnmatchedElements_shouldIncludeMissing() {
        InteropDataElement element = createDataElement("DE01.01.002", "患者性别", null);

        when(dataSetRepository.findAll()).thenReturn(List.of(dataSet));
        when(dataElementRepository.findByDataSetId(dataSet.getId())).thenReturn(List.of(element));
        when(mappingRepository.findByItemId(element.getId())).thenReturn(List.of());

        ComplianceReport report = service.generateReport(ExportConfig.defaults(), principal);

        assertThat(report.dataSetReports()).hasSize(1);
        DataSetReport ds = report.dataSetReports().get(0);
        assertThat(ds.mappings().get(0).matchStatus()).isEqualTo(ComplianceReportService.STATUS_UNMATCHED);
        assertThat(ds.missingEntries()).hasSize(1);
        assertThat(ds.missingEntries().get(0).deIdentifier()).isEqualTo("DE01.01.002");
        assertThat(ds.missingEntries().get(0).suggestedAction()).isEqualTo("新建本地项");
    }

    // =========================================================================
    // 2. 导出配置筛选
    // =========================================================================

    @Test
    @DisplayName("生成报告 - 按数据集编码筛选")
    void generateReport_withDataSetFilter_shouldFilter() {
        InteropDataSet ds2 = new InteropDataSet();
        ds2.setId(UUID.randomUUID());
        ds2.setCode("HDSB02.01");
        ds2.setName("药品数据集");

        when(dataSetRepository.findAll()).thenReturn(List.of(dataSet, ds2));
        when(dataElementRepository.findByDataSetId(any())).thenReturn(List.of());

        ExportConfig config = new ExportConfig(List.of("HDSB01.01"), List.of(), "测试报告", "");
        ComplianceReport report = service.generateReport(config, principal);

        assertThat(report.dataSetReports()).hasSize(1);
        assertThat(report.dataSetReports().get(0).dataSetCode()).isEqualTo("HDSB01.01");
    }

    @Test
    @DisplayName("生成报告 - 按匹配状态筛选")
    void generateReport_withMatchStatusFilter_shouldFilter() {
        InteropDataElement matched = createDataElement("DE01.01.001", "患者姓名", null);
        matched.setMappedItemId(UUID.randomUUID());
        InteropDataElement unmatched = createDataElement("DE01.01.002", "患者性别", null);

        when(dataSetRepository.findAll()).thenReturn(List.of(dataSet));
        when(dataElementRepository.findByDataSetId(dataSet.getId())).thenReturn(List.of(matched, unmatched));

        // matched: EQUIVALENT mapping
        ItemMapping mapping = createMapping(matched.getId(), matched.getMappedItemId(), MappingType.EQUIVALENT);
        when(mappingRepository.findByItemId(matched.getId())).thenReturn(List.of(mapping));
        when(mappingRepository.findByItemId(unmatched.getId())).thenReturn(List.of());

        ExportConfig config = new ExportConfig(List.of(), List.of("已匹配"), "测试报告", "");
        ComplianceReport report = service.generateReport(config, principal);

        DataSetReport ds = report.dataSetReports().get(0);
        assertThat(ds.mappings()).hasSize(1);
        assertThat(ds.mappings().get(0).matchStatus()).isEqualTo(ComplianceReportService.STATUS_MATCHED);
    }

    // =========================================================================
    // 3. 匹配状态解析
    // =========================================================================

    @Test
    @DisplayName("resolveMatchStatus - mappedItemId + EQUIVALENT → 已匹配")
    void resolveMatchStatus_mappedWithEquivalent_shouldReturnMatched() {
        InteropDataElement element = createDataElement("DE01", "测试", null);
        element.setMappedItemId(UUID.randomUUID());

        ItemMapping mapping = createMapping(element.getId(), element.getMappedItemId(), MappingType.EQUIVALENT);
        when(mappingRepository.findByItemId(element.getId())).thenReturn(List.of(mapping));

        assertThat(service.resolveMatchStatus(element)).isEqualTo(ComplianceReportService.STATUS_MATCHED);
    }

    @Test
    @DisplayName("resolveMatchStatus - mappedItemId 无 EQUIVALENT → 待确认")
    void resolveMatchStatus_mappedWithoutEquivalent_shouldReturnPending() {
        InteropDataElement element = createDataElement("DE01", "测试", null);
        element.setMappedItemId(UUID.randomUUID());

        ItemMapping mapping = createMapping(element.getId(), element.getMappedItemId(), MappingType.DERIVED);
        when(mappingRepository.findByItemId(element.getId())).thenReturn(List.of(mapping));

        assertThat(service.resolveMatchStatus(element)).isEqualTo(ComplianceReportService.STATUS_PENDING);
    }

    @Test
    @DisplayName("resolveMatchStatus - 无 mappedItemId 无映射 → 未匹配")
    void resolveMatchStatus_noMapping_shouldReturnUnmatched() {
        InteropDataElement element = createDataElement("DE01", "测试", null);

        when(mappingRepository.findByItemId(element.getId())).thenReturn(List.of());

        assertThat(service.resolveMatchStatus(element)).isEqualTo(ComplianceReportService.STATUS_UNMATCHED);
    }

    // =========================================================================
    // 4. 质量评分计算
    // =========================================================================

    @Test
    @DisplayName("calculateQualityScore - 全部匹配得满分")
    void calculateQualityScore_allMatched_shouldReturn100() {
        InteropDataElement e1 = createDataElement("DE01", "测试1", null);
        e1.setMappedItemId(UUID.randomUUID());
        InteropDataElement e2 = createDataElement("DE02", "测试2", null);
        e2.setMappedItemId(UUID.randomUUID());

        when(mappingRepository.findByItemId(e1.getId())).thenReturn(
                List.of(createMapping(e1.getId(), e1.getMappedItemId(), MappingType.EQUIVALENT)));
        when(mappingRepository.findByItemId(e2.getId())).thenReturn(
                List.of(createMapping(e2.getId(), e2.getMappedItemId(), MappingType.EQUIVALENT)));

        double score = service.calculateQualityScore(List.of(e1, e2));
        assertThat(score).isEqualTo(100.0);
    }

    @Test
    @DisplayName("calculateQualityScore - 全部未匹配得 0 分")
    void calculateQualityScore_allUnmatched_shouldReturn0() {
        InteropDataElement e1 = createDataElement("DE01", "测试1", null);
        InteropDataElement e2 = createDataElement("DE02", "测试2", null);

        when(mappingRepository.findByItemId(e1.getId())).thenReturn(List.of());
        when(mappingRepository.findByItemId(e2.getId())).thenReturn(List.of());

        double score = service.calculateQualityScore(List.of(e1, e2));
        assertThat(score).isEqualTo(0.0);
    }

    @Test
    @DisplayName("calculateQualityScore - 部分匹配计算正确")
    void calculateQualityScore_partialMatch_shouldCalculateCorrectly() {
        // 1 exact (1.0) + 1 partial (0.5) + 1 none (0.0) = 1.5 / (3 * 1.0) = 0.5 → 50分
        InteropDataElement e1 = createDataElement("DE01", "测试1", null);
        e1.setMappedItemId(UUID.randomUUID());
        InteropDataElement e2 = createDataElement("DE02", "测试2", null);
        e2.setMappedItemId(UUID.randomUUID());
        InteropDataElement e3 = createDataElement("DE03", "测试3", null);

        when(mappingRepository.findByItemId(e1.getId())).thenReturn(
                List.of(createMapping(e1.getId(), e1.getMappedItemId(), MappingType.EQUIVALENT)));
        when(mappingRepository.findByItemId(e2.getId())).thenReturn(
                List.of(createMapping(e2.getId(), e2.getMappedItemId(), MappingType.DERIVED)));
        when(mappingRepository.findByItemId(e3.getId())).thenReturn(List.of());

        double score = service.calculateQualityScore(List.of(e1, e2, e3));
        assertThat(score).isEqualTo(50.0);
    }

    @Test
    @DisplayName("calculateQualityScore - 空列表返回 100")
    void calculateQualityScore_emptyList_shouldReturn100() {
        double score = service.calculateQualityScore(List.of());
        assertThat(score).isEqualTo(100.0);
    }

    // =========================================================================
    // 5. 改进建议
    // =========================================================================

    @Test
    @DisplayName("generateSuggestions - 低匹配率生成改进建议")
    void generateSuggestions_lowMatchRate_shouldGenerateSuggestion() {
        DataSetReport ds = new DataSetReport("HDSB01.01", "卫生服务数据集",
                List.of(), List.of(), 0.2, 20.0);

        List<String> suggestions = service.generateSuggestions(List.of(ds));
        assertThat(suggestions).isNotEmpty();
        assertThat(suggestions.get(0)).contains("匹配率较低");
    }

    @Test
    @DisplayName("generateSuggestions - 完全匹配时返回无需调整")
    void generateSuggestions_fullMatch_shouldReturnNoAdjustment() {
        DataSetReport ds = new DataSetReport("HDSB01.01", "卫生服务数据集",
                List.of(), List.of(), 1.0, 100.0);

        List<String> suggestions = service.generateSuggestions(List.of(ds));
        assertThat(suggestions).contains("所有数据集匹配率均为 100%，无需调整。");
    }

    // =========================================================================
    // 6. Principal 为 null 时的兜底
    // =========================================================================

    @Test
    @DisplayName("生成报告 - principal 为 null 时使用 system")
    void generateReport_withNullPrincipal_shouldUseSystem() {
        when(dataSetRepository.findAll()).thenReturn(List.of(dataSet));
        when(dataElementRepository.findByDataSetId(dataSet.getId())).thenReturn(List.of());

        ComplianceReport report = service.generateReport(ExportConfig.defaults(), null);
        assertThat(report.generatedBy()).isEqualTo("system");
    }

    // =========================================================================
    // 7. 自定义报告标题
    // =========================================================================

    @Test
    @DisplayName("生成报告 - 自定义标题")
    void generateReport_withCustomTitle_shouldUseIt() {
        when(dataSetRepository.findAll()).thenReturn(List.of(dataSet));
        when(dataElementRepository.findByDataSetId(dataSet.getId())).thenReturn(List.of());

        ExportConfig config = new ExportConfig(List.of(), List.of(), "自定义测评报告", "说明文字");
        ComplianceReport report = service.generateReport(config, principal);

        assertThat(report.title()).isEqualTo("自定义测评报告");
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    private InteropDataElement createDataElement(String identifier, String name, UUID mappedItemId) {
        InteropDataElement element = new InteropDataElement();
        element.setId(UUID.randomUUID());
        element.setDeIdentifier(identifier);
        element.setDeName(name);
        element.setDeDefinition("定义 for " + name);
        element.setMappedItemId(mappedItemId);
        element.setDataSet(dataSet);
        element.setCreatedAt(LocalDateTime.now());
        return element;
    }

    private ItemMapping createMapping(UUID subjectId, UUID objectId, MappingType type) {
        ItemMappingSubject subject = ItemMappingSubject.forItem(subjectId);
        ItemMappingObject object = ItemMappingObject.forItem(objectId);
        return new ItemMapping(subject, object, type);
    }
}
