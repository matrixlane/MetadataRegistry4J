package io.github.matrixlane.metadataregistry.web.inference;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Context;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Slot;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Acceptability;
import io.github.matrixlane.metadataregistry.dataspecification.DataElement;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.common.ItemMetaResponse;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLineContextResponse;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLineResponse;
import io.github.matrixlane.metadataregistry.web.repository.ContextRepository;
import io.github.matrixlane.metadataregistry.web.repository.ItemRepository;
import io.github.matrixlane.metadataregistry.web.service.ItemAdministrationService;
import io.github.matrixlane.metadataregistry.web.util.ItemNamingHelper;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 推断提示词组装器：以目标项目既有元数据（只读）组装中文提示词。
 *
 * <p>已知元数据段落包含指称（语种/上下文/来源文档）、定义、数据元概念与值域
 * 首选名称、slots、注册信息，空段落省略，长文本按 2000 字符截断；任务段落指明
 * 请求推断的缺失字段、目标语种与每字段候选数上限；并内嵌 ISO/IEC 11179-4 定义
 * 写作规则、ISO/IEC 11179-5 命名原则与「仅输出单个 JSON 对象」的严格输出契约。
 */
@Service
@RequiredArgsConstructor
public class InferencePromptBuilder {

    /** 单字段文本截断上限，防止长来源文档摘录撑爆提示词。 */
    static final int FIELD_TRUNCATE_LIMIT = 2000;

    private final ItemRepository itemRepository;
    private final ItemAdministrationService itemAdministrationService;
    private final ContextRepository contextRepository;

    /**
     * 组装推断提示词。Item 图在只读事务内加载（open-in-view=false 下
     * 关联集合须于事务内读取）。
     *
     * @param itemId        目标项目
     * @param fields        请求推断的字段（definition / designation）
     * @param language      目标语种（如 zh-CN），可空时默认 zh-CN
     * @param contextId     指称候选的建议上下文，可空
     * @param candidateCount 每字段候选数上限（≤ maxCandidates）
     */
    @Transactional(readOnly = true)
    public String build(UUID itemId, List<String> fields, String language,
                        UUID contextId, int candidateCount) {
        String targetLanguage = (language == null || language.isBlank()) ? "zh-CN" : language.trim();
        Context context = contextId == null ? null : contextRepository.findById(contextId)
                .orElseThrow(() -> new ResourceNotFoundException("Context", contextId));

        StringBuilder prompt = new StringBuilder();
        prompt.append("你是元数据注册系统（遵循 ISO/IEC 11179）的元数据写作助手。"
                + "请基于给定注册条目的已知元数据，推断缺失的元数据候选。\n");

        prompt.append(buildItemContextSection(itemId));
        appendTask(prompt, fields, targetLanguage, context, candidateCount);
        appendWritingRules(prompt);
        appendOutputContract(prompt);

        return prompt.toString();
    }

    /**
     * 组装单个注册条目的已知元数据段落（指称/定义/上下文/来源文档摘要、注册信息），
     * 供推断提示词与协作任务（如划选解释）复用。
     */
    @Transactional(readOnly = true)
    public String buildItemContextSection(UUID itemId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Item", itemId));
        // 触发 JOINED 继承实体的具体类型加载，供 DataElement 专属段落使用
        Hibernate.initialize(item);
        StringBuilder prompt = new StringBuilder();
        appendKnownMetadata(prompt, item);
        return prompt.toString();
    }

    /**
     * 草稿模式组装推断提示词（ai-generate-everywhere D2）：无 itemId 时以表单草稿
     * （条目类型 + 已填指称/定义行）组装「已知元数据」段；写作规则、推断任务段与
     * JSON 输出契约与 {@link #build} 完全一致。
     *
     * <p>草稿是用户主动提供的内容（非缺省状态的降级），故与 selection-chat 不同，
     * 任务层不做 degraded 标注。本方法不读数据库，调用方无需只读事务。
     *
     * @param draft          草稿（itemType? / designations? / definitions?，行结构容错由任务层保证）
     * @param fields         请求推断的字段（definition / designation）
     * @param language       目标语种（如 zh-CN），可空时默认 zh-CN
     * @param candidateCount 每字段候选数上限
     */
    public String buildDraft(Map<String, Object> draft, List<String> fields, String language,
                             int candidateCount) {
        String targetLanguage = (language == null || language.isBlank()) ? "zh-CN" : language.trim();
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是元数据注册系统（遵循 ISO/IEC 11179）的元数据写作助手。"
                + "请基于给定注册条目的已知元数据，推断缺失的元数据候选。\n");

        appendDraftKnownMetadata(prompt, draft);
        appendTask(prompt, fields, targetLanguage, null, candidateCount);
        appendWritingRules(prompt);
        appendOutputContract(prompt);

        return prompt.toString();
    }

    /** 草稿「已知元数据」段：条目类型、指称/定义行；空段省略，长文本按上限截断。 */
    private void appendDraftKnownMetadata(StringBuilder prompt, Map<String, Object> draft) {
        prompt.append("\n【已知元数据】\n");
        if (draft == null) {
            return;
        }
        Object itemType = draft.get("itemType");
        if (itemType instanceof String type && !type.isBlank()) {
            prompt.append("- 条目类型：").append(truncate(type.trim())).append('\n');
        }
        appendDraftTextLines(prompt, "指称（designation）", draft.get("designations"));
        appendDraftTextLines(prompt, "定义（definition）", draft.get("definitions"));
    }

    /** 草稿文本行段落：元素须为 {language?, value} 结构；非结构元素与空行跳过。 */
    private void appendDraftTextLines(StringBuilder prompt, String sectionTitle, Object lines) {
        if (!(lines instanceof List<?> list)) {
            return;
        }
        List<String> rendered = new ArrayList<>();
        for (Object element : list) {
            if (!(element instanceof Map<?, ?> row)) {
                continue;
            }
            Object value = row.get("value");
            if (!(value instanceof String text) || text.isBlank()) {
                continue;
            }
            Object language = row.get("language");
            String languageCode = language instanceof String s && !s.isBlank() ? s : "未知";
            rendered.add("- " + truncate(text.trim()) + "（语种: " + languageCode + "）");
        }
        if (rendered.isEmpty()) {
            return;
        }
        prompt.append('\n').append(sectionTitle).append("：\n");
        for (String line : rendered) {
            prompt.append(line).append('\n');
        }
    }

    private void appendKnownMetadata(StringBuilder prompt, Item item) {
        prompt.append("\n【已知元数据】\n");
        ItemMetaResponse meta = itemAdministrationService.buildItemMeta(item);
        prompt.append("- 条目类型：").append(Hibernate.getClass(item).getSimpleName()).append('\n');
        if (meta != null) {
            if (notBlank(meta.preferredDesignation())) {
                prompt.append("- 首选指称：").append(truncate(meta.preferredDesignation())).append('\n');
            }
            if (notBlank(meta.preferredDefinition())) {
                prompt.append("- 首选定义：").append(truncate(meta.preferredDefinition())).append('\n');
            }
            if (notBlank(meta.scopedIdentifier())) {
                prompt.append("- 标识：").append(meta.scopedIdentifier()).append('\n');
            }
            if (notBlank(meta.registrationAuthorityName())) {
                prompt.append("- 注册机构：").append(truncate(meta.registrationAuthorityName())).append('\n');
            }
            if (meta.registrationStatus() != null) {
                prompt.append("- 注册状态：").append(meta.registrationStatus()).append('\n');
            }
        }

        List<TextLineResponse> designations = itemAdministrationService.mapDesignations(item);
        if (!designations.isEmpty()) {
            prompt.append("\n指称（designation）：\n");
            for (TextLineResponse line : designations) {
                prompt.append("- ").append(truncate(line.value()))
                        .append("（语种: ").append(line.languageCode() == null ? "未知" : line.languageCode());
                if (notBlank(line.sourceTitle())) {
                    prompt.append("；来源文档: ").append(truncate(line.sourceTitle()));
                }
                if (line.acceptability() == Acceptability.PREFERRED) {
                    prompt.append("；首选");
                }
                appendContexts(prompt, line.contexts());
                prompt.append("）\n");
            }
        }

        List<TextLineResponse> definitions = itemAdministrationService.mapDefinitions(item);
        if (!definitions.isEmpty()) {
            prompt.append("\n定义（definition）：\n");
            for (TextLineResponse line : definitions) {
                prompt.append("- ").append(truncate(line.value()))
                        .append("（语种: ").append(line.languageCode() == null ? "未知" : line.languageCode());
                if (notBlank(line.sourceTitle())) {
                    prompt.append("；来源文档: ").append(truncate(line.sourceTitle()));
                }
                if (line.acceptability() == Acceptability.PREFERRED) {
                    prompt.append("；首选");
                }
                appendContexts(prompt, line.contexts());
                prompt.append("）\n");
            }
        }

        if (item instanceof DataElement dataElement) {
            if (dataElement.getDataElementConcept() != null) {
                String decName = ItemNamingHelper.preferredDesignation(dataElement.getDataElementConcept());
                if (notBlank(decName)) {
                    prompt.append("\n数据元概念（DEC）首选指称：").append(truncate(decName)).append('\n');
                }
            }
            if (dataElement.getValueDomain() != null) {
                String vdName = ItemNamingHelper.preferredDesignation(dataElement.getValueDomain());
                if (notBlank(vdName)) {
                    prompt.append("值域（VD）首选指称：").append(truncate(vdName)).append('\n');
                }
            }
        }

        if (item.getSlots() != null && !item.getSlots().isEmpty()) {
            prompt.append("\n扩展槽（slot）：\n");
            for (Slot slot : item.getSlots()) {
                prompt.append("- ").append(slot.getName());
                if (notBlank(slot.getType())) {
                    prompt.append("（类型: ").append(slot.getType()).append("）");
                }
                if (slot.getValue() != null && !slot.getValue().isEmpty()) {
                    prompt.append("：");
                    for (int i = 0; i < slot.getValue().size(); i++) {
                        if (i > 0) {
                            prompt.append("；");
                        }
                        prompt.append(truncate(slot.getValue().get(i)));
                    }
                }
                prompt.append('\n');
            }
        }
    }

    private void appendContexts(StringBuilder prompt, List<TextLineContextResponse> contexts) {
        if (contexts == null || contexts.isEmpty()) {
            return;
        }
        prompt.append("；上下文: ");
        for (int i = 0; i < contexts.size(); i++) {
            if (i > 0) {
                prompt.append("、");
            }
            TextLineContextResponse ctx = contexts.get(i);
            prompt.append(ctx.contextLabel() == null ? "未知" : ctx.contextLabel());
            if ("PREFERRED".equals(ctx.acceptability())) {
                prompt.append("（首选）");
            }
        }
    }

    private void appendTask(StringBuilder prompt, List<String> fields, String targetLanguage,
                            Context context, int candidateCount) {
        prompt.append("\n【推断任务】\n");
        if (fields.contains("definition")) {
            prompt.append("1. 为该条目推断「定义」（definition）候选，语种：").append(targetLanguage).append("。\n");
        }
        if (fields.contains("designation")) {
            prompt.append("2. 为该条目推断「指称」（designation，即可读的名称）候选，语种：")
                    .append(targetLanguage).append("。\n");
            if (context != null) {
                String contextName = ItemNamingHelper.preferredDesignation(context);
                prompt.append("   指称候选的适用上下文：")
                        .append(contextName == null ? "（未命名上下文）" : truncate(contextName)).append("。\n");
            }
        }
        prompt.append("每个字段至多给出 ").append(candidateCount).append(" 个候选，每个候选附生成理由（rationale）。\n");
        prompt.append("候选内容必须与上方已知元数据保持一致，不得引入矛盾的概念或范围。\n");
    }

    private void appendWritingRules(StringBuilder prompt) {
        prompt.append("\n【写作规则】\n");
        prompt.append("定义（ISO/IEC 11179-4）：\n");
        prompt.append("- 每个定义只表述单一概念，使用陈述句，清晰、无歧义。\n");
        prompt.append("- 采用「属 + 种差」结构：先给出上位概念（属），再给出区别于同类的本质特征（种差）。\n");
        prompt.append("- 不得循环定义（不得用被定义项自身或其同义表述定义自己）。\n");
        prompt.append("- 只陈述本质特征，不混入用途、来源或示例等非定义性内容。\n");
        prompt.append("命名（ISO/IEC 11179-5）：\n");
        prompt.append("- 指称应简洁、单义、符合目标语种命名惯例，避免缩写与歧义。\n");
        prompt.append("- 指称与定义使用一致的语种与术语体系。\n");
    }

    private void appendOutputContract(StringBuilder prompt) {
        prompt.append("\n【输出契约】\n");
        prompt.append("只输出一个 JSON 对象，不要输出任何其他文字、markdown 代码块或解释。结构如下：\n");
        prompt.append("{\"candidates\":[");
        prompt.append("{\"field\":\"definition\",\"language\":\"zh-CN\",\"text\":\"...\",\"rationale\":\"...\"},");
        prompt.append("{\"field\":\"designation\",\"language\":\"en-US\",\"context\":\"...\",\"text\":\"...\",\"rationale\":\"...\"}");
        prompt.append("]}\n");
        prompt.append("field 取值只能是要推断的字段名；text 为候选正文，rationale 为生成理由。\n");
    }

    static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= FIELD_TRUNCATE_LIMIT ? text : text.substring(0, FIELD_TRUNCATE_LIMIT) + "…（已截断）";
    }

    private boolean notBlank(String text) {
        return text != null && !text.isBlank();
    }
}
