package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Designation;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.common.ItemMetaResponse;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLineResponse;
import io.github.matrixlane.metadataregistry.web.dto.namespace.NamespaceRequest;
import io.github.matrixlane.metadataregistry.web.dto.namespace.NamespaceResponse;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NamespaceServiceTest {

    @Mock
    private NamespaceRepository repository;

    @Mock
    private RegistrationAuthorityRepository registrationAuthorityRepository;

    @Mock
    private ItemAdministrationService itemAdministrationService;

    @InjectMocks
    private NamespaceService service;

    private UUID namespaceId;
    private UUID raId;
    private Namespace namespace;
    private RegistrationAuthority registrationAuthority;

    @BeforeEach
    void setUp() {
        namespaceId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        raId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

        registrationAuthority = new RegistrationAuthority();
        registrationAuthority.setId(raId);
        Sign sign = new Sign();
        sign.setValue("Test RA");
        registrationAuthority.setName(List.of(sign));

        namespace = new Namespace();
        namespace.setId(namespaceId);
        namespace.setRegistrationAuthority(registrationAuthority);
    }

    @Test
    void list_shouldReturnPageWithoutKeyword() {
        Page<Namespace> page = new PageImpl<>(List.of(namespace));
        when(repository.findAll(any(Specification.class), eq(PageRequest.of(0, 10)))).thenReturn(page);
        when(itemAdministrationService.mapDesignations(any())).thenReturn(List.of());
        when(itemAdministrationService.mapDefinitions(any())).thenReturn(List.of());
        when(itemAdministrationService.buildItemMeta(any())).thenReturn(mockItemMeta());

        Page<NamespaceResponse> result = service.list(null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).id()).isEqualTo(namespaceId);
    }

    @Test
    void list_shouldReturnPageWithKeyword() {
        Page<Namespace> page = new PageImpl<>(List.of(namespace));
        when(repository.findAll(any(Specification.class), eq(PageRequest.of(0, 10)))).thenReturn(page);
        when(itemAdministrationService.mapDesignations(any())).thenReturn(List.of());
        when(itemAdministrationService.mapDefinitions(any())).thenReturn(List.of());
        when(itemAdministrationService.buildItemMeta(any())).thenReturn(mockItemMeta());

        Page<NamespaceResponse> result = service.list("test", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void get_shouldReturnNamespace() {
        when(repository.findById(namespaceId)).thenReturn(Optional.of(namespace));
        when(itemAdministrationService.mapDesignations(any())).thenReturn(List.of());
        when(itemAdministrationService.mapDefinitions(any())).thenReturn(List.of());
        when(itemAdministrationService.buildItemMeta(any())).thenReturn(mockItemMeta());

        NamespaceResponse result = service.get(namespaceId);

        assertThat(result.id()).isEqualTo(namespaceId);
        assertThat(result.namingAuthorityId()).isEqualTo(raId);
        assertThat(result.namingAuthorityName()).isEqualTo("Test RA");
    }

    @Test
    void get_shouldThrowWhenNotFound() {
        when(repository.findById(namespaceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(namespaceId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Namespace");
    }

    @Test
    void create_shouldSaveAndReturnNamespace() {
        NamespaceRequest request = new NamespaceRequest(raId, List.of(), List.of(), null);

        when(registrationAuthorityRepository.findById(raId)).thenReturn(Optional.of(registrationAuthority));
        when(repository.save(any(Namespace.class))).thenAnswer(i -> {
            Namespace ns = i.getArgument(0);
            ns.setId(namespaceId);
            return ns;
        });
        when(itemAdministrationService.mapDesignations(any())).thenReturn(List.of());
        when(itemAdministrationService.mapDefinitions(any())).thenReturn(List.of());
        when(itemAdministrationService.buildItemMeta(any())).thenReturn(mockItemMeta());

        NamespaceResponse result = service.create(request);

        assertThat(result.id()).isEqualTo(namespaceId);
        assertThat(result.namingAuthorityId()).isEqualTo(raId);
        verify(itemAdministrationService).applyDesignations(any(), eq(request.designations()));
        verify(itemAdministrationService).applyDefinitions(any(), eq(request.definitions()));
    }

    @Test
    void create_shouldThrowWhenRegistrationAuthorityNotFound() {
        NamespaceRequest request = new NamespaceRequest(raId, List.of(), List.of(), null);
        when(registrationAuthorityRepository.findById(raId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("RegistrationAuthority");
    }

    @Test
    void update_shouldModifyAndReturnNamespace() {
        NamespaceRequest request = new NamespaceRequest(raId, List.of(), List.of(), null);

        when(repository.findById(namespaceId)).thenReturn(Optional.of(namespace));
        when(registrationAuthorityRepository.findById(raId)).thenReturn(Optional.of(registrationAuthority));
        when(repository.save(any(Namespace.class))).thenReturn(namespace);
        when(itemAdministrationService.mapDesignations(any())).thenReturn(List.of());
        when(itemAdministrationService.mapDefinitions(any())).thenReturn(List.of());
        when(itemAdministrationService.buildItemMeta(any())).thenReturn(mockItemMeta());

        NamespaceResponse result = service.update(namespaceId, request);

        assertThat(result.id()).isEqualTo(namespaceId);
        verify(itemAdministrationService).applyDesignations(any(), eq(request.designations()));
        verify(itemAdministrationService).applyDefinitions(any(), eq(request.definitions()));
    }

    @Test
    void update_shouldThrowWhenNamespaceNotFound() {
        NamespaceRequest request = new NamespaceRequest(null, List.of(), List.of(), null);
        when(repository.findById(namespaceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(namespaceId, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Namespace");
    }

    @Test
    void delete_shouldRemoveNamespace() {
        when(repository.existsById(namespaceId)).thenReturn(true);

        service.delete(namespaceId);

        verify(repository).deleteById(namespaceId);
    }

    @Test
    void delete_shouldThrowWhenNotFound() {
        when(repository.existsById(namespaceId)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(namespaceId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Namespace");
    }

    @Test
    void create_shouldAllowNullNamingAuthority() {
        NamespaceRequest request = new NamespaceRequest(null, List.of(), List.of(), null);

        when(repository.save(any(Namespace.class))).thenAnswer(i -> {
            Namespace ns = i.getArgument(0);
            ns.setId(namespaceId);
            return ns;
        });
        when(itemAdministrationService.mapDesignations(any())).thenReturn(List.of());
        when(itemAdministrationService.mapDefinitions(any())).thenReturn(List.of());
        when(itemAdministrationService.buildItemMeta(any())).thenReturn(mockItemMeta());

        NamespaceResponse result = service.create(request);

        assertThat(result.namingAuthorityId()).isNull();
        assertThat(result.namingAuthorityName()).isNull();
    }

    private ItemMetaResponse mockItemMeta() {
        return new ItemMetaResponse(
                namespaceId, null, null, null,
                null, null, null, null,
                null, null);
    }
}
