package io.github.matrixlane.metadataregistry.web.dto.fhir.compliance;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProfileSuggestionResponse {

    private UUID profileId;
    private UUID resourceId;

    /**
     * Explicit status marker: this profile is a draft and requires human review.
     */
    @Builder.Default
    private String status = "DRAFT";

    @Builder.Default
    private String disclaimer = "本 Profile 为自动生成的草稿建议，需经 FHIR 实施专家人工审核后方可用于生产环境。";

    /**
     * The generated StructureDefinition JSON string.
     */
    private String profileJson;

    /**
     * Suggested extensions for local extra fields.
     */
    private List<ExtensionSuggestion> suggestedExtensions;

    /**
     * Warnings about assumptions or limitations in the generated profile.
     */
    private List<String> warnings;
}
