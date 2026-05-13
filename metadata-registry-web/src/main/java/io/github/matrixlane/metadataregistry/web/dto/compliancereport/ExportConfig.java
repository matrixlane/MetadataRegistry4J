package io.github.matrixlane.metadataregistry.web.dto.compliancereport;

import java.util.List;

/**
 * 报告导出配置.
 *
 * <p>对应 WI-013-3 需求 4：导出配置。
 * 支持选择数据集、匹配状态、自定义标题和说明文字。
 */
public record ExportConfig(
    List<String> dataSetCodes,
    List<String> matchStatuses,
    String reportTitle,
    String reportDescription
) {

    /** 默认导出配置：包含所有数据集和所有匹配状态. */
    public static ExportConfig defaults() {
        return new ExportConfig(List.of(), List.of(), "互联互通标准化成熟度测评合规报告", "");
    }
}
