package io.github.matrixlane.metadataregistry.web.dto.export.datadictionary;

import io.github.matrixlane.metadataregistry.web.dto.export.ExportFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * 数据字典导出请求参数
 */
public record DataDictionaryExportRequest(

        @NotEmpty(message = "导出项ID列表不能为空")
        List<UUID> itemIds,

        @NotNull(message = "导出格式不能为空")
        ExportFormat format,

        /** 导出模式：SINGLE = 单文件多sheet, MULTI = 多文件zip */
        ExportMode mode,

        /** 是否包含值域详细信息 */
        boolean includeValueDomainDetails,

        /** 是否包含注册元信息 */
        boolean includeRegistrationInfo,

        /** 是否异步导出（大数据量时自动启用） */
        boolean async,

        /** 异步导出：最大条数限制（默认5000同步，最多50000异步） */
        @Min(1)
        @Max(50000)
        Integer maxItems
) {
    public DataDictionaryExportRequest {
        if (mode == null) {
            mode = ExportMode.SINGLE;
        }
        if (maxItems == null) {
            maxItems = 5000;
        }
    }

    public enum ExportMode {
        SINGLE,
        MULTI
    }
}
