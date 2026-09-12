package io.github.matrixlane.metadataregistry.web.assistant;

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
 * AI 辅助设置服务：读写 {@code ~/.metadata-registry/ai-assist.yml}。
 *
 * <p>模式同 {@code InferenceAgentSettingsService}：启动时加载进内存
 * （{@link AtomicReference}），保存时写文件并立即替换内存态，无需重启即生效；
 * 文件缺失或损坏时回退默认设置，记警告日志且不阻断启动。
 *
 * <p>可用系统属性 {@code metadata.registry.ai-assist.file} 覆盖文件位置（测试用）。
 */
@Slf4j
@Service
public class AiAssistSettingsService {

    private static final Path DEFAULT_FILE = Paths.get(
            System.getProperty("user.home"), ".metadata-registry", "ai-assist.yml");

    private final Path settingsFile;
    private final AtomicReference<AiAssistSettings> settings;

    public AiAssistSettingsService() {
        this(resolveFile());
    }

    /** 测试专用构造器：指定设置文件路径。 */
    public AiAssistSettingsService(Path settingsFile) {
        this.settingsFile = settingsFile;
        this.settings = new AtomicReference<>(load());
    }

    private static Path resolveFile() {
        String override = System.getProperty("metadata.registry.ai-assist.file");
        return override != null && !override.isBlank()
                ? Paths.get(override)
                : DEFAULT_FILE;
    }

    private AiAssistSettings load() {
        if (!Files.exists(settingsFile)) {
            return AiAssistSettings.empty();
        }
        try (InputStream in = Files.newInputStream(settingsFile)) {
            Map<String, Object> loaded = new Yaml().load(in);
            return AiAssistSettings.fromYamlMap(loaded);
        } catch (Exception e) {
            log.warn("AI 辅助设置文件 {} 无法解析，回退默认设置：{}",
                    settingsFile.toAbsolutePath(), e.getMessage());
            return AiAssistSettings.empty();
        }
    }

    /** 当前生效的设置（candidateCount/hotkeys 为持久化原值，读侧用 OrDefault 方法）。 */
    public AiAssistSettings current() {
        return settings.get();
    }

    /** 生效候选数（1-5，默认 3）。complete-metadata 任务执行时经此读取。 */
    public int candidateCount() {
        return current().candidateCountOrDefault();
    }

    /** 保存设置：写文件成功后立即替换内存态。 */
    public void save(AiAssistSettings newSettings) {
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
            throw new BusinessException("写入 AI 辅助设置文件失败: " + e.getMessage());
        }
        settings.set(newSettings);
        log.info("AI 辅助设置已保存到 {} 并立即生效", settingsFile.toAbsolutePath());
    }
}
