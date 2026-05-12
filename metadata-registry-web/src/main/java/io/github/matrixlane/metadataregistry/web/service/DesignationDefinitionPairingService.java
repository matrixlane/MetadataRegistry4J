package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Context;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Definition;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Designation;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.DesignationDefinitionPairing;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.designationdefinition.DesignationDefinitionPairingRequest;
import io.github.matrixlane.metadataregistry.web.dto.designationdefinition.DesignationDefinitionPairingResponse;
import io.github.matrixlane.metadataregistry.web.repository.ContextRepository;
import io.github.matrixlane.metadataregistry.web.repository.DefinitionRepository;
import io.github.matrixlane.metadataregistry.web.repository.DesignationDefinitionPairingRepository;
import io.github.matrixlane.metadataregistry.web.repository.DesignationRepository;
import io.github.matrixlane.metadataregistry.web.util.ItemNamingHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DesignationDefinitionPairingService {

    private final DesignationDefinitionPairingRepository pairingRepository;
    private final DesignationRepository designationRepository;
    private final DefinitionRepository definitionRepository;
    private final ContextRepository contextRepository;

    @Transactional(readOnly = true)
    public List<DesignationDefinitionPairingResponse> listByItemId(UUID itemId) {
        return pairingRepository.findByDesignation_Item_Id(itemId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public DesignationDefinitionPairingResponse create(DesignationDefinitionPairingRequest request) {
        DesignationDefinitionPairing pairing = new DesignationDefinitionPairing();
        applyRequest(pairing, request);
        return toResponse(pairingRepository.save(pairing));
    }

    @Transactional
    public DesignationDefinitionPairingResponse update(UUID id, DesignationDefinitionPairingRequest request) {
        DesignationDefinitionPairing pairing = pairingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DesignationDefinitionPairing", id));
        applyRequest(pairing, request);
        return toResponse(pairingRepository.save(pairing));
    }

    @Transactional
    public void delete(UUID id) {
        pairingRepository.deleteById(id);
    }

    private void applyRequest(DesignationDefinitionPairing pairing, DesignationDefinitionPairingRequest request) {
        Designation d = designationRepository.findById(request.designationId())
                .orElseThrow(() -> new ResourceNotFoundException("Designation", request.designationId()));
        Definition def = definitionRepository.findById(request.definitionId())
                .orElseThrow(() -> new ResourceNotFoundException("Definition", request.definitionId()));
        Context ctx = request.contextId() == null ? null
                : contextRepository.findById(request.contextId())
                .orElseThrow(() -> new ResourceNotFoundException("Context", request.contextId()));

        pairing.setDesignation(d);
        pairing.setDefinition(def);
        pairing.setContext(ctx);
        pairing.setAcceptability(request.acceptability());
    }

    private DesignationDefinitionPairingResponse toResponse(DesignationDefinitionPairing pairing) {
        return new DesignationDefinitionPairingResponse(
                pairing.getId(),
                pairing.getDesignation() == null ? null : pairing.getDesignation().getId(),
                pairing.getDesignation() == null ? null
                        : (pairing.getDesignation().getSign() == null ? null
                                : pairing.getDesignation().getSign().getValue()),
                pairing.getDefinition() == null ? null : pairing.getDefinition().getId(),
                pairing.getDefinition() == null ? null
                        : (pairing.getDefinition().getText() == null ? null
                                : pairing.getDefinition().getText().getValue()),
                pairing.getContext() == null ? null : pairing.getContext().getId(),
                pairing.getContext() == null ? null : ItemNamingHelper.preferredDesignation(pairing.getContext()),
                pairing.getAcceptability() == null ? null : pairing.getAcceptability().name()
        );
    }
}
