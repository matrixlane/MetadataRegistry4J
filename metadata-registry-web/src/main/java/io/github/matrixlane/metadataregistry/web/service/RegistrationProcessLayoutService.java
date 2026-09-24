package io.github.matrixlane.metadataregistry.web.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * 注册流程画布布局的本地文件存储。
 *
 * <p>布局文件为开放式脚本（JSON），位置由 {@code app.registration-process-layout-file}
 * 配置（默认 {@code layout/registration-process-layout.json}，相对应用工作目录）。
 * 启动时若文件缺失，自动以打包预置模板 {@code layout-defaults/registration-process-layout.json}
 * 播种。保存校验 JSON 对象与 64KB 体积上限，并原子写入。</p>
 */
@Slf4j
@Service
public class RegistrationProcessLayoutService {

    private static final long MAX_BYTES = 64 * 1024;

    private final ObjectMapper objectMapper;
    private final Path filePath;

    public RegistrationProcessLayoutService(
            ObjectMapper objectMapper,
            @Value("${app.registration-process-layout-file:layout/registration-process-layout.json}") String file) {
        this.objectMapper = objectMapper;
        this.filePath = Paths.get(file).toAbsolutePath().normalize();
    }

    /** 首启播种：文件缺失时以打包预置模板生成 */
    @PostConstruct
    void ensureSeeded() throws IOException {
        if (Files.exists(filePath)) {
            return;
        }
        Files.createDirectories(filePath.getParent());
        ClassPathResource template = new ClassPathResource("layout-defaults/registration-process-layout.json");
        if (template.exists()) {
            try (InputStream in = template.getInputStream()) {
                Files.copy(in, filePath);
            }
        } else {
            Files.write(filePath, "{}".getBytes(StandardCharsets.UTF_8));
        }
        log.info("注册流程布局文件已播种：{}", filePath);
    }

    /** 读取布局文件内容（文件经 ensureSeeded 保证存在） */
    public JsonNode read() {
        try {
            if (!Files.exists(filePath)) {
                throw new ResourceNotFoundException("RegistrationProcessLayout", filePath);
            }
            return objectMapper.readTree(Files.readAllBytes(filePath));
        } catch (IOException e) {
            throw new BusinessException("注册流程布局文件读取失败: " + e.getMessage());
        }
    }

    /** 保存布局：校验（JSON 对象、≤64KB）后原子写入 */
    public void save(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new BusinessException("布局内容必须为 JSON 对象");
        }
        byte[] bytes;
        try {
            bytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(body);
        } catch (IOException e) {
            throw new BusinessException("布局内容序列化失败: " + e.getMessage());
        }
        if (bytes.length > MAX_BYTES) {
            throw new BusinessException("布局内容超过 64KB 上限");
        }
        try {
            Files.createDirectories(filePath.getParent());
            Path tmp = filePath.resolveSibling(filePath.getFileName() + ".tmp");
            Files.write(tmp, bytes);
            try {
                Files.move(tmp, filePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, filePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new BusinessException("注册流程布局文件写入失败: " + e.getMessage());
        }
        log.info("注册流程布局已保存：{}（{} 字节）", filePath, bytes.length);
    }
}
