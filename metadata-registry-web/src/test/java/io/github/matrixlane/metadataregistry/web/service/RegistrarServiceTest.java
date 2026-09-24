package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Individual;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Organization;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Role;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.Registrar;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.registrar.RegistrarRequest;
import io.github.matrixlane.metadataregistry.web.dto.registrar.RegistrarResponse;
import io.github.matrixlane.metadataregistry.web.auth.AccountRepository;
import io.github.matrixlane.metadataregistry.web.repository.IndividualRepository;
import io.github.matrixlane.metadataregistry.web.repository.OrganizationRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrarRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import io.github.matrixlane.metadataregistry.web.repository.RoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * add-registrar-three-elements: 注册人员三要素（个人 / 角色 / 组织）编辑与初始化场景。
 * 覆盖：
 * <ul>
 *   <li>{@link RegistrarService#applyRequest} 处理 individualId / roleId / organizationId</li>
 *   <li>{@link RegistrarService#createRegistrarForUser} 按三要素建实体并复用 Role</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class RegistrarServiceTest {

    @Mock private RegistrarRepository registrarRepository;
    @Mock private RegistrationAuthorityRepository registrationAuthorityRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private IndividualRepository individualRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private ContactAccountBindingService bindingService;

    @InjectMocks
    private RegistrarService service;

    private RegistrationAuthority ra() {
        RegistrationAuthority r = new RegistrationAuthority();
        r.setId(UUID.randomUUID());
        return r;
    }

    private Organization org(UUID id) {
        Organization o = new Organization();
        o.setId(id);
        return o;
    }

    private Role role(UUID id, String name) {
        Role role = new Role();
        role.setId(id);
        role.setRoleName(name);
        return role;
    }

    private Individual individual(UUID id) {
        Individual i = new Individual();
        i.setId(id);
        return i;
    }

    // ───────────────────────── applyRequest ─────────────────────────

    @Test
    void applyRequest_roleIdProvided_setsRole() {
        Organization organization = org(UUID.randomUUID());
        Role role = role(UUID.randomUUID(), "数据管理员");
        when(organizationRepository.findById(organization.getId())).thenReturn(Optional.of(organization));
        when(roleRepository.findById(role.getId())).thenReturn(Optional.of(role));

        RegistrarRequest req = new RegistrarRequest(
                "admin-registrar",
                null, null, organization.getId(), null, role.getId());

        service.create(req);

        ArgumentCaptor<Registrar> captor = ArgumentCaptor.forClass(Registrar.class);
        verify(registrarRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isSameAs(role);
    }

    @Test
    void applyRequest_roleIdNull_clearsRole() {
        Organization organization = org(UUID.randomUUID());
        when(organizationRepository.findById(organization.getId())).thenReturn(Optional.of(organization));

        RegistrarRequest req = new RegistrarRequest(
                "admin-registrar",
                null, null, organization.getId(), null, null);

        service.create(req);

        ArgumentCaptor<Registrar> captor = ArgumentCaptor.forClass(Registrar.class);
        verify(registrarRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isNull();
    }

    @Test
    void applyRequest_organizationIdMissing_throwsResourceNotFound() {
        // @NotNull organizationId 由 Validator 在控制器层触发；服务层在 organizationId 不在库时抛 ResourceNotFoundException。
        UUID missing = UUID.randomUUID();
        when(organizationRepository.findById(missing)).thenReturn(Optional.empty());

        RegistrarRequest req = new RegistrarRequest(
                "admin-registrar",
                null, null, missing, null, null);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void applyRequest_roleIdMissing_throwsResourceNotFound() {
        Organization organization = org(UUID.randomUUID());
        UUID missingRole = UUID.randomUUID();
        when(organizationRepository.findById(organization.getId())).thenReturn(Optional.of(organization));
        when(roleRepository.findById(missingRole)).thenReturn(Optional.empty());

        RegistrarRequest req = new RegistrarRequest(
                "admin-registrar",
                null, null, organization.getId(), null, missingRole);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void applyRequest_individualIdNull_keepsNoIndividual() {
        Organization organization = org(UUID.randomUUID());
        when(organizationRepository.findById(organization.getId())).thenReturn(Optional.of(organization));

        RegistrarRequest req = new RegistrarRequest(
                "admin-registrar",
                null, null, organization.getId(), null, null);

        service.create(req);

        ArgumentCaptor<Registrar> captor = ArgumentCaptor.forClass(Registrar.class);
        verify(registrarRepository).save(captor.capture());
        assertThat(captor.getValue().getIndividual()).isNull();
        verify(individualRepository, never()).findById(any(UUID.class));
    }

    // ─────────────────── createRegistrarForUser ───────────────────

    @Test
    void createRegistrarForUser_individualNameProvided_createsIndividual() {
        RegistrationAuthority authority = ra();
        Role registrarRole = role(UUID.randomUUID(), "注册管理员");
        when(accountRepository.findById(any(UUID.class))).thenReturn(Optional.of(new Account()));
        when(roleRepository.findByRoleNameIgnoreCase("注册管理员")).thenReturn(Optional.empty());
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> {
            Role r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });
        when(individualRepository.save(any(Individual.class))).thenAnswer(inv -> {
            Individual i = inv.getArgument(0);
            i.setId(UUID.randomUUID());
            return i;
        });
        when(registrarRepository.save(any(Registrar.class))).thenAnswer(inv -> {
            Registrar r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        Registrar saved = service.createRegistrarForUser(
                UUID.randomUUID(), "admin", "李雷", "注册管理员", null, authority);

        assertThat(saved.getIndividual()).isNotNull();
        assertThat(saved.getIndividual().getName().getValue()).isEqualTo("李雷");
        assertThat(saved.getIndividual().getIdentifier()).isEqualTo("admin");
        assertThat(saved.getRole()).isNotNull();
        assertThat(saved.getRole().getRoleName()).isEqualTo("注册管理员");
        assertThat(saved.getOrganization()).isSameAs(authority);
    }

    @Test
    void createRegistrarForUser_individualNameBlank_skipsIndividual() {
        RegistrationAuthority authority = ra();
        Role existingRole = role(UUID.randomUUID(), "注册管理员");
        when(accountRepository.findById(any(UUID.class))).thenReturn(Optional.of(new Account()));
        when(roleRepository.findByRoleNameIgnoreCase("注册管理员")).thenReturn(Optional.of(existingRole));
        when(registrarRepository.save(any(Registrar.class))).thenAnswer(inv -> {
            Registrar r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        Registrar saved = service.createRegistrarForUser(
                UUID.randomUUID(), "admin", "   ", "注册管理员", null, authority);

        assertThat(saved.getIndividual()).isNull();
        verify(individualRepository, never()).save(any(Individual.class));
    }

    @Test
    void createRegistrarForUser_roleNameExisting_reusesRole() {
        RegistrationAuthority authority = ra();
        Role existingRole = role(UUID.randomUUID(), "注册管理员");
        when(accountRepository.findById(any(UUID.class))).thenReturn(Optional.of(new Account()));
        when(roleRepository.findByRoleNameIgnoreCase("注册管理员")).thenReturn(Optional.of(existingRole));
        when(registrarRepository.save(any(Registrar.class))).thenAnswer(inv -> {
            Registrar r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        Registrar saved = service.createRegistrarForUser(
                UUID.randomUUID(), "admin", null, "注册管理员", null, authority);

        assertThat(saved.getRole()).isSameAs(existingRole);
        verify(roleRepository, never()).save(any(Role.class));
    }

    @Test
    void createRegistrarForUser_roleNameMissing_createsNewRole() {
        RegistrationAuthority authority = ra();
        when(accountRepository.findById(any(UUID.class))).thenReturn(Optional.of(new Account()));
        when(roleRepository.findByRoleNameIgnoreCase("数据治理主管")).thenReturn(Optional.empty());
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> {
            Role r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });
        when(registrarRepository.save(any(Registrar.class))).thenAnswer(inv -> {
            Registrar r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        Registrar saved = service.createRegistrarForUser(
                UUID.randomUUID(), "admin", null, "数据治理主管", null, authority);

        assertThat(saved.getRole().getRoleName()).isEqualTo("数据治理主管");
        verify(roleRepository).save(any(Role.class));
    }

    @Test
    void createRegistrarForUser_roleNameBlank_throwsValidationError() {
        RegistrationAuthority authority = ra();
        when(accountRepository.findById(any(UUID.class))).thenReturn(Optional.of(new Account()));

        assertThatThrownBy(() -> service.createRegistrarForUser(
                UUID.randomUUID(), "admin", null, "  ", null, authority))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("注册管理员角色不能为空");
    }

    @Test
    void createRegistrarForUser_organizationIdNull_fallsBackToRegistrationAuthority() {
        RegistrationAuthority authority = ra();
        Role existingRole = role(UUID.randomUUID(), "注册管理员");
        when(accountRepository.findById(any(UUID.class))).thenReturn(Optional.of(new Account()));
        when(roleRepository.findByRoleNameIgnoreCase("注册管理员")).thenReturn(Optional.of(existingRole));
        when(registrarRepository.save(any(Registrar.class))).thenAnswer(inv -> {
            Registrar r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        Registrar saved = service.createRegistrarForUser(
                UUID.randomUUID(), "admin", null, "注册管理员", null, authority);

        assertThat(saved.getOrganization()).isSameAs(authority);
        verify(organizationRepository, never()).findById(any(UUID.class));
    }

    @Test
    void createRegistrarForUser_organizationIdProvided_resolvesById() {
        RegistrationAuthority authority = ra();
        Organization explicitOrg = org(UUID.randomUUID());
        Role existingRole = role(UUID.randomUUID(), "注册管理员");
        when(accountRepository.findById(any(UUID.class))).thenReturn(Optional.of(new Account()));
        when(roleRepository.findByRoleNameIgnoreCase("注册管理员")).thenReturn(Optional.of(existingRole));
        when(organizationRepository.findById(explicitOrg.getId())).thenReturn(Optional.of(explicitOrg));
        when(registrarRepository.save(any(Registrar.class))).thenAnswer(inv -> {
            Registrar r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        Registrar saved = service.createRegistrarForUser(
                UUID.randomUUID(), "admin", null, "注册管理员", explicitOrg.getId(), authority);

        assertThat(saved.getOrganization()).isSameAs(explicitOrg);
    }
}
