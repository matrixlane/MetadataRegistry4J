package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.dto.registrationauthority.RegistrationAuthorityRequest;
import io.github.matrixlane.metadataregistry.web.dto.registrationauthority.RegistrationAuthorityResponse;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationAuthorityServiceTest {

    @Mock
    private RegistrationAuthorityRepository repository;

    @Mock
    private NamespaceRepository namespaceRepository;

    @Mock
    private LanguageIdentificationRepository languageRepository;

    @Mock
    private SystemSettingService systemSettingService;

    @Mock
    private RegistrationProcessService registrationProcessService;

    @InjectMocks
    private RegistrationAuthorityService service;

    @Test
    void create_shouldSucceedInSingleModeWhenNoAuthorityExists() {
        RegistrationAuthorityRequest request = new RegistrationAuthorityRequest(
                "Test RA", "Test RA English", "Address", List.of(), List.of(), "http://test.com",
                null, null, null, null, null, List.of()
        );

        when(systemSettingService.getRunningMode()).thenReturn("SINGLE");
        when(repository.count()).thenReturn(0L);
        when(repository.save(any(RegistrationAuthority.class))).thenAnswer(i -> {
            RegistrationAuthority ra = i.getArgument(0);
            ra.setId(UUID.randomUUID());
            return ra;
        });

        RegistrationAuthorityResponse response = service.create(request);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Test RA");
        verify(repository).save(any(RegistrationAuthority.class));
        // registration-process-designer：RA 创建路径接入标准流程规则播种
        verify(registrationProcessService).ensureSeed(any());
    }

    @Test
    void create_shouldThrowExceptionInSingleModeWhenAuthorityAlreadyExists() {
        RegistrationAuthorityRequest request = new RegistrationAuthorityRequest(
                "Test RA", "Test RA English", "Address", List.of(), List.of(), "http://test.com",
                null, null, null, null, null, List.of()
        );

        when(systemSettingService.getRunningMode()).thenReturn("SINGLE");
        when(repository.count()).thenReturn(1L);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("单注册表模式下只能创建一个注册机构");

        verify(repository, never()).save(any(RegistrationAuthority.class));
    }

    @Test
    void create_shouldSucceedInMultipleModeEvenWhenAuthorityAlreadyExists() {
        RegistrationAuthorityRequest request = new RegistrationAuthorityRequest(
                "Test RA", "Test RA English", "Address", List.of(), List.of(), "http://test.com",
                null, null, null, null, null, List.of()
        );

        when(systemSettingService.getRunningMode()).thenReturn("MULTIPLE");
        when(repository.save(any(RegistrationAuthority.class))).thenAnswer(i -> {
            RegistrationAuthority ra = i.getArgument(0);
            ra.setId(UUID.randomUUID());
            return ra;
        });

        RegistrationAuthorityResponse response = service.create(request);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Test RA");
        verify(repository).save(any(RegistrationAuthority.class));
    }
}
