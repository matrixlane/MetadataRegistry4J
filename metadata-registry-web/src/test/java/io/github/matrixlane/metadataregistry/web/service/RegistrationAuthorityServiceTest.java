package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.Registrar;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.web.auth.AccountRepository;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.registrationauthority.RegistrationAuthorityRequest;
import io.github.matrixlane.metadataregistry.web.dto.registrationauthority.RegistrationAuthorityResponse;
import io.github.matrixlane.metadataregistry.web.edition.DeploymentEdition;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

    @Mock
    private RegistrarService registrarService;

    @Mock
    private ContactAccountBindingService contactAccountBindingService;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private RegistrationAuthorityDeletionService deletionService;

    @InjectMocks
    private RegistrationAuthorityService service;

    private static final UUID ACCOUNT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    private RegistrationAuthorityRequest request() {
        return new RegistrationAuthorityRequest(
                "Test RA", "Test RA English", "Address", List.of(), List.of(), "http://test.com",
                null, null, null, null, null, List.of()
        );
    }

    private void stubSaveWithId() {
        when(repository.save(any(RegistrationAuthority.class))).thenAnswer(i -> {
            RegistrationAuthority ra = i.getArgument(0);
            ra.setId(UUID.randomUUID());
            return ra;
        });
    }

    @Test
    void create_teamEditionSingleModeWithoutAuthoritySucceeds() {
        when(systemSettingService.getEdition()).thenReturn(DeploymentEdition.TEAM);
        when(systemSettingService.getRunningMode()).thenReturn("SINGLE");
        when(repository.count()).thenReturn(0L);
        stubSaveWithId();

        RegistrationAuthorityResponse response = service.create(request(), ACCOUNT_ID, SystemRole.SUPER_ADMIN);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Test RA");
        verify(repository).save(any(RegistrationAuthority.class));
        // registration-process-designer：RA 创建路径接入标准流程规则播种
        verify(registrationProcessService).ensureSeed(any());
        // 团队版不自动建立注册人员
        verify(registrarService, never()).createRegistrarForUser(any(), any(), any(), any(), any(), any());
    }

    @Test
    void create_teamEditionSingleModeWithExistingAuthorityRejected() {
        when(systemSettingService.getEdition()).thenReturn(DeploymentEdition.TEAM);
        when(systemSettingService.getRunningMode()).thenReturn("SINGLE");
        when(repository.count()).thenReturn(1L);

        assertThatThrownBy(() -> service.create(request(), ACCOUNT_ID, SystemRole.SUPER_ADMIN))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("单注册表模式下只能创建一个注册机构");

        verify(repository, never()).save(any(RegistrationAuthority.class));
    }

    @Test
    void create_unsetEditionTreatedAsTeam_rejectsInSingleMode() {
        when(systemSettingService.getEdition()).thenReturn(null);
        when(systemSettingService.getRunningMode()).thenReturn("SINGLE");
        when(repository.count()).thenReturn(1L);

        assertThatThrownBy(() -> service.create(request(), ACCOUNT_ID, SystemRole.SUPER_ADMIN))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("单注册表模式下只能创建一个注册机构");

        verify(repository, never()).save(any(RegistrationAuthority.class));
    }

    @Test
    void create_teamEditionMultipleModeSucceedsEvenWhenAuthorityExists() {
        when(systemSettingService.getEdition()).thenReturn(DeploymentEdition.TEAM);
        when(systemSettingService.getRunningMode()).thenReturn("MULTIPLE");
        stubSaveWithId();

        RegistrationAuthorityResponse response = service.create(request(), ACCOUNT_ID, SystemRole.SUPER_ADMIN);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Test RA");
        verify(repository).save(any(RegistrationAuthority.class));
    }

    @Test
    void create_personalEditionSuperAdminSucceedsWithoutCountLimit() {
        when(systemSettingService.getEdition()).thenReturn(DeploymentEdition.PERSONAL);
        stubSaveWithId();
        Account account = new Account();
        account.setId(ACCOUNT_ID);
        account.setUsername("admin");
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        Registrar registrar = new Registrar();
        registrar.setId(UUID.randomUUID());
        when(registrarService.createRegistrarForUser(eq(ACCOUNT_ID), eq("admin"), isNull(), eq("注册管理员"),
                any(UUID.class), any(RegistrationAuthority.class))).thenReturn(registrar);
        // 未 stub hasAnyContact：Mockito 默认返回 false，即"首个身份"分支

        RegistrationAuthorityResponse response = service.create(request(), ACCOUNT_ID, SystemRole.SUPER_ADMIN);

        assertThat(response).isNotNull();
        // 个人版不设数量限制：不得查询机构数量
        verify(repository, never()).count();
        verify(registrarService).createRegistrarForUser(eq(ACCOUNT_ID), eq("admin"), isNull(), eq("注册管理员"),
                any(UUID.class), any(RegistrationAuthority.class));
        // 首个身份须标为默认
        verify(contactAccountBindingService).markDefault(ACCOUNT_ID, registrar.getId());
    }

    @Test
    void create_personalEditionSuperAdminNotFirstIdentityDoesNotMarkDefault() {
        when(systemSettingService.getEdition()).thenReturn(DeploymentEdition.PERSONAL);
        stubSaveWithId();
        Account account = new Account();
        account.setId(ACCOUNT_ID);
        account.setUsername("admin");
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        Registrar registrar = new Registrar();
        registrar.setId(UUID.randomUUID());
        when(registrarService.createRegistrarForUser(eq(ACCOUNT_ID), anyString(), isNull(), eq("注册管理员"),
                any(UUID.class), any(RegistrationAuthority.class))).thenReturn(registrar);
        when(contactAccountBindingService.hasAnyContact(ACCOUNT_ID)).thenReturn(true);

        service.create(request(), ACCOUNT_ID, SystemRole.SUPER_ADMIN);

        verify(contactAccountBindingService, never()).markDefault(any(), any());
    }

    @Test
    void create_personalEditionNonSuperAdminRejected() {
        when(systemSettingService.getEdition()).thenReturn(DeploymentEdition.PERSONAL);

        assertThatThrownBy(() -> service.create(request(), ACCOUNT_ID, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("超级管理员");

        verify(repository, never()).save(any(RegistrationAuthority.class));
        verify(registrarService, never()).createRegistrarForUser(any(), any(), any(), any(), any(), any());
    }

    @Test
    void create_personalEditionMissingAccountRejected() {
        when(systemSettingService.getEdition()).thenReturn(DeploymentEdition.PERSONAL);
        stubSaveWithId();
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request(), ACCOUNT_ID, SystemRole.SUPER_ADMIN))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_personalEditionPrimaryRejected() {
        UUID id = UUID.randomUUID();
        RegistrationAuthority ra = new RegistrationAuthority();
        ra.setId(id);
        ra.setPrimary(true);
        when(systemSettingService.getEdition()).thenReturn(DeploymentEdition.PERSONAL);
        when(repository.findById(id)).thenReturn(Optional.of(ra));

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("主要注册机构不允许删除");

        verify(deletionService, never()).delete(any());
    }

    @Test
    void delete_personalEditionNonPrimaryDelegatesToDeletionService() {
        UUID id = UUID.randomUUID();
        RegistrationAuthority ra = new RegistrationAuthority();
        ra.setId(id);
        ra.setPrimary(false);
        when(systemSettingService.getEdition()).thenReturn(DeploymentEdition.PERSONAL);
        when(repository.findById(id)).thenReturn(Optional.of(ra));

        service.delete(id);

        verify(deletionService).delete(id);
    }

    @Test
    void delete_teamEditionSingleModeRejected() {
        UUID id = UUID.randomUUID();
        RegistrationAuthority ra = new RegistrationAuthority();
        ra.setId(id);
        when(systemSettingService.getEdition()).thenReturn(DeploymentEdition.TEAM);
        when(systemSettingService.getRunningMode()).thenReturn("SINGLE");
        when(repository.findById(id)).thenReturn(Optional.of(ra));

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("单注册表模式下不允许删除注册机构");

        verify(deletionService, never()).delete(any());
    }

    @Test
    void delete_teamEditionMultipleModeDelegatesToDeletionService() {
        UUID id = UUID.randomUUID();
        RegistrationAuthority ra = new RegistrationAuthority();
        ra.setId(id);
        when(systemSettingService.getEdition()).thenReturn(DeploymentEdition.TEAM);
        when(systemSettingService.getRunningMode()).thenReturn("MULTIPLE");
        when(repository.findById(id)).thenReturn(Optional.of(ra));

        service.delete(id);

        verify(deletionService).delete(id);
    }

    @Test
    void delete_unsetEditionTreatedAsTeam_rejectsInSingleMode() {
        UUID id = UUID.randomUUID();
        RegistrationAuthority ra = new RegistrationAuthority();
        ra.setId(id);
        when(systemSettingService.getEdition()).thenReturn(null);
        when(systemSettingService.getRunningMode()).thenReturn("SINGLE");
        when(repository.findById(id)).thenReturn(Optional.of(ra));

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("单注册表模式下不允许删除注册机构");

        verify(deletionService, never()).delete(any());
    }

    @Test
    void delete_notFoundThrows404() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(deletionService, never()).delete(any());
    }
}
