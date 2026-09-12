package io.github.matrixlane.metadataregistry.web.assistant;

/**
 * 协作任务 SPI：一种人机协作场景（补全 / 划选解释 / 评审 / 翻译…）的插件抽象。
 *
 * <p>新增协作场景 = 新增本接口实现（注册为 Spring Bean 即入注册表），
 * 作业内核、Agent 接入层与安全边界零改动。作业内核按
 * {@code validate → buildPrompt → parseOutput} 顺序调用；
 * {@code parseOutput} 携带 {@link TaskContext}，供依赖请求参数的后处理
 * （如按候选数上限裁剪）使用。
 */
public interface AssistantTask {

    /** 任务唯一 id，如 complete-metadata / explain-selection。 */
    String id();

    /** 展示名（任务清单返回）；默认回退 id。 */
    default String displayName() {
        return id();
    }

    /** 输入规格：是否必须关联项目、划选要求、支持的 params 键。 */
    TaskInputSpec inputSpec();

    /** 输出种类，决定作业结果的定型与前端展示方式。 */
    OutputKind outputKind();

    /** 任务级输入校验（在内核通用校验之后调用）；非法输入抛 BusinessException(VALIDATION_FAILED)。 */
    default void validate(TaskContext ctx) {
    }

    /** 组装提示词；ctx 已按 inputSpec 完成通用校验。 */
    String buildPrompt(TaskContext ctx);

    /**
     * 按输出种类校验/结构化原始输出：CANDIDATES 解析候选数组，TEXT 做轻量清洗，
     * FINDINGS 结构化意见。失败抛 InferenceException（作业置 FAILED）。
     */
    Object parseOutput(TaskContext ctx, String raw);

    /** 本次执行是否按降级语境处理（如无关联项目时的通用解释）；默认 false。 */
    default boolean degraded(TaskContext ctx) {
        return false;
    }
}
