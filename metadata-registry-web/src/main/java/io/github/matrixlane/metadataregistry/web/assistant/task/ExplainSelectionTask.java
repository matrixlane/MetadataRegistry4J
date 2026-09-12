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

import java.util.Set;

/**
 * explain-selection 任务：结合关联注册条目的既有元数据与 ISO/IEC 11179 概念体系，
 * 以简体中文解释用户划选文本的含义与依据（TEXT 输出）。
 *
 * <p>selection.text 必填非空且不超过 2000 字符；surroundingText 注入提示词时按上限
 * 截断；起止偏移不进提示词（仅供前端溯源展示）。未关联项目时降级为通用术语文境
 * 解释（{@link #degraded} 返回 true，响应中标注）。提示词只引用 ISO/IEC 11179-1
 * 的概念体系术语，不杜撰具体条款编号。
 */
@Component
public class ExplainSelectionTask implements AssistantTask {

    public static final String TASK_ID = "explain-selection";

    /** 选段原文长度上限。 */
    static final int SELECTION_TEXT_LIMIT = 2000;

    /** 所在完整文本注入提示词的截断上限。 */
    static final int SURROUNDING_TEXT_LIMIT = 2000;

    private final InferencePromptBuilder promptBuilder;

    public ExplainSelectionTask(InferencePromptBuilder promptBuilder) {
        this.promptBuilder = promptBuilder;
    }

    @Override
    public String id() {
        return TASK_ID;
    }

    @Override
    public String displayName() {
        return "解释所选文本";
    }

    @Override
    public TaskInputSpec inputSpec() {
        return new TaskInputSpec(false, TaskInputSpec.SelectionRequirement.REQUIRED, Set.of("question"));
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
                    "explain-selection 任务要求 selection.text 非空");
        }
        if (selection.text().length() > SELECTION_TEXT_LIMIT) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "selection.text 长度超过 " + SELECTION_TEXT_LIMIT + " 字符上限");
        }
    }

    @Override
    public boolean degraded(TaskContext ctx) {
        // 未关联注册条目：降级为通用术语文境解释
        return ctx.itemId() == null;
    }

    @Override
    public String buildPrompt(TaskContext ctx) {
        Selection selection = ctx.selection();
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是元数据注册系统（遵循 ISO/IEC 11179 系列）的数据治理助手。"
                + "请用简体中文解释用户在注册条目文本上划选的一段内容，说明其含义、在元数据治理中的"
                + "作用，以及判断依据。\n");

        prompt.append("\n【划选内容】\n");
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
            prompt.append("（未关联注册条目）请给出面向通用数据治理语境的术语解释，说明该表述的一般含义、"
                    + "常见使用场景与注意事项，并在解释开头明确这是未关联注册条目的通用解释。\n");
        }

        prompt.append("\n【ISO/IEC 11179 概念要点】\n");
        prompt.append("- data element（数据元）：以定义、标识、表示与允许值描述的数据单元，是注册系统的核心条目。\n");
        prompt.append("- designation（指称）与 definition（定义）：指称是给数据元的名称，定义是表述其含义的陈述句，"
                + "二者共同保证单义理解。\n");
        prompt.append("- context（语境）与 context of designation（指称语境）：同一指称在不同应用场景下可能有"
                + "不同含义，语境用于限定其适用范围。\n");
        prompt.append("- conceptual domain / value domain（概念域 / 值域）：概念域表达数据元的含义空间，"
                + "值域给出允许的取值集合。\n");

        String question = ctx.stringParam("question");
        if (question != null) {
            prompt.append("\n【用户补充问题】\n").append(question).append('\n');
        }

        prompt.append("\n【输出要求】\n");
        prompt.append("- 使用简体中文，单段解释（约 200-500 字），不使用 Markdown 格式或代码块。\n");
        prompt.append("- 解释须结合上方条目上下文（若已提供）；判断依据优先引用该条目的既有元数据。\n");
        prompt.append("- 未关联注册条目时，明确说明这是通用语境解释。\n");
        return prompt.toString();
    }

    @Override
    public Object parseOutput(TaskContext ctx, String raw) {
        return clean(raw);
    }

    /** TEXT 轻量清洗：去代码围栏、修剪首尾空白、压缩连续空行。 */
    static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();
        if (text.startsWith("```") && text.endsWith("```") && text.length() >= 6) {
            text = text.substring(3, text.length() - 3).trim();
            int newline = text.indexOf('\n');
            if (newline >= 0 && text.substring(0, newline).chars().allMatch(ch -> ch == '`' || Character.isLetter(ch))) {
                text = text.substring(newline + 1).trim();
            }
        }
        return text.replaceAll("\\n{3,}", "\n\n");
    }

    private static String truncate(String text, int limit) {
        return text.length() <= limit ? text : text.substring(0, limit) + "…（已截断）";
    }
}
