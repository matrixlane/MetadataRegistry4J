package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Organization;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.web.dto.organization.OrganizationOption;
import io.github.matrixlane.metadataregistry.web.repository.OrganizationRepository;
import io.github.matrixlane.metadataregistry.web.seed.SystemDefaultNamespaceInitializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * add-registrar-three-elements: OrganizationService.options() 用于"注册人员"编辑表单
 * 组织下拉选择器。验证：按 name 排序、仅返回 (id, name)。
 */
@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock
    private OrganizationRepository repository;

    @InjectMocks
    private OrganizationService service;

    private Organization named(UUID id, String name) {
        Organization o = new Organization();
        o.setId(id);
        o.getName().add(Sign.of(name));
        return o;
    }

    @Test
    void options_returnsSortedIdAndName() {
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        UUID idC = UUID.randomUUID();
        when(repository.findAll()).thenReturn(List.of(
                named(idC, "Charlie"),
                named(idA, "alpha"),
                named(idB, "Bravo")
        ));

        List<OrganizationOption> result = service.options();

        assertThat(result).extracting(OrganizationOption::id)
                .containsExactly(idA, idB, idC);
        assertThat(result).extracting(OrganizationOption::name)
                .containsExactly("alpha", "Bravo", "Charlie");
    }

    @Test
    void options_emptyRepository_returnsEmptyList() {
        when(repository.findAll()).thenReturn(List.of());

        assertThat(service.options()).isEmpty();
    }

    @Test
    void options_organizationWithNoName_yieldsNullName() {
        UUID id = UUID.randomUUID();
        Organization noName = new Organization();
        noName.setId(id);
        when(repository.findAll()).thenReturn(List.of(noName));

        List<OrganizationOption> result = service.options();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(id);
        assertThat(result.get(0).name()).isNull();
    }

    @Test
    void options_excludesSystemOrganization() {
        when(repository.findAll()).thenReturn(List.of(
                named(SystemDefaultNamespaceInitializer.SYSTEM_ORGANIZATION_ID, "system"),
                named(UUID.randomUUID(), "默认注册机构")
        ));

        List<OrganizationOption> result = service.options();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("默认注册机构");
    }

    @Test
    void delete_systemOrganization_throwsValidationError() {
        assertThatThrownBy(() -> service.delete(SystemDefaultNamespaceInitializer.SYSTEM_ORGANIZATION_ID))
                .hasMessageContaining("系统组织");
        verify(repository, never()).deleteById(any());
    }
}
