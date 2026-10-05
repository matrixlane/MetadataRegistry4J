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
    void create_teamEditionSucceeds() {
        when(systemSettingService.getEdition()).thenReturn(DeploymentEdition.TEAM);
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
    void create_teamEditionWithExistingAuthoritySucceeds() {
        // 系统不存在运行模式概念：团队版已存在一个机构时再创建无任何限制
        when(systemSettingService.getEdition()).thenReturn(DeploymentEdition.TEAM);
        stubSaveWithId();

        RegistrationAuthorityResponse response = service.create(request(), ACCOUNT_ID, SystemRole.SUPER_ADMIN);

        assertThat(response).isNotNull();
        verify(repository).save(any(RegistrationAuthority.class));
    }

    @Test
    void create_unsetEditionTreatedAsTeam_succeedsWithExistingAuthority() {
        when(systemSettingService.getEdition()).thenReturn(null);
        stubSaveWithId();

        RegistrationAuthorityResponse response = service.create(request(), ACCOUNT_ID, SystemRole.SUPER_ADMIN);

        assertThat(response).isNotNull();
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
        // M4：角色门槛先于版本治理判定——非 SUPER_ADMIN 直接拒绝
        assertThatThrownBy(() -> service.create(request(), ACCOUNT_ID, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("超级管理员");

        verify(repository, never()).save(any(RegistrationAuthority.class));
        verify(registrarService, never()).createRegistrarForUser(any(), any(), any(), any(), any(), any());
    }

    @Test
    void create_teamEditionNonSuperAdminRejected() {
        // M4：创建注册机构要求 SUPER_ADMIN（不分版本）；SUBMITTER/REGISTRAR 业务角色
        //（SystemRole 非 SUPER_ADMIN）一律拒绝（角色门槛先于版本治理判定）
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

        assertThatThrownBy(() -> service.delete(id, SystemRole.SUPER_ADMIN))
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

        service.delete(id, SystemRole.SUPER_ADMIN);

        verify(deletionService).delete(id);
    }

    @Test
    void delete_teamEditionSingleModeNonPrimarySucceeds() {
        // 删除保护仅按版本判定——TEAM（含版本未知按 TEAM）无任何模式门槛，
        // SINGLE 只约束创建数量；非 Primary 机构在 SINGLE 下亦可删除
        UUID id = UUID.randomUUID();
        RegistrationAuthority ra = new RegistrationAuthority();
        ra.setId(id);
        when(systemSettingService.getEdition()).thenReturn(DeploymentEdition.TEAM);
        when(repository.findById(id)).thenReturn(Optional.of(ra));

        service.delete(id, SystemRole.SUPER_ADMIN);

        verify(deletionService).delete(id);
    }

    @Test
    void delete_unsetEditionTreatedAsTeam_singleModeNonPrimarySucceeds() {
        // 版本未知保守按 TEAM：删除同样不受任何模式约束
        UUID id = UUID.randomUUID();
        RegistrationAuthority ra = new RegistrationAuthority();
        ra.setId(id);
        when(systemSettingService.getEdition()).thenReturn(null);
        when(repository.findById(id)).thenReturn(Optional.of(ra));

        service.delete(id, SystemRole.SUPER_ADMIN);

        verify(deletionService).delete(id);
    }

    @Test
    void delete_nonSuperAdminRejected() {
        // M4：删除注册机构要求 SUPER_ADMIN；SUBMITTER/REGISTRAR 业务角色拒绝
        // （角色门槛先于存在性检查，findById 不应被调用）
        assertThatThrownBy(() -> service.delete(UUID.randomUUID(), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("超级管理员");

        verify(deletionService, never()).delete(any());
        verify(repository, never()).findById(any());
    }

    @Test
    void update_nonSuperAdminRejected() {
        // M4：更新注册机构要求 SUPER_ADMIN（角色门槛先于存在性检查）
        assertThatThrownBy(() -> service.update(UUID.randomUUID(), request(), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("超级管理员");

        verify(repository, never()).save(any(RegistrationAuthority.class));
        verify(repository, never()).findById(any());
    }

    @Test
    void update_superAdminSucceeds() {
        UUID id = UUID.randomUUID();
        RegistrationAuthority ra = new RegistrationAuthority();
        ra.setId(id);
        when(repository.findById(id)).thenReturn(Optional.of(ra));
        when(repository.save(any(RegistrationAuthority.class))).thenAnswer(i -> i.getArgument(0));

        RegistrationAuthorityResponse response = service.update(id, request(), SystemRole.SUPER_ADMIN);

        assertThat(response).isNotNull();
        verify(repository).save(any(RegistrationAuthority.class));
    }

    @Test
    void listAndGet_fillDeletableByEditionAndPrimary() {
        // m6：deletable = !(个人版 && Primary)；版本未知按个人版保守
        UUID primaryId = UUID.randomUUID();
        RegistrationAuthority primary = new RegistrationAuthority();
        primary.setId(primaryId);
        primary.setPrimary(true);
        primary.getName().add(io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign.of("主要机构"));
        UUID normalId = UUID.randomUUID();
        RegistrationAuthority normal = new RegistrationAuthority();
        normal.setId(normalId);
        normal.getName().add(io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign.of("普通机构"));
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(primary, normal)));

        // 个人版：Primary 不可删，其余可删
        when(systemSettingService.getEdition()).thenReturn(DeploymentEdition.PERSONAL);
        var personalPage = service.list(null, org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(personalPage.getContent().get(0).deletable()).isFalse();
        assertThat(personalPage.getContent().get(1).deletable()).isTrue();
        when(repository.findById(primaryId)).thenReturn(Optional.of(primary));
        assertThat(service.get(primaryId).deletable()).isFalse();

        // 团队版：无 Primary 删除保护，均可删
        when(systemSettingService.getEdition()).thenReturn(DeploymentEdition.TEAM);
        var teamPage = service.list(null, org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(teamPage.getContent().get(0).deletable()).isTrue();

        // 版本未知：按个人版保守
        when(systemSettingService.getEdition()).thenReturn(null);
        var unsetPage = service.list(null, org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(unsetPage.getContent().get(0).deletable()).isFalse();
    }

    @Test
    void delete_teamEditionMultipleModeDelegatesToDeletionService() {
        UUID id = UUID.randomUUID();
        RegistrationAuthority ra = new RegistrationAuthority();
        ra.setId(id);
        when(systemSettingService.getEdition()).thenReturn(DeploymentEdition.TEAM);
        when(repository.findById(id)).thenReturn(Optional.of(ra));

        service.delete(id, SystemRole.SUPER_ADMIN);

        verify(deletionService).delete(id);
    }

    @Test
    void delete_notFoundThrows404() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id, SystemRole.SUPER_ADMIN))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(deletionService, never()).delete(any());
    }
}
