package io.github.matrixlane.metadataregistry.web.assistant.task;

import io.github.matrixlane.metadataregistry.web.assistant.AssistantTask;
import io.github.matrixlane.metadataregistry.web.assistant.OutputKind;
import io.github.matrixlane.metadataregistry.web.assistant.Selection;
import io.github.matrixlane.metadataregistry.web.assistant.TaskContext;
import io.github.matrixlane.metadataregistry.web.assistant.TaskInputSpec;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.inference.InferencePromptBuilder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * selection-chat 任务：以划选内容为引用上下文的多轮问答（TEXT 输出）。
 *
 * <p>selection.text 必填非空且不超过 2000 字符；params.question 必填非空（≤2000）；
 * params.history 为可选的历史问答 [{question, answer}]，仅最近 {@value #HISTORY_LIMIT} 轮
 * 注入提示词（服务端不存会话，多轮由客户端随请求重注入）。有 itemId 时注入项目上下文
 * 摘要（复用 {@link InferencePromptBuilder} 的上下文段）；无 itemId 降级为通用语境回答
 * 并标注。TEXT 清洗复用 {@link ExplainSelectionTask#clean} 的惯例。
 */
@Component
public class SelectionChatTask implements AssistantTask {

    public static final String TASK_ID = "selection-chat";

    /** 选段原文长度上限。 */
    static final int SELECTION_TEXT_LIMIT = 2000;

    /** 新问题长度上限。 */
    static final int QUESTION_LIMIT = 2000;

    /** 所在完整文本注入提示词的截断上限。 */
    static final int SURROUNDING_TEXT_LIMIT = 2000;

    /** 注入提示词的历史问答轮数上限。 */
    static final int HISTORY_LIMIT = 10;

    private final InferencePromptBuilder promptBuilder;

    public SelectionChatTask(InferencePromptBuilder promptBuilder) {
        this.promptBuilder = promptBuilder;
    }

    @Override
    public String id() {
        return TASK_ID;
    }

    @Override
    public String displayName() {
        return "划词问答";
    }

    @Override
    public TaskInputSpec inputSpec() {
        return new TaskInputSpec(false, TaskInputSpec.SelectionRequirement.REQUIRED,
                Set.of("question", "history"));
    }

    @Override
    public OutputKind outputKind() {
        return OutputKind.TEXT;
    }

    @Override
    public void validate(TaskContext ctx) {
        Selection selection = ctx.selection();
        if (selection == null || selection.text() == null || selection.text().isBlank()) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "selection-chat 任务要求 selection.text 非空");
        }
        if (selection.text().length() > SELECTION_TEXT_LIMIT) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "selection.text 长度超过 " + SELECTION_TEXT_LIMIT + " 字符上限");
        }
        String question = ctx.stringParam("question");
        if (question == null || question.isBlank()) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "selection-chat 任务要求 params.question 非空");
        }
        if (question.length() > QUESTION_LIMIT) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "params.question 长度超过 " + QUESTION_LIMIT + " 字符上限");
        }
    }

    @Override
    public boolean degraded(TaskContext ctx) {
        // 未关联注册条目：降级为通用语境回答
        return ctx.itemId() == null;
    }

    @Override
    public String buildPrompt(TaskContext ctx) {
        Selection selection = ctx.selection();
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是元数据注册系统（遵循 ISO/IEC 11179 系列）的数据治理助手。"
                + "用户在一则注册相关文本上划选了一段内容作为引用，并就此提问；"
                + "请结合引用原文与对话历史用简体中文回答。\n");

        prompt.append("\n【引用原文】\n");
        prompt.append("- 所在字段：")
                .append(selection.field() == null || selection.field().isBlank() ? "未指定" : selection.field())
                .append('\n');
        prompt.append("- 选段原文：").append(selection.text()).append('\n');
        if (selection.language() != null && !selection.language().isBlank()) {
            prompt.append("- 选段语种：").append(selection.language()).append('\n');
        }
        if (selection.surroundingText() != null && !selection.surroundingText().isBlank()) {
            prompt.append("- 所在完整文本（超长已截断）：")
                    .append(truncate(selection.surroundingText(), SURROUNDING_TEXT_LIMIT)).append('\n');
        }

        prompt.append("\n【关联注册条目上下文】\n");
        if (ctx.itemId() != null) {
            prompt.append(promptBuilder.buildItemContextSection(ctx.itemId()));
        } else {
            prompt.append("（未关联注册条目）请基于通用数据治理语境回答，并在回答开头明确这是"
                    + "未关联注册条目的通用回答。\n");
        }

        List<QaTurn> history = historyOf(ctx);
        if (!history.isEmpty()) {
            prompt.append("\n【历史问答】\n");
            history.forEach(turn -> prompt.append("问：").append(turn.question())
                    .append("\n答：").append(turn.answer()).append('\n'));
        }

        prompt.append("\n【新问题】\n").append(ctx.stringParam("question")).append('\n');

        prompt.append("\n【输出要求】\n");
        prompt.append("- 使用简体中文，单段回答（约 100-400 字），不使用 Markdown 格式或代码块。\n");
        prompt.append("- 回答须结合引用原文与历史问答；判断依据优先引用关联条目的既有元数据（若已提供）。\n");
        prompt.append("- 未关联注册条目时，明确说明这是通用语境回答。\n");
        return prompt.toString();
    }

    @Override
    public Object parseOutput(TaskContext ctx, String raw) {
        return ExplainSelectionTask.clean(raw);
    }

    /** history 参数：仅取最近 HISTORY_LIMIT 轮结构合法（question/answer 均为字符串）的问答。 */
    static List<QaTurn> historyOf(TaskContext ctx) {
        Object value = ctx.params().get("history");
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<QaTurn> turns = new ArrayList<>();
        for (Object element : list) {
            if (element instanceof Map<?, ?> turn
                    && turn.get("question") instanceof String question
                    && turn.get("answer") instanceof String answer) {
                turns.add(new QaTurn(question, answer));
            }
        }
        return turns.size() <= HISTORY_LIMIT
                ? List.copyOf(turns)
                : List.copyOf(turns.subList(turns.size() - HISTORY_LIMIT, turns.size()));
    }

    /** 一轮历史问答。 */
    record QaTurn(String question, String answer) {
    }

    private static String truncate(String text, int limit) {
        return text.length() <= limit ? text : text.substring(0, limit) + "…（已截断）";
    }
}
