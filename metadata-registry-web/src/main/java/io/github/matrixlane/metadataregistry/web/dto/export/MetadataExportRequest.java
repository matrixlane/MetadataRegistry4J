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
        List<String> itemTypes,

        @Schema(description = "分页页码（从 0 起，与 size 同传生效；不传则不分页）")
        Integer page,

        @Schema(description = "分页大小（1-10000，与 page 同传生效；不传则不分页）")
        Integer size
) {
        public MetadataExportRequest {
                if (fields != null) {
                        fields = List.copyOf(fields);
                }
                if (itemTypes != null) {
                        itemTypes = List.copyOf(itemTypes);
                }
                if (page == null && size == null) {
                        // 不分页：保持既有行为
                } else if (page == null || size == null) {
                        throw new IllegalArgumentException("page 与 size 必须同时提供或同时省略");
                } else {
                        if (page < 0) {
                                throw new IllegalArgumentException("page 必须 >= 0，当前: " + page);
                        }
                        if (size < 1 || size > 10_000) {
                                throw new IllegalArgumentException("size 必须在 1..10000 之间，当前: " + size);
                        }
                }
        }

        /** 不分页请求的便捷构造（等价于 page/size 均省略）。 */
        public MetadataExportRequest(
                        RegistrationStatus registrationStatus,
                        UUID registrationAuthorityId,
                        UUID stewardshipOrganizationId,
                        LocalDateTime createdAfter,
                        LocalDateTime createdBefore,
                        LocalDateTime modifiedAfter,
                        LocalDateTime modifiedBefore,
                        List<String> fields,
                        List<String> itemTypes) {
                this(registrationStatus, registrationAuthorityId, stewardshipOrganizationId,
                        createdAfter, createdBefore, modifiedAfter, modifiedBefore,
                        fields, itemTypes, null, null);
        }

        public boolean paged() {
                return page != null;
        }
}
