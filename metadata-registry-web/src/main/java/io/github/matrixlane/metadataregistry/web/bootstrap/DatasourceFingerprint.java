package io.github.matrixlane.metadataregistry.web.bootstrap;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 有效数据源配置的指纹。
 *
 * <p>按固定位置顺序覆盖影响建表与连接的设置：方言、JDBC 地址、Hibernate 方言、
 * ORM 映射资源、Flyway 迁移目录与 schema 定位。指纹用于识别"数据源配置被手工
 * 改动"——改动即失配，系统退回向导相要求重新确认，而不是带着未知配置进入完整相。
 *
 * <p>输入须为逐项解析后的字符串（已对环境变量占位符求值），逐项 trim 后拼接
 * 取 SHA-256；键序由参数位置固定。本类不接收口令参数，调用方也 MUST NOT 传入
 * 口令或完整连接串之外的未受控内容。
 */
public final class DatasourceFingerprint {

    private DatasourceFingerprint() {
    }

    /** 位置顺序即规范化顺序：vendor、jdbcUrl、dialect、mappingResources、flywayLocations、flywaySchemas、flywayDefaultSchema。 */
    public static String compute(String... values) {
        StringBuilder canonical = new StringBuilder();
        for (String value : values) {
            if (canonical.length() > 0) {
                canonical.append('\n');
            }
            canonical.append(value == null ? "" : value.trim());
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
