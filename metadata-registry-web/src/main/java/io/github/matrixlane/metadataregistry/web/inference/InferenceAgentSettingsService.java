package io.github.matrixlane.metadataregistry.web.inference;

import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 推断 Agent 设置服务：读写 {@code ~/.metadata-registry/inference-agents.yml}。
 *
 * <p>启动时加载进内存（{@link AtomicReference}），保存时写文件并立即替换内存态，
 * 无需重启即生效。文件缺失或损坏时回退到「无持久化设置」（静态配置语义），
 * 记警告日志且不阻断启动。
 *
 * <p>风格对齐 {@code DatasourceConfigService}：固定用户主目录位置，
 * 可用系统属性 {@code metadata.registry.inference.agents.file} 覆盖（测试用）。
 */
@Slf4j
@Service
public class InferenceAgentSettingsService {

    private static final Path DEFAULT_FILE = Paths.get(
            System.getProperty("user.home"), ".metadata-registry", "inference-agents.yml");

    private final Path settingsFile;
    private final AtomicReference<InferenceAgentSettings> settings;

    public InferenceAgentSettingsService() {
        this(resolveFile());
    }

    /** 测试专用构造器：指定设置文件路径（跨包测试可用）。 */
    public InferenceAgentSettingsService(Path settingsFile) {
        this.settingsFile = settingsFile;
        this.settings = new AtomicReference<>(load());
    }

    private static Path resolveFile() {
        String override = System.getProperty("metadata.registry.inference.agents.file");
        return override != null && !override.isBlank()
                ? Paths.get(override)
                : DEFAULT_FILE;
    }

    private InferenceAgentSettings load() {
        if (!Files.exists(settingsFile)) {
            return InferenceAgentSettings.empty();
        }
        try (InputStream in = Files.newInputStream(settingsFile)) {
            Map<String, Object> loaded = new Yaml().load(in);
            return InferenceAgentSettings.fromYamlMap(loaded);
        } catch (Exception e) {
            log.warn("推断 Agent 设置文件 {} 无法解析，回退到静态配置语义：{}",
                    settingsFile.toAbsolutePath(), e.getMessage());
            return InferenceAgentSettings.empty();
        }
    }

    /** 当前生效的设置（可能为空设置）。 */
    public InferenceAgentSettings current() {
        return settings.get();
    }

    /** 保存设置：写文件成功后立即替换内存态。 */
    public void save(InferenceAgentSettings newSettings) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        Yaml yaml = new Yaml(options);
        try {
            Path parent = settingsFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Writer writer = Files.newBufferedWriter(settingsFile)) {
                yaml.dump(newSettings.toYamlMap(), writer);
            }
        } catch (IOException e) {
            throw new BusinessException("写入推断 Agent 设置文件失败: " + e.getMessage());
        }
        settings.set(newSettings);
        log.info("推断 Agent 设置已保存到 {} 并立即生效", settingsFile.toAbsolutePath());
    }
}
