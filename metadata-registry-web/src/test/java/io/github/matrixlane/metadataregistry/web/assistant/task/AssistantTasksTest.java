package io.github.matrixlane.metadataregistry.web.assistant.task;

import io.github.matrixlane.metadataregistry.web.assistant.AiAssistSettings;
import io.github.matrixlane.metadataregistry.web.assistant.AiAssistSettingsService;
import io.github.matrixlane.metadataregistry.web.assistant.OutputKind;
import io.github.matrixlane.metadataregistry.web.assistant.Selection;
import io.github.matrixlane.metadataregistry.web.assistant.TaskContext;
import io.github.matrixlane.metadataregistry.web.assistant.TaskInputSpec;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.inference.CandidateJsonExtractor;
import io.github.matrixlane.metadataregistry.web.inference.InferencePromptBuilder;
import io.github.matrixlane.metadataregistry.web.inference.dto.InferenceCandidate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** complete-metadata 与 explain-selection 任务实现的输入校验、提示词组装与输出解析。 */
@ExtendWith(MockitoExtension.class)
class AssistantTasksTest {

    @Mock
    private InferencePromptBuilder promptBuilder;

    @TempDir
    Path tempDir;

    private AiAssistSettingsService settingsService() {
        return new AiAssistSettingsService(tempDir.resolve("ai-assist.yml"));
    }

    private CompleteMetadataTask completeTask() {
        return new CompleteMetadataTask(promptBuilder, new CandidateJsonExtractor(), settingsService());
    }

    private ExplainSelectionTask explainTask() {
        return new ExplainSelectionTask(promptBuilder);
    }

    private TaskContext ctx(UUID itemId, Selection selection, Map<String, Object> params) {
        return new TaskContext(itemId, selection, params, "alice");
    }

    // ---------- complete-metadata ----------

    @Test
    void completeMetadata_spec_isCandidatesAndItemOptional() {
        CompleteMetadataTask task = completeTask();

        assertThat(task.id()).isEqualTo("complete-metadata");
        assertThat(task.outputKind()).isEqualTo(OutputKind.CANDIDATES);
        // 草稿模式（ai-generate-everywhere D2）：itemId 可选，无 itemId 时以 draft 组装提示词
        assertThat(task.inputSpec().itemIdRequired()).isFalse();
        assertThat(task.inputSpec().selection()).isEqualTo(TaskInputSpec.SelectionRequirement.NONE);
    }

    @Test
    void completeMetadata_validate_rejectsEmptyAndUnknownFields() {
        CompleteMetadataTask task = completeTask();
        UUID itemId = UUID.randomUUID();

        assertThatThrownBy(() -> task.validate(ctx(itemId, null, Map.of())))
                .isInstanceOf(BusinessException.class).hasMessageContaining("fields 不能为空");
        assertThatThrownBy(() -> task.validate(ctx(itemId, null, Map.of("fields", List.of("name")))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("definition / designation");
    }

    @Test
    void completeMetadata_buildPrompt_usesSettingsCandidateCount_andIgnoresRequestParam() {
        AiAssistSettingsService settingsService = settingsService();
        settingsService.save(new AiAssistSettings(AiAssistSettings.FORMAT_VERSION, 2, null));
        CompleteMetadataTask task = new CompleteMetadataTask(promptBuilder, new CandidateJsonExtractor(), settingsService);
        UUID itemId = UUID.randomUUID();
        lenient().when(promptBuilder.build(eq(itemId), anyList(), isNull(), isNull(), anyInt()))
                .thenReturn("prompt");

        // 请求 params 带 candidateCount=10 也被忽略，注入的是设置值 2
        task.buildPrompt(ctx(itemId, null, Map.of("fields", List.of("definition"), "candidateCount", 10)));

        org.mockito.ArgumentCaptor<Integer> captor = org.mockito.ArgumentCaptor.forClass(Integer.class);
        org.mockito.Mockito.verify(promptBuilder).build(eq(itemId), anyList(), isNull(), isNull(), captor.capture());
        assertThat(captor.getValue()).isEqualTo(2);
    }

    @Test
    void completeMetadata_parseOutput_capsPerFieldAtSettingsCount() {
        AiAssistSettingsService settingsService = settingsService();
        settingsService.save(new AiAssistSettings(AiAssistSettings.FORMAT_VERSION, 2, null));
        CompleteMetadataTask task = new CompleteMetadataTask(promptBuilder, new CandidateJsonExtractor(), settingsService);
        UUID itemId = UUID.randomUUID();
        String raw = "{\"candidates\":["
                + "{\"field\":\"definition\",\"text\":\"d1\"},"
                + "{\"field\":\"definition\",\"text\":\"d2\"},"
                + "{\"field\":\"definition\",\"text\":\"d3\"},"
                + "{\"field\":\"definition\",\"text\":\"d4\"}]}";

        @SuppressWarnings("unchecked")
        List<InferenceCandidate> result = (List<InferenceCandidate>) task.parseOutput(
                ctx(itemId, null, Map.of("fields", List.of("definition"))), raw);

        assertThat(result).hasSize(2);
    }

    @Test
    void completeMetadata_parseOutput_capsPerField() {
        CompleteMetadataTask task = completeTask();
        UUID itemId = UUID.randomUUID();
        String raw = "{\"candidates\":["
                + "{\"field\":\"definition\",\"text\":\"d1\"},"
                + "{\"field\":\"definition\",\"text\":\"d2\"},"
                + "{\"field\":\"definition\",\"text\":\"d3\"},"
                + "{\"field\":\"definition\",\"text\":\"d4\"}]}";

        @SuppressWarnings("unchecked")
        List<InferenceCandidate> result = (List<InferenceCandidate>) task.parseOutput(
                ctx(itemId, null, Map.of("fields", List.of("definition"))), raw);

        assertThat(result).hasSize(3);
    }

    // ---------- complete-metadata 草稿模式（ai-generate-everywhere D2） ----------

    @Test
    void completeMetadata_spec_itemIdOptional_andDeclaresDraftParam() {
        CompleteMetadataTask task = completeTask();

        assertThat(task.inputSpec().itemIdRequired()).isFalse();
        assertThat(task.inputSpec().params()).contains("fields", "language", "contextId", "draft");
    }

    @Test
    void completeMetadata_validate_withoutItemId_requiresDraft() {
        CompleteMetadataTask task = completeTask();

        // 无 itemId 且无 draft
        assertThatThrownBy(() -> task.validate(ctx(null, null, Map.of("fields", List.of("definition")))))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(ApiCodes.VALIDATION_FAILED))
                .hasMessageContaining("itemId");
        // draft 非 Map（容错按缺失处理）
        assertThatThrownBy(() -> task.validate(ctx(null, null,
                Map.of("fields", List.of("definition"), "draft", "not-a-map"))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void completeMetadata_validate_draftNeedsAtLeastOneNonBlankTextLine() {
        CompleteMetadataTask task = completeTask();

        // 全部文本行为空
        assertThatThrownBy(() -> task.validate(ctx(null, null, Map.of("fields", List.of("definition"),
                "draft", Map.of("designations", List.of(Map.of("value", "  ")))))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("非空文本");
        // 仅 itemType、无指称/定义行：不足以生成
        assertThatThrownBy(() -> task.validate(ctx(null, null, Map.of("fields", List.of("definition"),
                "draft", Map.of("itemType", "DataElement")))))
                .isInstanceOf(BusinessException.class);
        // 非法行跳过后仍有有效行：通过
        Map<String, Object> draft = Map.of(
                "itemType", "DataElement",
                "designations", List.of(
                        "illegal-row",
                        Map.of("value", 42),
                        Map.of("language", "zh-CN", "value", "接收标本日期时间")),
                "definitions", List.of(Map.of()));
        task.validate(ctx(null, null, Map.of("fields", List.of("definition"), "draft", draft)));
    }

    @Test
    void completeMetadata_buildPrompt_itemIdPreferred_overDraft() {
        CompleteMetadataTask task = completeTask();
        UUID itemId = UUID.randomUUID();
        Map<String, Object> draft = Map.of("designations", List.of(Map.of("value", "草稿指称")));
        lenient().when(promptBuilder.build(eq(itemId), anyList(), isNull(), isNull(), anyInt()))
                .thenReturn("prompt");

        String prompt = task.buildPrompt(ctx(itemId, null,
                Map.of("fields", List.of("definition"), "draft", draft)));

        assertThat(prompt).isEqualTo("prompt");
        org.mockito.Mockito.verify(promptBuilder)
                .build(eq(itemId), anyList(), isNull(), isNull(), anyInt());
        org.mockito.Mockito.verify(promptBuilder, org.mockito.Mockito.never())
                .buildDraft(any(), anyList(), isNull(), anyInt());
    }

    @Test
    void completeMetadata_buildPrompt_draftOnly_delegatesToBuildDraft() {
        CompleteMetadataTask task = completeTask();
        Map<String, Object> draft = Map.of(
                "itemType", "DataElement",
                "designations", List.of(Map.of("language", "zh-CN", "value", "接收标本日期时间")));
        lenient().when(promptBuilder.buildDraft(any(), anyList(), isNull(), anyInt()))
                .thenReturn("draft-prompt");

        String prompt = task.buildPrompt(ctx(null, null,
                Map.of("fields", List.of("definition"), "draft", draft)));

        assertThat(prompt).isEqualTo("draft-prompt");
        org.mockito.ArgumentCaptor<Map<String, Object>> captor =
                org.mockito.ArgumentCaptor.forClass(Map.class);
        org.mockito.Mockito.verify(promptBuilder)
                .buildDraft(captor.capture(), anyList(), isNull(), anyInt());
        Map<String, Object> passed = captor.getValue();
        assertThat(passed.get("itemType")).isEqualTo("DataElement");
        assertThat(passed.get("designations")).isInstanceOf(List.class);
    }

    @Test
    void completeMetadata_buildPrompt_draftOnly_withoutValidate_rejectsEmptyDraft() {
        CompleteMetadataTask task = completeTask();

        assertThatThrownBy(() -> task.buildPrompt(ctx(null, null,
                Map.of("fields", List.of("definition"), "draft", Map.of("designations", List.of())))))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(ApiCodes.VALIDATION_FAILED));
    }

    // ---------- explain-selection ----------

    @Test
    void explainSelection_spec_isTextAndRequiresSelection() {
        ExplainSelectionTask task = explainTask();

        assertThat(task.id()).isEqualTo("explain-selection");
        assertThat(task.outputKind()).isEqualTo(OutputKind.TEXT);
        assertThat(task.inputSpec().itemIdRequired()).isFalse();
        assertThat(task.inputSpec().selection()).isEqualTo(TaskInputSpec.SelectionRequirement.REQUIRED);
    }

    @Test
    void explainSelection_validate_rejectsMissingBlankAndOverlongText() {
        ExplainSelectionTask task = explainTask();
        UUID itemId = UUID.randomUUID();

        assertThatThrownBy(() -> task.validate(ctx(itemId, null, Map.of())))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(ApiCodes.VALIDATION_FAILED));
        assertThatThrownBy(() -> task.validate(ctx(itemId, new Selection("definition", "  ", null, null, null, null), Map.of())))
                .isInstanceOf(BusinessException.class).hasMessageContaining("非空");
        String overlong = "x".repeat(2001);
        assertThatThrownBy(() -> task.validate(ctx(itemId, new Selection("definition", overlong, null, null, null, null), Map.of())))
                .isInstanceOf(BusinessException.class).hasMessageContaining("2000");
    }

    @Test
    void explainSelection_buildPrompt_withItem_includesSelectionContextAndConcepts() {
        ExplainSelectionTask task = explainTask();
        UUID itemId = UUID.randomUUID();
        when(promptBuilder.buildItemContextSection(itemId)).thenReturn("【已知元数据】…");
        Selection selection = new Selection("definition", "患者的唯一标识", "患者主索引的唯一标识符", "zh-CN", 3, 9);

        String prompt = task.buildPrompt(ctx(itemId, selection, Map.of("question", "为何这样定义？")));

        assertThat(prompt).contains("患者的唯一标识", "患者主索引的唯一标识符", "definition", "zh-CN");
        assertThat(prompt).contains("【已知元数据】…", "为何这样定义？");
        // ISO/IEC 11179 概念体系术语在，杜撰的条款编号不在
        assertThat(prompt).contains("data element", "designation", "definition", "context");
        assertThat(prompt).doesNotContain("§", "11179-3:", "11179-4:", "11179-5:");
        assertThat(task.degraded(ctx(itemId, selection, Map.of()))).isFalse();
    }

    @Test
    void explainSelection_buildPrompt_withoutItem_degradesToGeneric() {
        ExplainSelectionTask task = explainTask();
        Selection selection = new Selection(null, "permissible value", null, null, null, null);

        String prompt = task.buildPrompt(ctx(null, selection, Map.of()));

        assertThat(prompt).contains("permissible value", "未关联注册条目", "通用");
        assertThat(task.degraded(ctx(null, selection, Map.of()))).isTrue();
    }

    @Test
    void explainSelection_parseOutput_cleansFencesAndBlankLines() {
        ExplainSelectionTask task = explainTask();

        Object result = task.parseOutput(ctx(null,
                new Selection(null, "x", null, null, null, null), Map.of()),
                "```text\n  这是解释。\n\n\n\n第二段。 ```\n");

        assertThat(result).isEqualTo("这是解释。\n\n第二段。");
    }

    // ---------- selection-chat ----------

    private SelectionChatTask chatTask() {
        return new SelectionChatTask(promptBuilder);
    }

    @Test
    void selectionChat_spec_isTextAndRequiresSelection() {
        SelectionChatTask task = chatTask();

        assertThat(task.id()).isEqualTo("selection-chat");
        assertThat(task.displayName()).isEqualTo("划词问答");
        assertThat(task.outputKind()).isEqualTo(OutputKind.TEXT);
        assertThat(task.inputSpec().itemIdRequired()).isFalse();
        assertThat(task.inputSpec().selection()).isEqualTo(TaskInputSpec.SelectionRequirement.REQUIRED);
        assertThat(task.inputSpec().params()).contains("question", "history");
    }

    @Test
    void selectionChat_validate_rejectsMissingBlankOverlong() {
        SelectionChatTask task = chatTask();
        UUID itemId = UUID.randomUUID();

        assertThatThrownBy(() -> task.validate(ctx(itemId, null, Map.of("question", "q"))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("selection.text");
        assertThatThrownBy(() -> task.validate(ctx(itemId,
                new Selection(null, "  ", null, null, null, null), Map.of("question", "q"))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("非空");
        assertThatThrownBy(() -> task.validate(ctx(itemId,
                new Selection(null, "x".repeat(2001), null, null, null, null), Map.of("question", "q"))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("2000");
        Selection ok = new Selection(null, "text", null, null, null, null);
        assertThatThrownBy(() -> task.validate(ctx(itemId, ok, Map.of())))
                .isInstanceOf(BusinessException.class).hasMessageContaining("question");
        assertThatThrownBy(() -> task.validate(ctx(itemId, ok, Map.of("question", " "))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("question");
        assertThatThrownBy(() -> task.validate(ctx(itemId, ok, Map.of("question", "x".repeat(2001)))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("2000");
    }

    @Test
    void selectionChat_buildPrompt_withItem_includesContextHistoryQuestion() {
        SelectionChatTask task = chatTask();
        UUID itemId = UUID.randomUUID();
        when(promptBuilder.buildItemContextSection(itemId)).thenReturn("【已知元数据】…");
        Selection selection = new Selection("definition", "患者主索引", "患者主索引的唯一标识符", "zh-CN", 0, 4);
        List<Map<String, String>> history = List.of(
                Map.of("question", "第一问", "answer", "第一答"),
                Map.of("question", "第二问", "answer", "第二答"));

        String prompt = task.buildPrompt(ctx(itemId, selection, Map.of("question", "现在问什么？", "history", history)));

        assertThat(prompt).contains("患者主索引", "患者主索引的唯一标识符", "【已知元数据】…");
        assertThat(prompt).contains("问：第一问", "答：第一答", "问：第二问", "答：第二答");
        assertThat(prompt).contains("现在问什么？");
        assertThat(task.degraded(ctx(itemId, selection, Map.of("question", "q")))).isFalse();
    }

    @Test
    void selectionChat_buildPrompt_injectsOnlyLast10Turns() {
        SelectionChatTask task = chatTask();
        List<Map<String, String>> history = new java.util.ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            history.add(Map.of("question", String.format("问%02d", i), "answer", String.format("答%02d", i)));
        }
        Selection selection = new Selection(null, "选段", null, null, null, null);

        String prompt = task.buildPrompt(ctx(null, selection, Map.of("question", "q", "history", history)));

        assertThat(prompt).contains("问：问03", "问：问12");
        assertThat(prompt).doesNotContain("问：问01", "问：问02");
        assertThat(task.degraded(ctx(null, selection, Map.of("question", "q")))).isTrue();
    }

    @Test
    void selectionChat_parseOutput_cleansLikeExplainSelection() {
        SelectionChatTask task = chatTask();
        Object result = task.parseOutput(ctx(null,
                new Selection(null, "x", null, null, null, null), Map.of("question", "q")),
                "```text\n  这是回答。\n\n\n\n第二段。 ```\n");
        assertThat(result).isEqualTo("这是回答。\n\n第二段。");
    }
}
