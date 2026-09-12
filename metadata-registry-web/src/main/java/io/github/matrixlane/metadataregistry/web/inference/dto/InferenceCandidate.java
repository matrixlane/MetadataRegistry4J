package io.github.matrixlane.metadataregistry.web.inference.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 推断候选：为某个缺失字段（definition / designation）生成的一条建议。
 *
 * @param field     目标字段：definition 或 designation
 * @param language  候选文本语种（如 zh-CN、en-US），可空
 * @param context   指称候选的建议适用上下文名称，可空
 * @param text      候选文本（定义正文或指称名称）
 * @param rationale 生成理由，供注册人员评审
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InferenceCandidate(
        String field,
        String language,
        String context,
        String text,
        String rationale) {
}
