package io.github.matrixlane.metadataregistry.web.inference;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 推断 Agent 持久化设置（对应 {@code ~/.metadata-registry/inference-agents.yml}）。
 *
 * <p>格式版本 2（ACP 协议）：内置 Agent 的 command/args 不再可覆盖，条目仅存
 * enabled/displayName 覆盖；自定义条目的 command/args 解释为 ACP server 启动命令。
 * 加载 v1（或无 version）文件时，内置条目的 command/args 覆盖被丢弃
 * （CLI 时代参数对 ACP server 无意义），enabled/displayName/defaultProvider 保留。
 *
 * @param version         文件格式版本
 * @param defaultProvider 默认提供方 id（null = 未设置，回退静态配置旧语义）
 * @param agents          Agent 覆盖/自定义定义（id → 设置项）；内置 id 仅存覆盖项
 */
public record InferenceAgentSettings(
        String version,
        String defaultProvider,
        Map<String, AgentSetting> agents) {

    public static final String FORMAT_VERSION = "2";

    /** 内置 Agent id；v1 迁移时这些条目的 command/args 覆盖被丢弃。 */
    static final java.util.Set<String> BUILTIN_IDS = java.util.Set.of("claude", "codex", "kimi");

    public InferenceAgentSettings {
        agents = agents == null ? Map.of() : Map.copyOf(agents);
    }

    /** 无持久化设置（文件缺失/损坏或未保存过）时的空设置。 */
    public static InferenceAgentSettings empty() {
        return new InferenceAgentSettings(FORMAT_VERSION, null, Map.of());
    }

    public AgentSetting agent(String id) {
        return agents.get(id);
    }

    /** 单个 Agent 设置；enabled 为 null 表示未显式设置（回退内置默认启用）。 */
    public record AgentSetting(Boolean enabled, String displayName, String command, List<String> args) {

        public static AgentSetting enabledByDefault() {
            return new AgentSetting(null, null, null, null);
        }

        public boolean enabledOrDefault() {
            return enabled == null || enabled;
        }
    }

    /** 用于写文件的有序 map 表示。 */
    Map<String, Object> toYamlMap() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", version == null ? FORMAT_VERSION : version);
        if (defaultProvider != null && !defaultProvider.isBlank()) {
            root.put("defaultProvider", defaultProvider);
        }
        Map<String, Object> agentsMap = new LinkedHashMap<>();
        agents.forEach((id, setting) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            if (setting.enabled() != null) {
                item.put("enabled", setting.enabled());
            }
            if (setting.displayName() != null && !setting.displayName().isBlank()) {
                item.put("displayName", setting.displayName());
            }
            if (setting.command() != null && !setting.command().isBlank()) {
                item.put("command", setting.command());
            }
            if (setting.args() != null && !setting.args().isEmpty()) {
                item.put("args", List.copyOf(setting.args()));
            }
            agentsMap.put(id, item);
        });
        if (!agentsMap.isEmpty()) {
            root.put("agents", agentsMap);
        }
        return root;
    }

    @SuppressWarnings("unchecked")
    static InferenceAgentSettings fromYamlMap(Map<String, Object> root) {
        if (root == null) {
            return empty();
        }
        String version = root.get("version") instanceof String s ? s : "1";
        boolean legacyV1 = "1".equals(version) || version.isBlank();
        String defaultProvider = root.get("defaultProvider") instanceof String s ? s : null;
        Map<String, AgentSetting> agents = new LinkedHashMap<>();
        Object agentsObj = root.get("agents");
        if (agentsObj instanceof Map<?, ?> agentsMap) {
            agentsMap.forEach((key, value) -> {
                if (!(key instanceof String id) || !(value instanceof Map<?, ?> item)) {
                    return;
                }
                Boolean enabled = item.get("enabled") instanceof Boolean b ? b : null;
                String displayName = item.get("displayName") instanceof String s ? s : null;
                String command = item.get("command") instanceof String s ? s : null;
                List<String> args = null;
                if (item.get("args") instanceof List<?> list) {
                    args = list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
                }
                // v1 → v2 迁移：内置 Agent 的 command/args 覆盖属 CLI 时代配置，丢弃；
                // enabled/displayName 保留；自定义条目原样保留（command/args 解释为 ACP 启动命令）
                if (legacyV1 && BUILTIN_IDS.contains(id)) {
                    command = null;
                    args = null;
                }
                agents.put(id, new AgentSetting(enabled, displayName, command, args));
            });
        }
        // 旧文件中的未知键（如已移除的 direct 段）天然忽略，保证向后兼容
        return new InferenceAgentSettings(FORMAT_VERSION, defaultProvider, agents);
    }
}
