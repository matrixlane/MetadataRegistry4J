package io.github.matrixlane.metadataregistry.web.assistant;

import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 辅助设置（对应 {@code ~/.metadata-registry/ai-assist.yml}）。
 *
 * @param version        文件格式版本
 * @param candidateCount 每字段生成候选数（1-5，null = 默认 3）
 * @param hotkeys        快捷键绑定（命令 id → 绑定），null/缺项与默认值合并
 */
public record AiAssistSettings(
        String version,
        Integer candidateCount,
        Map<String, String> hotkeys) {

    public static final String FORMAT_VERSION = "1";
    public static final int MIN_CANDIDATE_COUNT = 1;
    public static final int MAX_CANDIDATE_COUNT = 5;
    public static final int DEFAULT_CANDIDATE_COUNT = 3;

    /** 默认快捷键绑定。 */
    public static final Map<String, String> DEFAULT_HOTKEYS = Map.of("aiDebug.toggle", "Ctrl+Shift+D");

    /** 修饰键的规范顺序（绑定书写 MUST 按此序）。 */
    private static final List<String> MODIFIER_ORDER = List.of("Ctrl", "Alt", "Shift", "Meta");

    /** 无持久化设置（文件缺失/损坏或未保存过）时的空设置。 */
    public static AiAssistSettings empty() {
        return new AiAssistSettings(FORMAT_VERSION, null, null);
    }

    /** 生效候选数：未设置回退默认 3，越界收敛到 [1,5]。 */
    public int candidateCountOrDefault() {
        if (candidateCount == null) {
            return DEFAULT_CANDIDATE_COUNT;
        }
        return Math.min(MAX_CANDIDATE_COUNT, Math.max(MIN_CANDIDATE_COUNT, candidateCount));
    }

    /** 生效快捷键：默认绑定为底、持久化值覆盖同键项。 */
    public Map<String, String> hotkeysOrDefault() {
        Map<String, String> merged = new LinkedHashMap<>(DEFAULT_HOTKEYS);
        if (hotkeys != null) {
            hotkeys.forEach((key, value) -> {
                if (key != null && !key.isBlank() && value != null) {
                    merged.put(key.trim(), value);
                }
            });
        }
        return Map.copyOf(merged);
    }

    /** 保存前校验：候选数范围与每个快捷键绑定的格式，非法抛参数校验错误。 */
    public static void validate(Integer candidateCount, Map<String, String> hotkeys) {
        if (candidateCount != null && (candidateCount < MIN_CANDIDATE_COUNT
                || candidateCount > MAX_CANDIDATE_COUNT)) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "candidateCount 必须在 " + MIN_CANDIDATE_COUNT + "-" + MAX_CANDIDATE_COUNT + " 之间");
        }
        if (hotkeys != null) {
            hotkeys.forEach((command, binding) -> {
                if (command == null || command.isBlank()) {
                    throw new BusinessException(ApiCodes.VALIDATION_FAILED, "快捷键命令标识不能为空");
                }
                String reason = invalidBindingReason(binding);
                if (reason != null) {
                    throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                            "快捷键绑定格式非法（" + command + "）：" + reason);
                }
            });
        }
    }

    /**
     * 绑定格式：修饰键（Ctrl/Alt/Shift/Meta，按规范序、不重复）+ '+' + 主键；
     * 主键为单字母 A-Z、a-z、数字 0-9 或功能键 F1-F12。返回 null 表示合法。
     */
    static String invalidBindingReason(String binding) {
        if (binding == null || binding.isBlank()) {
            return "绑定不能为空";
        }
        String[] parts = binding.split("\\+", -1);
        if (parts.length < 1 || parts[parts.length - 1].isEmpty()) {
            return "缺少主键";
        }
        int lastModifierIndex = -1;
        for (int i = 0; i < parts.length - 1; i++) {
            int order = MODIFIER_ORDER.indexOf(parts[i]);
            if (order < 0) {
                return "未知修饰键：" + parts[i] + "（只允许 Ctrl/Alt/Shift/Meta）";
            }
            if (order <= lastModifierIndex) {
                return "修饰键须按 Ctrl/Alt/Shift/Meta 顺序且不重复";
            }
            lastModifierIndex = order;
        }
        String main = parts[parts.length - 1];
        if (main.length() == 1) {
            char ch = main.charAt(0);
            if (Character.isLetter(ch)) {
                return null;
            }
            if (ch >= '0' && ch <= '9') {
                return null;
            }
        }
        if (main.matches("F([1-9]|1[0-2])")) {
            return null;
        }
        return "主键须为单字母、数字或 F1-F12：" + main;
    }

    /** 用于写文件的有序 map 表示。 */
    Map<String, Object> toYamlMap() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", version == null ? FORMAT_VERSION : version);
        Map<String, Object> generation = new LinkedHashMap<>();
        generation.put("candidateCount", candidateCountOrDefault());
        root.put("generation", generation);
        root.put("hotkeys", new LinkedHashMap<>(hotkeysOrDefault()));
        return root;
    }

    static AiAssistSettings fromYamlMap(Map<String, Object> root) {
        if (root == null) {
            return empty();
        }
        String version = root.get("version") instanceof String s ? s : FORMAT_VERSION;
        Integer candidateCount = null;
        if (root.get("generation") instanceof Map<?, ?> generation
                && generation.get("candidateCount") instanceof Number number) {
            candidateCount = number.intValue();
        }
        Map<String, String> parsedHotkeys = new LinkedHashMap<>();
        if (root.get("hotkeys") instanceof Map<?, ?> hotkeysMap) {
            hotkeysMap.forEach((key, value) -> {
                if (key instanceof String k && value instanceof String v) {
                    parsedHotkeys.put(k, v);
                }
            });
        }
        Map<String, String> hotkeys = parsedHotkeys.isEmpty() ? null : parsedHotkeys;
        return new AiAssistSettings(version, candidateCount, hotkeys);
    }
}
