package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Context;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Definition;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Designation;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.DesignationDefinitionPairing;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
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
import java.util.stream.Stream;

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
        if (request.contextId() == null) {
            throw new BusinessException("指称-定义配对必须关联一个上下文（contextId）");
        }
        DesignationDefinitionPairing pairing = new DesignationDefinitionPairing();
        Designation designation = designationRepository.findById(request.designationId())
                .orElseThrow(() -> new ResourceNotFoundException("Designation", request.designationId()));
        validatePreferredPairingUniqueness(designation, request.contextId(), null);
        applyRequest(pairing, request);
        return toResponse(pairingRepository.save(pairing));
    }

    @Transactional
    public DesignationDefinitionPairingResponse update(UUID id, DesignationDefinitionPairingRequest request) {
        DesignationDefinitionPairing pairing = pairingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DesignationDefinitionPairing", id));
        if (request.contextId() == null) {
            throw new BusinessException("指称-定义配对必须关联一个上下文（contextId）");
        }
        validatePreferredPairingUniqueness(pairing.getDesignation(), request.contextId(), id);
        applyRequest(pairing, request);
        return toResponse(pairingRepository.save(pairing));
    }

    /**
     * 删除配对。
     *
     * @param deleteLines false = 仅删除关联关系：留下的指称/定义仍须关联至少一个
     *                    上下文（自身 designation_contexts 或其它配对），否则拒绝；
     *                    true  = 连同指称/定义一并删除：若指称/定义仍被其它配对
     *                    引用则拒绝（可退而仅删关联）。
     */
    @Transactional
    public void delete(UUID id, boolean deleteLines) {
        DesignationDefinitionPairing pairing = pairingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DesignationDefinitionPairing", id));
        Designation designation = pairing.getDesignation();
        Definition definition = pairing.getDefinition();

        if (deleteLines) {
            if (designation != null && hasOtherPairing(designation.getDesignationDefinitionPairings(), id)) {
                throw new BusinessException(ApiCodes.CONFLICT,
                        "指称「" + designationValue(designation) + "」仍被其它配对引用，无法一并删除；"
                                + "可改为仅删除关联关系");
            }
            if (definition != null && hasOtherPairing(definition.getDesignationDefinitionPairings(), id)) {
                throw new BusinessException(ApiCodes.CONFLICT,
                        "定义「" + definitionValue(definition) + "」仍被其它配对引用，无法一并删除；"
                                + "可改为仅删除关联关系");
            }
        } else {
            if (designation != null && designation.getDesignationContexts().isEmpty()
                    && !hasOtherPairing(designation.getDesignationDefinitionPairings(), id)) {
                throw new BusinessException(ApiCodes.CONFLICT,
                        "删除关联后，指称「" + designationValue(designation)
                                + "」将不再关联任何上下文；请改为连同指称一并删除");
            }
            if (definition != null && definition.getDefinitionContexts().isEmpty()
                    && !hasOtherPairing(definition.getDesignationDefinitionPairings(), id)) {
                throw new BusinessException(ApiCodes.CONFLICT,
                        "删除关联后，定义「" + definitionValue(definition)
                                + "」将不再关联任何上下文；请改为连同定义一并删除");
            }
        }

        pairingRepository.delete(pairing);
        if (deleteLines) {
            if (designation != null) {
                designationRepository.delete(designation);
            }
            if (definition != null) {
                definitionRepository.delete(definition);
            }
        }
    }

    private boolean hasOtherPairing(List<DesignationDefinitionPairing> pairings, UUID excludeId) {
        if (pairings == null) {
            return false;
        }
        return pairings.stream().anyMatch(p -> !excludeId.equals(p.getId()));
    }

    private String designationValue(Designation d) {
        return d.getSign() == null ? "" : d.getSign().getValue();
    }

    private String definitionValue(Definition d) {
        return d.getText() == null ? "" : d.getText().getValue();
    }

    private void applyRequest(DesignationDefinitionPairing pairing, DesignationDefinitionPairingRequest request) {
        Designation d = pairing.getDesignation() != null ? pairing.getDesignation()
                : designationRepository.findById(request.designationId())
                        .orElseThrow(() -> new ResourceNotFoundException("Designation", request.designationId()));
        Definition def = definitionRepository.findById(request.definitionId())
                .orElseThrow(() -> new ResourceNotFoundException("Definition", request.definitionId()));
        Context ctx = contextRepository.findById(request.contextId())
                .orElseThrow(() -> new ResourceNotFoundException("Context", request.contextId()));

        pairing.setDesignation(d);
        pairing.setDefinition(def);
        pairing.setContext(ctx);
        pairing.setAcceptability(request.acceptability());
    }

    private void validatePreferredPairingUniqueness(Designation designation, UUID contextId, UUID excludePairingId) {
        if (designation == null || designation.getItem() == null || contextId == null) return;
        UUID itemId = designation.getItem().getId();
        List<DesignationDefinitionPairing> existing = pairingRepository.findByDesignation_Item_Id(itemId);
        boolean conflict = existing.stream()
                .filter(p -> excludePairingId == null || !excludePairingId.equals(p.getId()))
                .filter(p -> contextId.equals(p.getContext() == null ? null : p.getContext().getId()))
                .filter(p -> p.getAcceptability() == io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Acceptability.PREFERRED)
                .anyMatch(p -> true);
        if (conflict) {
            throw new BusinessException(ApiCodes.CONFLICT,
                    "同一项目+上下文下已存在首选 (PREFERRED) 配对，contextId=" + contextId);
        }
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
