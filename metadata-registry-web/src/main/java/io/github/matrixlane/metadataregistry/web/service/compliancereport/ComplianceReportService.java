package io.github.matrixlane.metadataregistry.web.service.compliancereport;

import io.github.matrixlane.metadataregistry.datasetregistration.entity.InteropDataElement;
import io.github.matrixlane.metadataregistry.datasetregistration.entity.InteropDataSet;
import io.github.matrixlane.metadataregistry.datasetregistration.repository.InteropDataElementRepository;
import io.github.matrixlane.metadataregistry.datasetregistration.repository.InteropDataSetRepository;
import io.github.matrixlane.metadataregistry.itemmapping.entity.ItemMapping;
import io.github.matrixlane.metadataregistry.itemmapping.enums.MappingType;
import io.github.matrixlane.metadataregistry.itemmapping.repository.ItemMappingRepository;
import io.github.matrixlane.metadataregistry.web.dto.compliancereport.ComplianceReport;
import io.github.matrixlane.metadataregistry.web.dto.compliancereport.DataSetReport;
import io.github.matrixlane.metadataregistry.web.dto.compliancereport.ExportConfig;
import io.github.matrixlane.metadataregistry.web.dto.compliancereport.MappingEntry;
import io.github.matrixlane.metadataregistry.web.dto.compliancereport.MissingEntry;
import io.github.matrixlane.metadataregistry.web.dto.compliancereport.OverallScore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 合规报告生成服务.
 *
 * <p>根据互联互通数据元对齐结果（WI-013-1 + WI-013-2），
 * 生成标准化成熟度测评所需的合规报告数据。
 *
 * <p>MVP 功能（按架构评审决策）：
 * <ul>
 *   <li>仅支持 Excel 导出（Word 延后到 V1）</li>
 *   <li>报告结构硬编码（不引入模板引擎）</li>
 *   <li>评分权重参数化（通过 {@link ComplianceScoreWeightConfig}）</li>
 *   <li>不包含评分趋势/历史对比</li>
 * </ul>
 */
@Service
public class ComplianceReportService {

    private static final Logger log = LoggerFactory.getLogger(ComplianceReportService.class);

    /**
     * 匹配状态常量.
     */
    public static final String STATUS_MATCHED = "已匹配";
    public static final String STATUS_PENDING = "待确认";
    public static final String STATUS_UNMATCHED = "未匹配";

    private final InteropDataSetRepository dataSetRepository;
    private final InteropDataElementRepository dataElementRepository;
    private final ItemMappingRepository mappingRepository;
    private final ComplianceScoreWeightConfig scoreWeightConfig;

    public ComplianceReportService(
            InteropDataSetRepository dataSetRepository,
            InteropDataElementRepository dataElementRepository,
            ItemMappingRepository mappingRepository,
            ComplianceScoreWeightConfig scoreWeightConfig) {
        this.dataSetRepository = dataSetRepository;
        this.dataElementRepository = dataElementRepository;
        this.mappingRepository = mappingRepository;
        this.scoreWeightConfig = scoreWeightConfig;
    }

    // ─────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────

    /**
     * 生成合规报告.
     *
     * @param config   导出配置（数据集筛选、匹配状态筛选等）
     * @param principal 当前用户（用于报告元信息）
     * @return 完整的合规报告
     */
    @Transactional(readOnly = true)
    public ComplianceReport generateReport(ExportConfig config, Principal principal) {
        String generatedBy = (principal != null) ? principal.getName() : "system";
        LocalDateTime now = LocalDateTime.now();

        // 获取所有数据集
        List<InteropDataSet> allDataSets = dataSetRepository.findAll();

        // 按导出配置筛选数据集
        List<InteropDataSet> filteredDataSets = filterDataSets(allDataSets, config);

        // 筛选的匹配状态集合
        Set<String> filterStatuses = (config.matchStatuses() == null || config.matchStatuses().isEmpty())
                ? Set.of(STATUS_MATCHED, STATUS_PENDING, STATUS_UNMATCHED)
                : new HashSet<>(config.matchStatuses());

        // 为每个数据集生成报告
        List<DataSetReport> dataSetReports = new ArrayList<>();
        for (InteropDataSet ds : filteredDataSets) {
            DataSetReport dsReport = buildDataSetReport(ds, filterStatuses);
            dataSetReports.add(dsReport);
        }

        // 计算总体评分
        OverallScore overallScore = calculateOverallScore(dataSetReports);

        String title = (config.reportTitle() != null && !config.reportTitle().isBlank())
                ? config.reportTitle()
                : "互联互通标准化成熟度测评合规报告";

        return new ComplianceReport(title, now, generatedBy, dataSetReports, overallScore);
    }

    // ─────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────

    /**
     * 按导出配置筛选数据集.
     */
    private List<InteropDataSet> filterDataSets(List<InteropDataSet> allDataSets, ExportConfig config) {
        if (config.dataSetCodes() == null || config.dataSetCodes().isEmpty()) {
            return allDataSets;
        }
        Set<String> allowedCodes = new HashSet<>(config.dataSetCodes());
        return allDataSets.stream()
                .filter(ds -> allowedCodes.contains(ds.getCode()))
                .toList();
    }

    /**
     * 为单个数据集构建报告.
     */
    private DataSetReport buildDataSetReport(InteropDataSet dataSet, Set<String> filterStatuses) {
        List<InteropDataElement> elements = dataElementRepository.findByDataSetId(dataSet.getId());

        // 构建对照表
        List<MappingEntry> mappings = new ArrayList<>();
        List<MissingEntry> missingEntries = new ArrayList<>();
        int matchedCount = 0;
        int totalCount = elements.size();

        int seq = 1;
        for (InteropDataElement element : elements) {
            String status = resolveMatchStatus(element);
            String localIdentifier = (element.getMappedItemId() != null)
                    ? element.getMappedItemId().toString() : "";
            String localName = resolveLocalName(element);

            // 只包含筛选的匹配状态
            if (!filterStatuses.contains(status)) {
                continue;
            }

            mappings.add(new MappingEntry(
                    seq++,
                    element.getDeIdentifier(),
                    element.getDeName(),
                    element.getDeDefinition(),
                    localIdentifier,
                    localName,
                    status,
                    ""  // 备注暂留空
            ));

            if (STATUS_MATCHED.equals(status)) {
                matchedCount++;
            }

            // 收集未匹配项
            if (STATUS_UNMATCHED.equals(status)) {
                missingEntries.add(new MissingEntry(
                        element.getDeIdentifier(),
                        element.getDeName(),
                        element.getDeDefinition(),
                        dataSet.getCode(),
                        dataSet.getName(),
                        "新建本地项"  // 默认建议行动
                ));
            }
        }

        // 计算匹配率和质量评分
        int filteredTotal = mappings.size();
        double matchRate = (filteredTotal > 0) ? (double) matchedCount / filteredTotal : 0.0;
        double qualityScore = calculateQualityScore(elements);

        return new DataSetReport(
                dataSet.getCode(),
                dataSet.getName(),
                mappings,
                missingEntries,
                matchRate,
                qualityScore
        );
    }

    /**
     * 解析数据元的匹配状态.
     *
     * <p>逻辑：
     * <ul>
     *   <li>如果 {@code mappedItemId} 非空且存在 EQUIVALENT 映射 → "已匹配"</li>
     *   <li>如果存在非 EQUIVALENT 的映射关系 → "待确认"</li>
     *   <li>其他 → "未匹配"</li>
     * </ul>
     */
    String resolveMatchStatus(InteropDataElement element) {
        if (element.getMappedItemId() != null) {
            // 检查是否有正式的映射关系
            List<ItemMapping> mappings = mappingRepository.findByItemId(element.getId());
            boolean hasEquivalent = mappings.stream()
                    .anyMatch(m -> m.getMappingType() == MappingType.EQUIVALENT);
            if (hasEquivalent) {
                return STATUS_MATCHED;
            }
            // 有 mappedItemId 但无 EQUIVALENT 映射 → 待确认
            return STATUS_PENDING;
        }

        // 检查是否有 ItemMapping 涉及此数据元
        List<ItemMapping> mappings = mappingRepository.findByItemId(element.getId());
        if (!mappings.isEmpty()) {
            // 有映射关系但无 mappedItemId → 待确认
            boolean hasDerivedOrSubdiv = mappings.stream()
                    .anyMatch(m -> m.getMappingType() == MappingType.DERIVED
                            || m.getMappingType() == MappingType.SUBDIVIDED);
            if (hasDerivedOrSubdiv) {
                return STATUS_PENDING;
            }
            return STATUS_UNMATCHED;
        }

        return STATUS_UNMATCHED;
    }

    /**
     * 解析本地对应元数据名称（MVP 简化：使用 mappedItemId 字符串）.
     */
    private String resolveLocalName(InteropDataElement element) {
        if (element.getMappedItemId() != null) {
            // MVP 简化：返回 mappedItemId 的简短表示
            // V1 可改为从 AdministeredItem 表查询名称
            return "本地项[" + element.getMappedItemId().toString().substring(0, 8) + "...]";
        }
        return "";
    }

    /**
     * 计算数据集的质量评分.
     *
     * <p>基于 WI-013-2 的对齐质量评分算法：
     * <ul>
     *   <li>MAPPED (完全匹配) → weight.exact</li>
     *   <li>CONFLICT (部分匹配) → weight.partial</li>
     *   <li>UNMAPPED (未匹配) → weight.none</li>
     * </ul>
     * 评分 = (sum of weights) / elementCount × 100
     */
    double calculateQualityScore(List<InteropDataElement> elements) {
        if (elements.isEmpty()) {
            return 100.0;
        }

        double totalWeight = 0.0;
        int weightedCount = 0;

        for (InteropDataElement element : elements) {
            String status = resolveMatchStatus(element);
            double weight;

            if (STATUS_MATCHED.equals(status)) {
                weight = scoreWeightConfig.getExact();
            } else if (STATUS_PENDING.equals(status)) {
                weight = scoreWeightConfig.getPartial();
            } else {
                weight = scoreWeightConfig.getNone();
            }

            totalWeight += weight;
            weightedCount++;
        }

        if (weightedCount == 0) {
            return 0.0;
        }

        // 归一化到百分制
        double maxWeight = scoreWeightConfig.getExact();
        double rawScore = totalWeight / (weightedCount * maxWeight);
        return Math.round(rawScore * 10000.0) / 100.0; // 保留两位小数
    }

    /**
     * 计算总体评分.
     */
    private OverallScore calculateOverallScore(List<DataSetReport> dataSetReports) {
        if (dataSetReports.isEmpty()) {
            return new OverallScore(0.0, List.of(), List.of("无可用数据集"));
        }

        double totalScore = 0.0;
        List<OverallScore.DataSetScore> scores = new ArrayList<>();

        for (DataSetReport ds : dataSetReports) {
            double score = ds.qualityScore();
            totalScore += score;
            scores.add(new OverallScore.DataSetScore(
                    ds.dataSetCode(), ds.dataSetName(), ds.matchRate(), score));
        }

        double overallScore = Math.round((totalScore / dataSetReports.size()) * 100.0) / 100.0;

        // 生成改进建议
        List<String> suggestions = generateSuggestions(dataSetReports);

        return new OverallScore(overallScore, scores, suggestions);
    }

    /**
     * 基于数据集评分生成改进建议.
     */
    List<String> generateSuggestions(List<DataSetReport> dataSetReports) {
        List<String> suggestions = new ArrayList<>();

        for (DataSetReport ds : dataSetReports) {
            double rate = ds.matchRate();
            if (rate < 0.3) {
                suggestions.add(String.format(
                        "%s（%s）匹配率较低（%.1f%%），建议补充本地元数据并重新执行对齐。",
                        ds.dataSetName(), ds.dataSetCode(), rate * 100));
            } else if (rate < 0.7) {
                suggestions.add(String.format(
                        "%s（%s）匹配率为 %.1f%%，建议检查待确认项并完善映射关系。",
                        ds.dataSetName(), ds.dataSetCode(), rate * 100));
            } else if (rate < 1.0) {
                suggestions.add(String.format(
                        "%s（%s）匹配率良好（%.1f%%），建议复核待确认项以提升至完全匹配。",
                        ds.dataSetName(), ds.dataSetCode(), rate * 100));
            }
        }

        if (suggestions.isEmpty()) {
            suggestions.add("所有数据集匹配率均为 100%，无需调整。");
        }

        return suggestions;
    }
}
