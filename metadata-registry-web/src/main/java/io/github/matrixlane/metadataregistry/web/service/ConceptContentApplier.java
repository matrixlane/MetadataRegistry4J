package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Concept;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.web.dto.common.ItemRegistrationRequest;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 通用的 Item / Concept 内容应用器：将 designations / definitions / sourceUri /
 * 注册信息一起应用到给定实体上。
 */
@Component
@RequiredArgsConstructor
public class ConceptContentApplier {

    private final ItemAdministrationService itemAdministrationService;

    public void apply(Item item,
                      List<TextLine> designations,
                      List<TextLine> definitions,
                      ItemRegistrationRequest registration) {
        itemAdministrationService.applyDesignations(item, designations);
        itemAdministrationService.applyDefinitions(item, definitions);
        if (registration != null) {
            itemAdministrationService.ensureAdministeredItem(item, registration);
        }
    }

    public void applyConcept(Concept concept,
                             String sourceUri,
                             List<TextLine> designations,
                             List<TextLine> definitions,
                             ItemRegistrationRequest registration) {
        concept.setSourceUri(sourceUri);
        apply(concept, designations, definitions, registration);
    }
}
