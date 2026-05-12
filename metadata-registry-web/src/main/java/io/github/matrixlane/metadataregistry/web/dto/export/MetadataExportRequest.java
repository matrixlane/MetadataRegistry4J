package io.github.matrixlane.metadataregistry.web.dto.export;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 注册元数据导出请求参数
 */
@Schema(description = "注册元数据导出请求")
public record MetadataExportRequest(

        @Schema(description = "按注册状态过滤")
        RegistrationStatus registrationStatus,

        @Schema(description = "按注册机构ID过滤")
        UUID registrationAuthorityId,

        @Schema(description = "按管理组织ID过滤（StewardshipRecord.organization）")
        UUID stewardshipOrganizationId,

        @Schema(description = "创建时间起始（包含）")
        LocalDateTime createdAfter,

        @Schema(description = "创建时间截止（包含）")
        LocalDateTime createdBefore,

        @Schema(description = "最后修改时间起始（包含）")
        LocalDateTime modifiedAfter,

        @Schema(description = "最后修改时间截止（包含）")
        LocalDateTime modifiedBefore,

        @Schema(description = "字段白名单（仅导出指定字段），为空则导出全部字段")
        @Size(max = 50, message = "字段筛选最多支持50个字段")
        List<String> fields,

        @Schema(description = "按Item类型过滤，如 DataElement, DataElementConcept, ObjectClass 等")
        @Size(max = 20, message = "类型筛选最多支持20个类型")
        List<String> itemTypes
) {
        public MetadataExportRequest {
                if (fields != null) {
                        fields = List.copyOf(fields);
                }
                if (itemTypes != null) {
                        itemTypes = List.copyOf(itemTypes);
                }
        }
}
