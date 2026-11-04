package io.github.matrixlane.metadataregistry.web.seed;

import io.github.matrixlane.metadataregistry.web.service.database.DatabaseEnvironment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;

/**
 * 通用基准脚本执行器（baseline-data-init-rebuild / D3）。
 *
 * <p>通用基准数据（G20 语言标识及中文名称等必选基准）的单一事实源是
 * {@code data/scripts/common/<方言>/00-common.sql}（纯 DML、幂等 NOT EXISTS 守卫、
 * 分号分隔），Java 侧只负责执行，MUST NOT 复制其中的数据。系统初始化链路
 * （启动期 {@link StaticDataPresetService#presetAll()}、向导 setup/abort、
 * 恢复出厂）按当前数据源方言自动执行该脚本。</p>
 *
 * <p>脚本目录经 {@code app.baseline-scripts-dir} 可配，默认
 * {@code <工作目录>/data/scripts}（规范化绝对路径）。目录或脚本缺失时记录
 * WARN 并跳过，MUST NOT 阻断启动或初始化流程（可经一键初始化批处理补入）；
 * 脚本执行失败记录 ERROR 并抛出——不完整基准比启动失败更危险。连接经
 * {@link DataSourceUtils} 获取，在 presetAll 的事务内执行，失败随事务整体回滚。</p>
 */
@Slf4j
@Service
public class BaselineScriptPresetService {

    static final String COMMON_SCRIPT = "00-common.sql";

    private final DataSource dataSource;
    private final DatabaseEnvironment databaseEnvironment;
    private final Path scriptsDir;

    public BaselineScriptPresetService(
            DataSource dataSource,
            DatabaseEnvironment databaseEnvironment,
            @Value("${app.baseline-scripts-dir:${user.dir}/data/scripts}") String scriptsDir) {
        this.dataSource = dataSource;
        this.databaseEnvironment = databaseEnvironment;
        this.scriptsDir = Paths.get(scriptsDir).toAbsolutePath().normalize();
    }

    /**
     * 执行当前方言的 {@code 00-common.sql} 通用基准脚本（幂等，可重复调用）。
     * 目录/脚本缺失 WARN 跳过；执行失败 ERROR 并抛出。
     */
    public void applyCommonBaseline() {
        Path dialectDir = scriptsDir.resolve("common").resolve(vendorDir());
        if (!Files.isDirectory(dialectDir)) {
            log.warn("通用基准脚本目录不存在（{}），跳过自动导入；可经 data/init-baseline.bat 补入", dialectDir);
            return;
        }
        Path script = dialectDir.resolve(COMMON_SCRIPT);
        if (!Files.isRegularFile(script)) {
            log.warn("通用基准脚本不存在（{}），跳过自动导入；可经 data/init-baseline.bat 补入", script);
            return;
        }
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            // 脚本为 UTF-8（生成契约）；EncodedResource 必须显式指定，
            // 否则 ScriptUtils 按平台默认字符集读取，中文名称在非 UTF-8 平台上落库乱码
            ScriptUtils.executeSqlScript(connection,
                    new EncodedResource(new FileSystemResource(script), StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            log.error("通用基准脚本 {} 执行失败，基准数据不完整", script, e);
            throw e;
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
        log.info("已执行通用基准脚本 {}", script);
    }

    private String vendorDir() {
        return switch (databaseEnvironment.vendor()) {
            case SQLITE -> "sqlite";
            case DM -> "dm";
            case SQLSERVER -> "sqlserver";
        };
    }
}
