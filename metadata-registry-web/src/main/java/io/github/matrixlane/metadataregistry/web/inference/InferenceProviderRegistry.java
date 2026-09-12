package io.github.matrixlane.metadataregistry.web.inference;

import io.github.matrixlane.metadataregistry.web.inference.dto.InferenceAgentView;
import io.github.matrixlane.metadataregistry.web.inference.dto.InferenceProviderOption;
import io.github.matrixlane.metadataregistry.web.inference.provider.AcpAgentProvider;
import io.github.matrixlane.metadataregistry.web.inference.provider.MetadataInferenceProvider;
import io.github.matrixlane.metadataregistry.web.inference.provider.ProcessRunner;
import io.github.matrixlane.metadataregistry.web.inference.provider.ProviderStatus;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 推断提供方注册表：内置 claude/codex/kimi 定义 ⊕ 持久化覆盖 ⊕ 自定义条目，
 * 每个 Agent 一个 ACP 常驻进程提供方实例（id 即 Agent id）。
 *
 * <p>启用状态来自 {@link InferenceAgentSettingsService}（保存后立即生效）；
 * 未保存过设置时行为与静态配置一致（{@code mdr.inference.cli.command} 覆盖
 * 默认提供方所指的内置 Agent）。可用性经 ACP {@code initialize} 握手探测，
 * {@link #probeAll()} 强制重建全部常驻进程并重新握手。
 */
@Slf4j
@Service
public class InferenceProviderRegistry {

    /** 内置 ACP Agent 静态定义（均为 ACP server 启动命令）。 */
    static final List<BuiltinAgent> BUILTINS = List.of(
            new BuiltinAgent("claude", "Claude Code (ACP)", "claude-agent-acp", List.of()),
            new BuiltinAgent("codex", "Codex CLI (ACP)", "codex-acp", List.of()),
            new BuiltinAgent("kimi", "Kimi CLI (ACP)", "kimi", List.of("acp")));

    /** 可用作默认提供方的静态 provider 值（旧语义 cli → claude）。 */
    private static final String LEGACY_CLI = "cli";

    private final InferenceProperties properties;
    private final InferenceAgentSettingsService settingsService;
    private final ProcessRunner processRunner;

    /** 按当前设置构建的 Agent 条目；设置变更或强制探测时整体重建。 */
    private volatile List<AgentEntry> entries = List.of();
    private volatile InferenceAgentSettings builtFor;

    public InferenceProviderRegistry(InferenceProperties properties,
                                     InferenceAgentSettingsService settingsService,
                                     ProcessRunner processRunner) {
        this.properties = properties;
        this.settingsService = settingsService;
        this.processRunner = processRunner;
    }

    /**
     * 合并视图（内置 + 覆盖 + 自定义），含探测状态与默认标记。
     * 设置未变化时复用既有提供方实例（常驻进程探测状态即时可查）。
     */
    public List<InferenceAgentView> list() {
        return entries().stream().map(AgentEntry::view).toList();
    }

    /** 推断对话框选项：仅已启用且探测可用的提供方。 */
    public List<InferenceProviderOption> availableOptions() {
        String defaultId = defaultProviderId();
        return entries().stream()
                .filter(e -> e.view.enabled() && e.view.available())
                .map(e -> new InferenceProviderOption(e.view.id(), e.view.displayName(),
                        e.view.id().equals(defaultId)))
                .toList();
    }

    /**
     * 解析已启用的提供方。仅已启用 Agent 可被推断作业选用；
     * 未知或已禁用 id 返回空（由调用方转为参数校验错误）。
     */
    public Optional<MetadataInferenceProvider> resolveEnabled(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return entries().stream()
                .filter(e -> e.view.id().equals(id) && e.view.enabled())
                .map(e -> e.provider)
                .findFirst();
    }

    /**
     * 默认提供方 id：设置文件的 defaultProvider 优先；未设置时回退静态配置旧语义
     * （{@code mdr.inference.provider}，其中 {@code cli} 映射到内置 {@code claude}）。
     */
    public String defaultProviderId() {
        InferenceAgentSettings settings = settingsService.current();
        if (settings.defaultProvider() != null && !settings.defaultProvider().isBlank()) {
            return settings.defaultProvider().trim();
        }
        String legacy = properties.getProvider();
        if (legacy == null || legacy.isBlank() || LEGACY_CLI.equals(legacy)) {
            return "claude";
        }
        return legacy.trim();
    }

    /** 强制销毁并重建全部 Agent 的常驻进程，重新握手探测，返回最新合并视图。 */
    public List<InferenceAgentView> probeAll() {
        InferenceAgentSettings current = settingsService.current();
        List<AgentEntry> rebuilt = buildEntries(current);
        List<AgentEntry> probed = new ArrayList<>();
        List<InferenceAgentView> views = new ArrayList<>();
        for (AgentEntry entry : rebuilt) {
            InferenceAgentView view = replaceProbe(entry.view, entry.provider.probe());
            probed.add(new AgentEntry(entry.provider, view));
            views.add(view);
        }
        synchronized (this) {
            entries = List.copyOf(probed);
            builtFor = current;
        }
        return views;
    }

    /** 应用关闭时销毁全部常驻 ACP 进程。 */
    @PreDestroy
    public void destroyAll() {
        for (AgentEntry entry : entries) {
            try {
                entry.provider().destroy();
            } catch (RuntimeException e) {
                log.debug("销毁推断提供方 {} 失败", entry.provider().id(), e);
            }
        }
    }

    /** 设置变更时按引用比较触发重建；新建实例立即探测一次填充视图与缓存。 */
    private List<AgentEntry> entries() {
        InferenceAgentSettings current = settingsService.current();
        if (entries.isEmpty() || builtFor != current) {
            synchronized (this) {
                current = settingsService.current();
                if (entries.isEmpty() || builtFor != current) {
                    List<AgentEntry> rebuilt = buildEntries(current);
                    List<AgentEntry> probed = new ArrayList<>();
                    for (AgentEntry entry : rebuilt) {
                        InferenceAgentView view = replaceProbe(entry.view, entry.provider.status());
                        probed.add(new AgentEntry(entry.provider, view));
                    }
                    entries = List.copyOf(probed);
                    builtFor = current;
                }
            }
        }
        return entries;
    }

    private List<AgentEntry> buildEntries(InferenceAgentSettings settings) {
        Map<String, AgentEntry> merged = new LinkedHashMap<>();
        String defaultId = defaultProviderId();
        for (BuiltinAgent builtin : BUILTINS) {
            InferenceAgentSettings.AgentSetting override = settings.agent(builtin.id());
            // v2 设置语义：内置条目的 command/args 覆盖已废弃（v1 迁移时丢弃），
            // 一律使用内置 ACP 命令；仅保留 enabled/displayName 覆盖
            String command = builtin.command();
            List<String> args = builtin.args();
            if (isStaticCommandOverride(builtin.id())) {
                command = properties.getCli().getCommand().trim();
                args = properties.getCli().getArgs() == null ? List.of() : List.copyOf(properties.getCli().getArgs());
            }
            String displayName = override != null && notBlank(override.displayName())
                    ? override.displayName() : builtin.displayName();
            boolean enabled = override == null || override.enabledOrDefault();
            List<String> fullCommand = new ArrayList<>();
            fullCommand.add(command);
            fullCommand.addAll(args);
            AcpAgentProvider provider = new AcpAgentProvider(builtin.id(), displayName, fullCommand,
                    properties.getCli().getTimeoutSeconds(), properties.getCli().getWorkDir(), processRunner);
            InferenceAgentView view = new InferenceAgentView(builtin.id(), displayName, "acp", command,
                    List.copyOf(args), enabled, false, null,
                    builtin.id().equals(defaultId), true);
            merged.put(builtin.id(), new AgentEntry(provider, view));
        }
        // 自定义条目：设置中出现、且不属于内置 id 的 Agent（command/args 为 ACP server 启动命令）
        for (Map.Entry<String, InferenceAgentSettings.AgentSetting> e : settings.agents().entrySet()) {
            String id = e.getKey();
            if (merged.containsKey(id)) {
                continue;
            }
            InferenceAgentSettings.AgentSetting custom = e.getValue();
            if (!notBlank(custom.command())) {
                log.warn("自定义推断 Agent {} 缺少 command，已跳过", id);
                continue;
            }
            boolean enabled = custom.enabledOrDefault();
            List<String> args = custom.args() == null ? List.of() : List.copyOf(custom.args());
            List<String> fullCommand = new ArrayList<>();
            fullCommand.add(custom.command().trim());
            fullCommand.addAll(args);
            String displayName = notBlank(custom.displayName()) ? custom.displayName() : id;
            AcpAgentProvider provider = new AcpAgentProvider(id, displayName, fullCommand,
                    properties.getCli().getTimeoutSeconds(), properties.getCli().getWorkDir(), processRunner);
            InferenceAgentView view = new InferenceAgentView(id, displayName, "acp", custom.command().trim(),
                    args, enabled, false, null, id.equals(defaultId), false);
            merged.put(id, new AgentEntry(provider, view));
        }
        return List.copyOf(merged.values());
    }

    private InferenceAgentView replaceProbe(InferenceAgentView view, ProviderStatus status) {
        return new InferenceAgentView(view.id(), view.displayName(), view.kind(), view.command(),
                view.args(), view.enabled(), status.available(), status.detail(), view.isDefault(), view.builtin());
    }

    /** 静态配置覆盖：mdr.inference.cli.command 非空时覆盖默认提供方所指的内置 Agent。 */
    private boolean isStaticCommandOverride(String builtinId) {
        return builtinId.equals(defaultProviderId())
                && properties.getCli().getCommand() != null
                && !properties.getCli().getCommand().isBlank();
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** 内置 Agent 静态定义。 */
    record BuiltinAgent(String id, String displayName, String command, List<String> args) {

        BuiltinAgent {
            args = List.copyOf(args);
        }
    }

    /** 提供方实例与其合并视图。 */
    record AgentEntry(MetadataInferenceProvider provider, InferenceAgentView view) {
    }
}
