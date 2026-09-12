package io.github.matrixlane.metadataregistry.web.assistant;

import java.util.Map;
import java.util.UUID;

/**
 * 协作任务执行上下文：任务实现组装提示词与解析输出所需的全部输入。
 *
 * @param itemId    关联注册项目 id，可空（是否必填由任务的 inputSpec 决定）
 * @param selection 划选输入，可空
 * @param params    任务参数（任务自定义语义，如 fields / language / question）
 * @param caller    调用方用户名（创建者隔离依据）
 */
public record TaskContext(
        UUID itemId,
        Selection selection,
        Map<String, Object> params,
        String caller) {

    public TaskContext {
        params = params == null ? Map.of() : Map.copyOf(params);
    }

    /** 读取字符串参数；缺失或非字符串时返回 null。 */
    public String stringParam(String key) {
        Object value = params.get(key);
        return value instanceof String s && !s.isBlank() ? s : null;
    }
}
