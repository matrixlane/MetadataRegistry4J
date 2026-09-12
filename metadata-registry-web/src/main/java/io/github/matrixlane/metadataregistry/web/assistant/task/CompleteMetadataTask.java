package io.github.matrixlane.metadataregistry.web.assistant.task;

import io.github.matrixlane.metadataregistry.web.assistant.AiAssistSettings;
import io.github.matrixlane.metadataregistry.web.assistant.AiAssistSettingsService;
import io.github.matrixlane.metadataregistry.web.assistant.AssistantTask;
import io.github.matrixlane.metadataregistry.web.assistant.OutputKind;
import io.github.matrixlane.metadataregistry.web.assistant.TaskContext;
import io.github.matrixlane.metadataregistry.web.assistant.TaskInputSpec;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.inference.CandidateJsonExtractor;
import io.github.matrixlane.metadataregistry.web.inference.InferencePromptBuilder;
import io.github.matrixlane.metadataregistry.web.inference.dto.InferenceCandidate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * complete-metadata 任务：为注册条目补全缺失的定义/指称候选（CANDIDATES 输出）。
 *
 * <p>复用 {@link InferencePromptBuilder} 组装提示词（含既有元数据段落、写作规则与
 * JSON 输出契约）与 {@link CandidateJsonExtractor} 解析候选；每字段候选数取 AI 辅助
 * 设置（{@code ~/.metadata-registry/ai-assist.yml}，默认 3、范围 1-5），
 * 请求体中的数量参数一律忽略（非流程决定性选项前置到系统设置，见工作流流畅性原则）。
 * 既有 /api/v1/inference/** 端点经 InferenceJobService 兼容外观委托本任务。
 *
 * <p>草稿模式（ai-generate-everywhere D2）：无 itemId 时接受 {@code params.draft}
 * （条目类型 + 已填指称/定义行）组装「已知元数据」提示字段段；itemId 与 draft
 * 同给时以 itemId 为准。草稿是用户主动提供的内容，非缺省状态的降级，故不做
 * degraded 标注（与 selection-chat 的降级语义区分）。
 */
@Slf4j
@Component
public class CompleteMetadataTask implements AssistantTask {

    public static final String TASK_ID = "complete-metadata";

    private static final Set<String> ALLOWED_FIELDS = Set.of("definition", "designation");

    private final InferencePromptBuilder promptBuilder;
    private final CandidateJsonExtractor candidateJsonExtractor;
    private final AiAssistSettingsService assistSettingsService;

    public CompleteMetadataTask(InferencePromptBuilder promptBuilder,
                                CandidateJsonExtractor candidateJsonExtractor,
                                AiAssistSettingsService assistSettingsService) {
        this.promptBuilder = promptBuilder;
        this.candidateJsonExtractor = candidateJsonExtractor;
        this.assistSettingsService = assistSettingsService;
    }

    @Override
    public String id() {
        return TASK_ID;
    }

    @Override
    public String displayName() {
        return "补全元数据";
    }

    @Override
    public TaskInputSpec inputSpec() {
        // 草稿模式（ai-generate-everywhere D2）：itemId 可选，无 itemId 时经 params.draft 组装提示词
        return new TaskInputSpec(false, TaskInputSpec.SelectionRequirement.NONE,
                Set.of("fields", "language", "contextId", "draft"));
    }

    @Override
    public OutputKind outputKind() {
        return OutputKind.CANDIDATES;
    }

    @Override
    public void validate(TaskContext ctx) {
        List<String> fields = fieldsOf(ctx);
        if (fields.isEmpty()) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "fields 不能为空");
        }
        for (String field : fields) {
            if (!ALLOWED_FIELDS.contains(field)) {
                throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                        "不支持的推断字段：" + field + "，只允许 definition / designation");
            }
        }
        // 候选数不再接受请求参数：一律取 AI 辅助设置（前置配置，见设计 D3/D8）
        if (ctx.itemId() == null && !rawParams(ctx).containsKey("draft")) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "任务 " + TASK_ID + " 要求关联项目 itemId 或提供 draft 草稿");
        }
        if (ctx.itemId() == null && draftTextLines(ctx).isEmpty()) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "draft 草稿至少须要一行非空文本（指称或定义）");
        }
    }

    @Override
    public String buildPrompt(TaskContext ctx) {
        if (ctx.itemId() != null) {
            // itemId 优先：编辑态以库内数据为准，draft 忽略（D2）
            if (rawParams(ctx).containsKey("draft")) {
                log.debug("complete-metadata：itemId 与 draft 同给，按 itemId 组装提示词，忽略 draft");
            }
            return promptBuilder.build(ctx.itemId(), fieldsOf(ctx), ctx.stringParam("language"),
                    uuidParam(ctx, "contextId"), candidateCount());
        }
        return promptBuilder.buildDraft(normalizedDraft(ctx), fieldsOf(ctx),
                ctx.stringParam("language"), candidateCount());
    }    @Override
    public Object parseOutput(TaskContext ctx, String raw) {
        return capPerField(candidateJsonExtractor.extract(raw), candidateCount());
    }

    /** fields 参数：接受 List 或逗号分隔字符串。 */
    static List<String> fieldsOf(TaskContext ctx) {
        Object value = ctx.params().get("fields");
        if (value instanceof List<?> list) {
            return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        }
        if (value instanceof String s && !s.isBlank()) {
            List<String> fields = new ArrayList<>();
            for (String part : s.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    fields.add(trimmed);
                }
            }
            return fields;
        }
        return List.of();
    }

    /** 原始 params（TaskContext 已做非空保护；包内可见便于测试）。 */
    private static Map<String, Object> rawParams(TaskContext ctx) {
        return ctx.params();
    }

    /**
     * 草稿的有效文本行（指称/定义，value 为非空字符串的行；非法行跳过）。
     * 仅 itemType 而无任何文本行时视为空草稿（D2：至少一个非空文本行）。
     */
    static List<Map<String, String>> draftTextLines(TaskContext ctx) {
        List<Map<String, String>> lines = new ArrayList<>();
        for (String key : List.of("designations", "definitions")) {
            Object value = draftMap(ctx).get(key);
            if (!(value instanceof List<?> list)) {
                continue;
            }
            for (Object element : list) {
                if (!(element instanceof Map<?, ?> row)) {
                    continue;
                }
                Object text = row.get("value");
                if (!(text instanceof String s) || s.isBlank()) {
                    continue;
                }
                Map<String, String> line = new LinkedHashMap<>();
                line.put("field", key);
                Object language = row.get("language");
                line.put("language", language instanceof String l && !l.isBlank() ? l : null);
                line.put("value", s.trim());
                lines.add(line);
            }
        }
        return lines;
    }

    /** 草稿原始 Map；缺失或非法形状时按空 Map 容错（由校验/组装处决定语义）。 */
    private static Map<String, Object> draftMap(TaskContext ctx) {
        Object draft = ctx.params().get("draft");
        return draft instanceof Map<?, ?> map
                ? map.entrySet().stream().filter(e -> e.getKey() instanceof String)
                        .collect(java.util.stream.Collectors.toMap(
                                e -> (String) e.getKey(), Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new))
                : Map.of();
    }

    /** 规范化草稿：{itemType?, designations?, definitions?}，供 InferencePromptBuilder.buildDraft。 */
    private static Map<String, Object> normalizedDraft(TaskContext ctx) {
        Map<String, Object> draft = draftMap(ctx);
        Map<String, Object> normalized = new LinkedHashMap<>();
        Object itemType = draft.get("itemType");
        if (itemType instanceof String s && !s.isBlank()) {
            normalized.put("itemType", s.trim());
        }
        for (String key : List.of("designations", "definitions")) {
            List<Map<String, String>> rows = new ArrayList<>();
            for (Map<String, String> line : draftTextLines(ctx)) {
                if (line.get("field").equals(key)) {
                    Map<String, String> row = new LinkedHashMap<>();
                    row.put("value", line.get("value"));
                    if (line.get("language") != null) {
                        row.put("language", line.get("language"));
                    }
                    rows.add(row);
                }
            }
            if (!rows.isEmpty()) {
                normalized.put(key, rows);
            }
        }
        if (normalized.isEmpty()) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "draft 草稿至少须要一行非空文本（指称或定义）");
        }
        return normalized;
    }

    /** 候选数：取 AI 辅助设置（1-5，默认 3）；请求体传入的数量参数被忽略。 */
    private int candidateCount() {
        int configured = assistSettingsService.candidateCount();
        return Math.min(AiAssistSettings.MAX_CANDIDATE_COUNT,
                Math.max(AiAssistSettings.MIN_CANDIDATE_COUNT, configured));
    }

    private static UUID uuidParam(TaskContext ctx, String key) {
        String value = ctx.stringParam(key);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, key + " 不是合法的项目标识：" + value);
        }
    }

    /** 解析结果按字段裁剪到候选数（spec：每字段候选数不超过 N）。 */
    private List<InferenceCandidate> capPerField(List<InferenceCandidate> candidates, int candidateCount) {
        Map<String, Integer> counts = new ConcurrentHashMap<>();
        List<InferenceCandidate> capped = new ArrayList<>();
        for (InferenceCandidate candidate : candidates) {
            int seen = counts.merge(candidate.field() == null ? "" : candidate.field(), 1, Integer::sum);
            if (seen <= candidateCount) {
                capped.add(candidate);
            }
        }
        return capped;
    }
}
