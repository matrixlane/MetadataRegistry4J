package io.github.matrixlane.metadataregistry.web.mcp.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * MCP 工具调用审计（add-mcp-server-support）。记录每次调用的
 * 时间戳、身份、工具名、参数摘要（SHA-256）、结果状态与耗时。
 *
 * <p>写入独立 logger {@code MDR.MCP}（logback 配置独立 appender 至
 * {@code logs/app-mcp.log}）；MUST NOT 写数据库（零 schema 变更），
 * MUST NOT 记录明文口令/令牌。
 */
@Service
public class McpToolAuditService {

    private static final Logger AUDIT = LoggerFactory.getLogger("MDR.MCP");

    /**
     * @param tool        工具名
     * @param identity    调用身份标签（如 {@code account:<uuid>} / {@code anonymous@<addr>}）
     * @param argsDigest  参数 JSON 的 SHA-256 摘要（hex）
     * @param status      结果状态：OK / BUSINESS_ERROR / INTERNAL_ERROR / REJECTED
     * @param durationMs  耗时
     */
    public void record(String tool, String identity, String argsDigest, String status, long durationMs) {
        AUDIT.info("tool={} identity={} argsSha256={} status={} durationMs={}",
                tool, identity, argsDigest, status, durationMs);
    }
}
