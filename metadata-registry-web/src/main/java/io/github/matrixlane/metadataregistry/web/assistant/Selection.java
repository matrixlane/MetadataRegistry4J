package io.github.matrixlane.metadataregistry.web.assistant;

/**
 * 划选输入：用户对某文本容器选中的一段原文及其溯源信息。
 *
 * @param field          所在字段（如 designation / definition / context），可空
 * @param text           选段原文（explain-selection 任务必填，长度上限见任务校验）
 * @param surroundingText 所在完整文本（注入提示词时超长截断），可空
 * @param language       选段语种（如 zh-CN），可空
 * @param startOffset    选段起始偏移，仅供展示溯源，不进提示词核心
 * @param endOffset      选段结束偏移，仅供展示溯源
 */
public record Selection(
        String field,
        String text,
        String surroundingText,
        String language,
        Integer startOffset,
        Integer endOffset) {
}
