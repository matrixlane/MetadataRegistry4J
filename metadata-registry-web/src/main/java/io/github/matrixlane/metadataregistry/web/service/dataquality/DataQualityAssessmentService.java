package io.github.matrixlane.metadataregistry.web.service.dataquality;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.web.config.DataQualityProperties;
import io.github.matrixlane.metadataregistry.web.dto.dataquality.DataQualityAssessmentRequest;
import io.github.matrixlane.metadataregistry.web.dto.dataquality.DataQualityAssessmentResult;
import io.github.matrixlane.metadataregistry.web.dto.dataquality.DataQualityAssessmentResult.NullValueStat;
import io.github.matrixlane.metadataregistry.web.dto.dataquality.DataQualityAssessmentResult.DistributionStat;
import io.github.matrixlane.metadataregistry.web.dto.dataquality.DataQualityAssessmentResult.OutlierStat;
import io.github.matrixlane.metadataregistry.web.dto.dataquality.DataQualityAssessmentResult.SamplingInfo;
import io.github.matrixlane.metadataregistry.web.dto.dataquality.DataQualityAssessmentResult.Summary;
import io.github.matrixlane.metadataregistry.web.dto.dataquality.DataQualityAssessmentResult.TableInfo;
import io.github.matrixlane.metadataregistry.web.entity.DataQualityTask;
import io.github.matrixlane.metadataregistry.web.entity.DataQualityTaskStatus;
import io.github.matrixlane.metadataregistry.web.repository.DataQualityTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 数据质量初评服务.
 *
 * <p>评估遗留系统表的数据质量，包含空值率统计、数据分布分析和异常值检测。
 * 支持同步（小表）和异步（大表）两种执行模式。
 *
 * <p>遵循"轻量级初评"定位，评估结果通过 resultJson 关联存储，
 * 不持久化到 MDR 核心领域模型中。
 */
@Service
public class DataQualityAssessmentService {

    private static final Logger log = LoggerFactory.getLogger(DataQualityAssessmentService.class);

    private final DataQualityTaskRepository taskRepository;
    private final DataQualityProperties properties;
    private final ObjectMapper objectMapper;
    private final SamplingStrategy samplingStrategy;
    private final NullValueAnalyzer nullValueAnalyzer;
    private final DistributionAnalyzer distributionAnalyzer;
    private final OutlierDetector outlierDetector;

    public DataQualityAssessmentService(
            DataQualityTaskRepository taskRepository,
            DataQualityProperties properties,
            ObjectMapper objectMapper) {
        this.taskRepository = taskRepository;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.samplingStrategy = new SamplingStrategy(properties.getAutoFullScanThreshold());
        this.nullValueAnalyzer = new NullValueAnalyzer(properties.getDefaultNullThresholdPercent());
        this.distributionAnalyzer = new DistributionAnalyzer();
        this.outlierDetector = new OutlierDetector(properties.getDefaultOutlierThresholdPercent());
    }

    // ─────────────────────────────────────────────────────────────────
    // 同步评估（小表使用）
    // ─────────────────────────────────────────────────────────────────

    /**
     * 同步执行数据质量评估.
     *
     * <p>使用已构建的 JDBC executor 执行评估.
     *
     * @param executor JDBC 执行器（连接到目标数据库）
     * @param request  评估请求
     * @return 评估结果
     */
    public DataQualityAssessmentResult assess(JdbcDataQualityExecutor executor,
                                               DataQualityAssessmentRequest request) {
        log.info("Starting data quality assessment for {}.{}",
                request.getSchema(), request.getTableName());

        long startTime = System.currentTimeMillis();
        DataQualityAssessmentResult result = new DataQualityAssessmentResult();
        result.setAssessedAt(LocalDateTime.now());

        // 1. Check if table is empty
        long totalRows = executor.getExactRowCount(request.getSchema(), request.getTableName());
        TableInfo tableInfo = new TableInfo(request.getSchema(), request.getTableName(), totalRows);
        result.setTableInfo(tableInfo);

        if (totalRows == 0) {
            result.setEmptyTable(true);
            result.setSummary(new Summary(0, 0, 0, 0));
            log.info("Table {}.{} is empty, returning empty result", request.getSchema(), request.getTableName());
            return result;
        }

        // 2. Determine sampling strategy
        boolean useSampling = samplingStrategy.shouldSample(totalRows, request);
        long effectiveRows = samplingStrategy.effectiveRows(totalRows, useSampling, request);
        SamplingInfo samplingInfo = samplingStrategy.buildSamplingInfo(totalRows, useSampling, effectiveRows, request);
        result.setSamplingInfo(samplingInfo);

        // 3. Fetch column data from database
        Map<String, List<Object>> columnData = executor.fetchColumnData(
                request.getSchema(), request.getTableName(),
                request.getColumns(), useSampling, (int) effectiveRows);

        if (columnData.isEmpty()) {
            result.setEmptyTable(true);
            result.setSummary(new Summary(0, 0, 0, 0));
            return result;
        }

        // 4. Run analysis
        List<NullValueStat> nullStats = nullValueAnalyzer.analyze(columnData, null);
        List<DistributionStat> distStats = distributionAnalyzer.analyze(columnData, null);
        List<OutlierStat> outlierStats = outlierDetector.detect(columnData, null, null);

        result.setNullValueStats(nullStats);
        result.setDistributionStats(distStats);
        result.setOutlierStats(outlierStats);

        // 5. Build summary
        result.setSummary(buildSummary(nullStats, outlierStats));

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Data quality assessment completed for {}.{} in {}ms (sampled={}, rows={})",
                request.getSchema(), request.getTableName(), elapsed, useSampling, columnData.size());

        return result;
    }

    // ─────────────────────────────────────────────────────────────────
    // 异步评估（大表使用）
    // ─────────────────────────────────────────────────────────────────

    /**
     * 创建异步评估任务.
     *
     * @param request   评估请求
     * @param executor  JDBC 执行器
     * @param userId    创建者用户 ID
     * @return 创建的评估任务
     */
    @Transactional
    public DataQualityTask createAsyncAssessment(JdbcDataQualityExecutor executor,
                                                  DataQualityAssessmentRequest request,
                                                  UUID userId) {
        String requestJson = toJsonString(request);

        DataQualityTask task = DataQualityTask.builder()
                .status(DataQualityTaskStatus.PENDING)
                .progressPct(0)
                .stepName("pending")
                .tableName(request.getTableName())
                .schemaName(request.getSchema())
                .dataSourceKey(request.getDataSourceKey())
                .requestParams(requestJson)
                .createdAt(LocalDateTime.now())
                .createdBy(userId)
                .build();

        return taskRepository.save(task);
    }

    /**
     * 异步执行评估任务.
     *
     * @param taskId    任务 ID
     * @param executor  JDBC 执行器
     */
    @Async("dataQualityTaskExecutor")
    @Transactional
    public void executeAsyncAssessment(UUID taskId, JdbcDataQualityExecutor executor) {
        DataQualityTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("评估任务不存在: " + taskId));

        try {
            // Parse request
            DataQualityAssessmentRequest request = parseRequest(task.getRequestParams());

            // Update to running
            task.setStatus(DataQualityTaskStatus.RUNNING);
            task.setProgressPct(10);
            task.setStepName("fetching data");
            taskRepository.save(task);

            // Execute assessment
            DataQualityAssessmentResult result = assess(executor, request);

            // Store result
            task.setProgressPct(95);
            task.setStepName("saving results");
            task.setResultJson(toJsonString(result));
            task.setStatus(DataQualityTaskStatus.COMPLETED);
            task.setProgressPct(100);
            task.setStepName("completed");
            task.setCompletedAt(LocalDateTime.now());
            taskRepository.save(task);

            log.info("Async assessment {} completed for {}.{}", taskId,
                    request.getSchema(), request.getTableName());

        } catch (Exception e) {
            log.error("Async assessment {} failed", taskId, e);
            task.setStatus(DataQualityTaskStatus.FAILED);
            task.setErrorMessage(e.getMessage() != null ? e.getMessage() : e.getClass().getName());
            task.setProgressPct(0);
            task.setCompletedAt(LocalDateTime.now());
            taskRepository.save(task);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 任务状态查询
    // ─────────────────────────────────────────────────────────────────

    /**
     * 查询任务进度.
     */
    public DataQualityTask getTask(UUID taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("评估任务不存在: " + taskId));
    }

    /**
     * 获取用户的所有最近任务.
     */
    public List<DataQualityTask> getUserTasks(UUID userId) {
        return taskRepository.findByCreatedByOrderByCreatedAtDesc(userId);
    }

    // ─────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────

    private Summary buildSummary(List<NullValueStat> nullStats, List<OutlierStat> outlierStats) {
        int totalColumns = nullStats.size();
        long highNullColumns = nullStats.stream().filter(NullValueStat::isAboveThreshold).count();
        long highOutlierColumns = outlierStats.stream().filter(OutlierStat::isAboveThreshold).count();
        long emptyColumns = nullStats.stream()
                .filter(s -> s.getTotalCount() == 0)
                .count();

        return new Summary(totalColumns, (int) highNullColumns, (int) highOutlierColumns, (int) emptyColumns);
    }

    private String toJsonString(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("Failed to serialize to JSON, falling back to toString", e);
            return obj != null ? obj.toString() : "";
        }
    }

    private DataQualityAssessmentRequest parseRequest(String json) {
        try {
            return objectMapper.readValue(json, DataQualityAssessmentRequest.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("无法解析评估请求参数", e);
        }
    }
}
