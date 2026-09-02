package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Text;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Definition;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.DefinitionContext;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Designation;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.DesignationContext;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.DesignationDefinitionPairing;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.repository.ContextRepository;
import io.github.matrixlane.metadataregistry.web.repository.DefinitionRepository;
import io.github.matrixlane.metadataregistry.web.repository.DesignationDefinitionPairingRepository;
import io.github.matrixlane.metadataregistry.web.repository.DesignationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DesignationDefinitionPairingServiceTest {

    @Mock private DesignationDefinitionPairingRepository pairingRepository;
    @Mock private DesignationRepository designationRepository;
    @Mock private DefinitionRepository definitionRepository;
    @Mock private ContextRepository contextRepository;

    @InjectMocks
    private DesignationDefinitionPairingService pairingService;

    private UUID pairingId;
    private DesignationDefinitionPairing pairing;
    private Designation designation;
    private Definition definition;

    @BeforeEach
    void setUp() {
        pairingId = UUID.randomUUID();
        designation = new Designation();
        designation.setId(UUID.randomUUID());
        designation.setSign(Sign.of("患者"));
        definition = new Definition();
        definition.setId(UUID.randomUUID());
        definition.setText(Text.of("患者定义"));
        pairing = new DesignationDefinitionPairing();
        pairing.setId(pairingId);
        pairing.setDesignation(designation);
        pairing.setDefinition(definition);
        designation.getDesignationDefinitionPairings().add(pairing);
        definition.getDesignationDefinitionPairings().add(pairing);
        when(pairingRepository.findById(pairingId)).thenReturn(Optional.of(pairing));
    }

    @Test
    void delete_associationOnly_lineStillHasContext_ok() {
        designation.getDesignationContexts().add(new DesignationContext());
        definition.getDefinitionContexts().add(new DefinitionContext());

        pairingService.delete(pairingId, false);

        verify(pairingRepository).delete(pairing);
        verify(designationRepository, never()).delete(org.mockito.ArgumentMatchers.any());
        verify(definitionRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void delete_associationOnly_lineReferencedByOtherPairing_ok() {
        // 其它配对仍引用该行 → 不孤立，仅删关联放行
        DesignationDefinitionPairing other = new DesignationDefinitionPairing();
        other.setId(UUID.randomUUID());
        designation.getDesignationDefinitionPairings().add(other);
        DesignationDefinitionPairing otherDef = new DesignationDefinitionPairing();
        otherDef.setId(UUID.randomUUID());
        definition.getDesignationDefinitionPairings().add(otherDef);

        pairingService.delete(pairingId, false);

        verify(pairingRepository).delete(pairing);
    }

    @Test
    void delete_associationOnly_pairingOnlyLine_rejected() {
        // 指称仅经本配对存在（无自身上下文）：仅删关联将使其不再关联任何上下文 → 拒绝
        assertThatThrownBy(() -> pairingService.delete(pairingId, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不再关联任何上下文");
        verify(pairingRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void delete_withLines_referencedByOtherPairing_rejected() {
        DesignationDefinitionPairing other = new DesignationDefinitionPairing();
        other.setId(UUID.randomUUID());
        designation.getDesignationDefinitionPairings().add(other);

        assertThatThrownBy(() -> pairingService.delete(pairingId, true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仍被其它配对引用");
        verify(pairingRepository, never()).delete(org.mockito.ArgumentMatchers.any());
        verify(designationRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void delete_withLines_ok() {
        pairingService.delete(pairingId, true);

        verify(pairingRepository).delete(pairing);
        verify(designationRepository).delete(designation);
        verify(definitionRepository).delete(definition);
    }

    @Test
    void delete_withLines_definitionReferencedByOtherPairing_rejected() {
        DesignationDefinitionPairing other = new DesignationDefinitionPairing();
        other.setId(UUID.randomUUID());
        definition.getDesignationDefinitionPairings().add(other);
        // 指称仅被本配对引用，可一并删除；定义被其它配对引用 → 整体拒绝
        assertThatThrownBy(() -> pairingService.delete(pairingId, true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仍被其它配对引用");
        verify(pairingRepository, never()).delete(org.mockito.ArgumentMatchers.any());
        verify(designationRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }
}
