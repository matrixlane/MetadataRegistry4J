package io.github.matrixlane.metadataregistry.web.dto.designationdefinition;

import java.util.List;

public record ItemRelationshipResponse(
        List<DesignationResponse> designations,
        List<DefinitionResponse> definitions,
        List<ItemRelationshipContextResponse> contexts,
        List<DesignationDefinitionPairingResponse> pairings) {
}
