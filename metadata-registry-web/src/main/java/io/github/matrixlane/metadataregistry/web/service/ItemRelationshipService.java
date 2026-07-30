package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.DesignationDefinitionPairing;
import io.github.matrixlane.metadataregistry.web.dto.designationdefinition.DefinitionResponse;
import io.github.matrixlane.metadataregistry.web.dto.designationdefinition.DesignationDefinitionPairingResponse;
import io.github.matrixlane.metadataregistry.web.dto.designationdefinition.DesignationResponse;
import io.github.matrixlane.metadataregistry.web.dto.designationdefinition.ItemRelationshipContextResponse;
import io.github.matrixlane.metadataregistry.web.dto.designationdefinition.ItemRelationshipResponse;
import io.github.matrixlane.metadataregistry.web.repository.DesignationDefinitionPairingRepository;
import io.github.matrixlane.metadataregistry.web.util.ItemNamingHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemRelationshipService {

    private final DesignationService designationService;
    private final DefinitionService definitionService;
    private final DesignationDefinitionPairingRepository pairingRepository;

    public ItemRelationshipResponse getItemRelationships(UUID itemId) {
        List<DesignationResponse> designations = designationService.listByItemId(itemId);
        List<DefinitionResponse> definitions = definitionService.listByItemId(itemId);
        List<DesignationDefinitionPairing> pairings = pairingRepository.findByDesignation_Item_Id(itemId);

        Map<UUID, ItemRelationshipContextResponse> contextMap = new LinkedHashMap<>();
        for (DesignationResponse d : designations) {
            if (d.contexts() != null) {
                for (DesignationResponse.ContextResponse c : d.contexts()) {
                    if (c.contextId() != null) {
                        contextMap.put(c.contextId(), new ItemRelationshipContextResponse(c.contextId(), c.contextLabel()));
                    }
                }
            }
        }
        for (DefinitionResponse d : definitions) {
            if (d.contexts() != null) {
                for (DefinitionResponse.ContextResponse c : d.contexts()) {
                    if (c.contextId() != null) {
                        contextMap.put(c.contextId(), new ItemRelationshipContextResponse(c.contextId(), c.contextLabel()));
                    }
                }
            }
        }
        for (DesignationDefinitionPairing p : pairings) {
            if (p.getContext() != null) {
                UUID cid = p.getContext().getId();
                String label = ItemNamingHelper.preferredDesignation(p.getContext());
                contextMap.put(cid, new ItemRelationshipContextResponse(cid, label));
            }
        }

        List<DesignationDefinitionPairingResponse> pairingResponses = pairings.stream()
                .map(p -> new DesignationDefinitionPairingResponse(
                        p.getId(),
                        p.getDesignation() == null ? null : p.getDesignation().getId(),
                        p.getDesignation() == null || p.getDesignation().getSign() == null
                                ? null : p.getDesignation().getSign().getValue(),
                        p.getDefinition() == null ? null : p.getDefinition().getId(),
                        p.getDefinition() == null || p.getDefinition().getText() == null
                                ? null : p.getDefinition().getText().getValue(),
                        p.getContext() == null ? null : p.getContext().getId(),
                        p.getContext() == null ? null : ItemNamingHelper.preferredDesignation(p.getContext()),
                        p.getAcceptability() == null ? null : p.getAcceptability().name()))
                .toList();

        return new ItemRelationshipResponse(
                designations,
                definitions,
                new ArrayList<>(contextMap.values()),
                pairingResponses);
    }
}
