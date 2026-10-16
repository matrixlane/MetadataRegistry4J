
package io.github.matrixlane.metadataregistry.web.service.namespace;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Designation;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.NamingConvention;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.repository.DesignationRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.repository.ScopedIdentifierRepository;
import io.github.matrixlane.metadataregistry.web.service.namingconvention.NamingConflictDetector;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NamespaceConstraintValidatorTest {

    @Mock private NamespaceRepository namespaceRepository;
    @Mock private DesignationRepository designationRepository;
    @Mock private ScopedIdentifierRepository scopedIdentifierRepository;
    @Mock private NamingConflictDetector namingConflictDetector;

    @InjectMocks
    private NamespaceConstraintValidator validator;

    private UUID namespaceId;
    private Namespace namespace;

    @BeforeEach
    void setUp() {
        namespaceId = UUID.randomUUID();
        namespace = new Namespace();
        namespace.setId(namespaceId);
        lenient().when(namespaceRepository.findById(namespaceId)).thenReturn(Optional.of(namespace));
    }

    @Test
    void validateNamespace_shouldPassWhenMandatoryFalseAndNoConventions() {
        namespace.setMandatoryNamingConventionIndicator(false);
        assertThatNoException().isThrownBy(() -> validator.validateNamespace(namespace));
    }

    @Test
    void validateNamespace_shouldPassWhenMandatoryTrueAndHasConventions() {
        namespace.setMandatoryNamingConventionIndicator(true);
        NamingConvention nc = new NamingConvention();
        nc.setId(UUID.randomUUID());
        namespace.getNamingConventions().add(nc);
        assertThatNoException().isThrownBy(() -> validator.validateNamespace(namespace));
    }

    @Test
    void validateNamespace_shouldFailWhenMandatoryTrueAndNoConventions() {
        namespace.setMandatoryNamingConventionIndicator(true);
        assertThatThrownBy(() -> validator.validateNamespace(namespace))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须至少关联一个 NamingConvention");
    }

    @Test
    void validateDesignation_shouldFailWhenMandatoryNamingConventionMissing() {
        namespace.setMandatoryNamingConventionIndicator(true);
        NamingConvention nc = new NamingConvention();
        nc.setId(UUID.randomUUID());
        namespace.getNamingConventions().add(nc);

        Designation designation = createDesignation("test");

        assertThatThrownBy(() -> validator.validateDesignation(namespaceId, designation, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Designation 必须关联 NamingConvention");
    }

    @Test
    void validateDesignation_shouldFailWhenConformsToWrongConvention() {
        namespace.setMandatoryNamingConventionIndicator(true);
        NamingConvention allowed = new NamingConvention();
        allowed.setId(UUID.randomUUID());
        namespace.getNamingConventions().add(allowed);

        NamingConvention other = new NamingConvention();
        other.setId(UUID.randomUUID());
        Designation designation = createDesignation("test");
        designation.getNamingConventions().add(other);

        assertThatThrownBy(() -> validator.validateDesignation(namespaceId, designation, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不在 Namespace");
    }

    @Test
    void validateDesignation_shouldPassWhenConformsToAllowedConvention() {
        namespace.setMandatoryNamingConventionIndicator(true);
        NamingConvention allowed = new NamingConvention();
        allowed.setId(UUID.randomUUID());
        namespace.getNamingConventions().add(allowed);

        Designation designation = createDesignation("test");
        designation.getNamingConventions().add(allowed);

        assertThatNoException().isThrownBy(() -> validator.validateDesignation(namespaceId, designation, null));
    }

    @Test
    void validateDesignation_shouldFailWhenOneNamePerItemViolated() {
        namespace.setOneNamePerItemIndicator(true);
        UUID itemId = UUID.randomUUID();
        Designation existing = createDesignation("existing");
        existing.setId(UUID.randomUUID());
        existing.getNamespaces().add(namespace);
        io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item item = new io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item() {};
        item.setId(itemId);
        existing.setItem(item);

        Designation newDesignation = createDesignation("new");
        newDesignation.setItem(item);

        when(designationRepository.findByItem_Id(itemId)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> validator.validateDesignation(namespaceId, newDesignation, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已存在 Designation");
    }

    @Test
    void validateDesignation_shouldFailWhenOneItemPerNameViolated() {
        namespace.setOneItemPerNameIndicator(true);
        Designation designation = createDesignation("duplicate");
        when(namingConflictDetector.hasConflict(namespaceId, Sign.of("duplicate"), null))
                .thenReturn(true);

        assertThatThrownBy(() -> validator.validateDesignation(namespaceId, designation, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被其他 Item 使用");
    }

    @Test
    void validateScopedIdentifier_shouldFailWhenOneNamePerItemViolated() {
        namespace.setOneNamePerItemIndicator(true);
        UUID identifiedItemId = UUID.randomUUID();
        AdministeredItem ii = new AdministeredItem();
        ii.setCreationDateTime(LocalDateTime.now());
        ii.setId(identifiedItemId);
        ScopedIdentifier existing = new ScopedIdentifier();
        existing.setId(UUID.randomUUID());
        existing.setNamespace(namespace);
        existing.setIdentifiedItem(ii);

        ScopedIdentifier newSi = new ScopedIdentifier();
        newSi.setIdentifiedItem(ii);

        when(scopedIdentifierRepository.findByIdentifiedItemId(identifiedItemId))
                .thenReturn(List.of(existing));

        assertThatThrownBy(() -> validator.validateScopedIdentifier(namespaceId, newSi, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已存在 ScopedIdentifier");
    }

    @Test
    void validateScopedIdentifier_shouldFailWhenOneItemPerNameViolated() {
        namespace.setOneItemPerNameIndicator(true);
        ScopedIdentifier si = new ScopedIdentifier();
        si.setIdentifier("duplicate");
        si.setIdentifiedItem(new AdministeredItem());
        si.getIdentifiedItem().setId(UUID.randomUUID());
        when(namingConflictDetector.hasIdentifierConflict(namespaceId, "duplicate", null))
                .thenReturn(true);

        assertThatThrownBy(() -> validator.validateScopedIdentifier(namespaceId, si, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被其他 IdentifiedItem 使用");
    }

    private Designation createDesignation(String value) {
        Designation d = new Designation();
        d.setId(UUID.randomUUID());
        d.setSign(Sign.of(value));
        return d;
    }
}
