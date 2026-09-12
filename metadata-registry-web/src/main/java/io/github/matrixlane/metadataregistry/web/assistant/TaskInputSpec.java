package io.github.matrixlane.metadataregistry.web.assistant;

import java.util.Set;

/**
 * 协作任务输入规格：是否需要关联项目、划选文本的要求程度、支持的 params 键。
 *
 * @param itemIdRequired 是否必须关联注册项目
 * @param selection      划选文本要求：NONE / OPTIONAL / REQUIRED
 * @param params         支持的 params 键集合（仅作契约声明，校验由任务实现负责）
 */
public record TaskInputSpec(boolean itemIdRequired, SelectionRequirement selection, Set<String> params) {

    public TaskInputSpec {
        params = params == null ? Set.of() : Set.copyOf(params);
    }

    /** 划选文本要求程度。 */
    public enum SelectionRequirement {
        NONE,
        OPTIONAL,
        REQUIRED
    }
}
