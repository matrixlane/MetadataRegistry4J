package io.github.matrixlane.metadataregistry.web.inference;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link InferencePromptBuilder#buildDraft} 草稿模式提示词组装：
 * 已知元数据段由表单草稿组装（空段省略、单值截断），写作规则、推断任务段与
 * JSON 输出契约与库内模式完全一致（ai-generate-everywhere D2）。
 */
class InferencePromptBuilderDraftTest {

    /** buildDraft 不读库：Repository/Service 依赖传 null 即为证。 */
    private InferencePromptBuilder builder() {
        return new InferencePromptBuilder(null, null, null);
    }

    @Test
    void buildDraft_includesItemTypeDesignationsAndDefinitions() {
        Map<String, Object> draft = Map.of(
                "itemType", "DataElement",
                "designations", List.of(
                        Map.of("language", "zh-CN", "value", "接收标本日期时间"),
                        Map.of("value", "specimen receipt datetime")),
                "definitions", List.of(Map.of("language", "zh-CN", "value", "标本被接收的时间。")));

        String prompt = builder().buildDraft(draft, List.of("definition"), "zh-CN", 3);

        assertThat(prompt).contains("- 条目类型：DataElement");
        assertThat(prompt).contains("接收标本日期时间");
        assertThat(prompt).contains("specimen receipt datetime");
        assertThat(prompt).contains("标本被接收的时间。");
        // 语种标注：缺省语种显示未知
        assertThat(prompt).contains("（语种: zh-CN）");
        assertThat(prompt).contains("（语种: 未知）");
    }

    @Test
    void buildDraft_omitsEmptySections_andBlankItemType() {
        Map<String, Object> draft = Map.of(
                "itemType", " ",
                "definitions", List.of(Map.of("value", "只有定义。")));

        String prompt = builder().buildDraft(draft, List.of("definition"), null, 3);

        assertThat(prompt).doesNotContain("条目类型：");
        assertThat(prompt).doesNotContain("指称（designation）");
        assertThat(prompt).contains("定义（definition）");
        // 空草稿（无 itemType/指称/定义）也只省略空段，不抛异常
        String empty = builder().buildDraft(Map.of(), List.of("definition"), "zh-CN", 3);
        assertThat(empty).contains("【已知元数据】");
        assertThat(empty).doesNotContain("条目类型");
    }

    @Test
    void buildDraft_taskRulesAndContract_matchItemBasedPrompt() {
        Map<String, Object> draft = Map.of(
                "designations", List.of(Map.of("value", "名称")));

        String draftPrompt = builder().buildDraft(draft, List.of("definition", "designation"), "zh-CN", 3);

        // 任务段：字段、语种、候选数上限
        assertThat(draftPrompt).contains("【推断任务】");
        assertThat(draftPrompt).contains("推断「定义」（definition）候选，语种：zh-CN");
        assertThat(draftPrompt).contains("推断「指称」（designation，即可读的名称）候选，语种：zh-CN");
        assertThat(draftPrompt).contains("每个字段至多给出 3 个候选");
        // 写作规则与输出契约与库内模式一致
        assertThat(draftPrompt).contains("【写作规则】");
        assertThat(draftPrompt).contains("ISO/IEC 11179-4");
        assertThat(draftPrompt).contains("ISO/IEC 11179-5");
        assertThat(draftPrompt).contains("【输出契约】");
        assertThat(draftPrompt).contains("{\"candidates\":[");
        assertThat(draftPrompt).contains("只输出一个 JSON 对象");
    }

    @Test
    void buildDraft_truncatesOverlongValues() {
        String overlong = "长".repeat(2001);
        Map<String, Object> draft = Map.of(
                "designations", List.of(Map.of("value", overlong)));

        String prompt = builder().buildDraft(draft, List.of("definition"), "zh-CN", 3);

        assertThat(prompt).contains("长".repeat(2000) + "…（已截断）");
        assertThat(prompt).doesNotContain(overlong);
    }
}
