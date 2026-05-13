package io.github.matrixlane.metadataregistry.web.service.compliancereport;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 合规评分权重配置.
 *
 * <p>参数化 WI-013-2 的对齐质量评分权重，避免硬编码。
 * 可通过 application.yml 配置：
 * <pre>
 * app:
 *   compliance:
 *     score:
 *       weight:
 *         exact: 1.0
 *         partial: 0.5
 *         none: 0.0
 * </pre>
 *
 * @see <a href="https://github.com/matrixlane/MetadataRegistry4j/">Metadata Registry</a>
 */
@Component
@ConfigurationProperties(prefix = "app.compliance.score.weight")
public class ComplianceScoreWeightConfig {

    /** 完全匹配权重. */
    private double exact = 1.0;

    /** 部分匹配权重. */
    private double partial = 0.5;

    /** 无匹配权重. */
    private double none = 0.0;

    public double getExact() { return exact; }
    public void setExact(double exact) { this.exact = exact; }

    public double getPartial() { return partial; }
    public void setPartial(double partial) { this.partial = partial; }

    public double getNone() { return none; }
    public void setNone(double none) { this.none = none; }
}
