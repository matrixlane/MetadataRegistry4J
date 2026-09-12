package io.github.matrixlane.metadataregistry.web.assistant;

/**
 * 协作任务输出种类。
 *
 * <p>CANDIDATES：候选数组（如补全指称/定义，供注册人员评审选择）；
 * TEXT：单段自由文本（如划选解释）；FINDINGS：结构化意见（如定义评审结论，占位）。
 */
public enum OutputKind {
    CANDIDATES,
    TEXT,
    FINDINGS
}
