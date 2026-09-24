
package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Text;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.NamingConvention;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.common.ItemMetaResponse;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLine;
import io.github.matrixlane.metadataregistry.web.dto.namingconvention.NamingConventionRequest;
import io.github.matrixlane.metadataregistry.web.dto.namingconvention.NamingConventionResponse;
import io.github.matrixlane.metadataregistry.web.repository.NamingConventionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NamingConventionServiceTest {

    @Mock
    private NamingConventionRepository repository;

    @Mock
    private ItemAdministrationService itemAdministrationService;

    @Mock
    private NamingConventionRulesService rulesService;

    @InjectMocks
    private NamingConventionService service;

    private UUID ncId;
    private UUID langId;
    private NamingConvention namingConvention;

    @BeforeEach
    void setUp() {
        ncId = UUID.randomUUID();
        langId = UUID.randomUUID();
        namingConvention = new NamingConvention();
        namingConvention.setId(ncId);
        namingConvention.setScopeRule(Text.of("必须以 org.example 开头"));
        namingConvention.setAuthorityRule(Text.of("由架构组审批"));
        org.mockito.Mockito.lenient().when(rulesService.getOrDefault(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> new io.github.matrixlane.metadataregistry.ext.designationanddefinition.NamingConventionRules());
    }

    @Test
    void get_shouldReturnNamingConvention() {
        when(repository.findById(ncId)).thenReturn(Optional.of(namingConvention));
        when(itemAdministrationService.mapDesignations(any())).thenReturn(List.of());
        when(itemAdministrationService.mapDefinitions(any())).thenReturn(List.of());
        when(itemAdministrationService.buildItemMeta(any())).thenReturn(mockMeta());

        NamingConventionResponse result = service.get(ncId);

        assertThat(result.id()).isEqualTo(ncId);
        assertThat(result.scopeRule()).isEqualTo("必须以 org.example 开头");
        assertThat(result.authorityRule()).isEqualTo("由架构组审批");
    }

    @Test
    void get_shouldThrowWhenNotFound() {
        when(repository.findById(ncId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(ncId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void list_shouldReturnPage() {
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class), eq(PageRequest.of(0, 10))))
                .thenReturn(new PageImpl<>(List.of(namingConvention)));
        when(itemAdministrationService.mapDesignations(any())).thenReturn(List.of());
        when(itemAdministrationService.mapDefinitions(any())).thenReturn(List.of());
        when(itemAdministrationService.buildItemMeta(any())).thenReturn(mockMeta());

        Page<NamingConventionResponse> result = service.list(null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).id()).isEqualTo(ncId);
    }

    @Test
    void create_shouldSaveAndReturn() {
        NamingConventionRequest request = new NamingConventionRequest(
                "scope rule",
                "authority rule",
                null,
                null,
                null,
                null,
                List.of(new TextLine(langId, "命名规范", null, null)),
                null,
                null);

        when(repository.save(any(NamingConvention.class))).thenAnswer(i -> {
            NamingConvention nc = i.getArgument(0);
            nc.setId(ncId);
            return nc;
        });
        when(itemAdministrationService.mapDesignations(any())).thenReturn(List.of());
        when(itemAdministrationService.mapDefinitions(any())).thenReturn(List.of());
        when(itemAdministrationService.buildItemMeta(any())).thenReturn(mockMeta());

        NamingConventionResponse result = service.create(request);

        assertThat(result.id()).isEqualTo(ncId);
        assertThat(result.scopeRule()).isEqualTo("scope rule");
        assertThat(result.authorityRule()).isEqualTo("authority rule");
    }

    @Test
    void update_shouldModifyAndReturn() {
        NamingConventionRequest request = new NamingConventionRequest(
                "updated scope",
                null,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                null);

        when(repository.findById(ncId)).thenReturn(Optional.of(namingConvention));
        when(repository.save(any(NamingConvention.class))).thenReturn(namingConvention);
        when(itemAdministrationService.mapDesignations(any())).thenReturn(List.of());
        when(itemAdministrationService.mapDefinitions(any())).thenReturn(List.of());
        when(itemAdministrationService.buildItemMeta(any())).thenReturn(mockMeta());

        NamingConventionResponse result = service.update(ncId, request);

        assertThat(result.scopeRule()).isEqualTo("updated scope");
        assertThat(result.authorityRule()).isNull();
    }

    private ItemMetaResponse mockMeta() {
        return new ItemMetaResponse(
                ncId, null, null, null,
                null, null, null, null,
                null, null, null, null,
                null, null, null, null,
                null, null, null, null,
                null, null);
    }
}
