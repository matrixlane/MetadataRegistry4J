package io.github.matrixlane.metadataregistry.web.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link RegistrationProcessLayoutService} 单元测试：首启播种、读取回环、
 * 校验（JSON 对象 / 64KB 上限）、原子写入。
 */
class RegistrationProcessLayoutServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RegistrationProcessLayoutService service(Path file) {
        return new RegistrationProcessLayoutService(objectMapper, file.toString());
    }

    @Test
    void ensureSeeded_createsFileFromBundledTemplate(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("layout/registration-process-layout.json");
        RegistrationProcessLayoutService svc = service(file);

        svc.ensureSeeded();

        assertThat(file).exists();
        ObjectNode root = (ObjectNode) svc.read();
        assertThat(root.has("nodes")).isTrue();
        assertThat(root.path("nodes").has("INCOMPLETE")).isTrue();
    }

    @Test
    void ensureSeeded_keepsExistingFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("layout/registration-process-layout.json");
        Files.createDirectories(file.getParent());
        Files.write(file, "{\"custom\":true}".getBytes());
        RegistrationProcessLayoutService svc = service(file);

        svc.ensureSeeded();

        assertThat(svc.read().has("custom")).isTrue();
        assertThat(svc.read().has("nodes")).isFalse();
    }

    @Test
    void save_roundTrip(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("layout/registration-process-layout.json");
        RegistrationProcessLayoutService svc = service(file);
        svc.ensureSeeded();
        ObjectNode body = objectMapper.createObjectNode();
        body.putObject("nodes").putObject("INCOMPLETE").put("x", 1).put("y", 2);

        svc.save(body);

        assertThat(svc.read().path("nodes").path("INCOMPLETE").path("x").asInt()).isEqualTo(1);
    }

    @Test
    void save_rejectsNonObject(@TempDir Path dir) {
        RegistrationProcessLayoutService svc = service(dir.resolve("layout/x.json"));

        assertThatThrownBy(() -> svc.save(objectMapper.createArrayNode()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("JSON 对象");
    }

    @Test
    void save_rejectsOversize(@TempDir Path dir) {
        RegistrationProcessLayoutService svc = service(dir.resolve("layout/x.json"));
        ObjectNode body = objectMapper.createObjectNode();
        body.put("pad", "x".repeat(70 * 1024));

        assertThatThrownBy(() -> svc.save(body))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("64KB");
    }
}
