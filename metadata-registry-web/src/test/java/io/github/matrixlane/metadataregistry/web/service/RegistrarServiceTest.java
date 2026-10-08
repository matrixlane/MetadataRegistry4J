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

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
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
    @Mock private ContactAccountService bindingService;

    @InjectMocks
    private RegistrarService service;

    @org.junit.jupiter.api.BeforeEach
    void stubSave() {
        // create/update 链路依赖 save 返回带 id 实体（随后 applyAccountBinding 读取 saved.getId()）
        org.mockito.Mockito.lenient().when(registrarRepository.save(any(Registrar.class))).thenAnswer(inv -> {
            Registrar r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(UUID.randomUUID());
            }
            return r;
        });
    }

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

    /**
     * remove-registrar-contact-identifier：identifier 非 ISO/IEC 11179-3:2023 §6.3.5 Contact
     * 的标准属性，管理端请求/响应契约均不得再暴露。
     */
    @Test
    void registrarContract_hasNoIdentifierComponent() {
        assertThat(Arrays.stream(RegistrarRequest.class.getRecordComponents()).map(RecordComponent::getName))
                .doesNotContain("identifier");
        assertThat(Arrays.stream(RegistrarResponse.class.getRecordComponents()).map(RecordComponent::getName))
                .doesNotContain("identifier");
    }

    // ───────────────────────── applyRequest ─────────────────────────

    @Test
    void applyRequest_roleIdProvided_setsRole() {
        Organization organization = org(UUID.randomUUID());
        Role role = role(UUID.randomUUID(), "数据管理员");
        when(organizationRepository.findById(organization.getId())).thenReturn(Optional.of(organization));
        when(roleRepository.findById(role.getId())).thenReturn(Optional.of(role));

        RegistrarRequest req = new RegistrarRequest(
                null, null, organization.getId(), null, null, role.getId());

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
                null, null, organization.getId(), null, null, null);

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
                null, null, missing, null, null, null);

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
                null, null, organization.getId(), null, null, missingRole);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void applyRequest_individualIdNull_keepsNoIndividual() {
        Organization organization = org(UUID.randomUUID());
        when(organizationRepository.findById(organization.getId())).thenReturn(Optional.of(organization));

        RegistrarRequest req = new RegistrarRequest(
                null, null, organization.getId(), null, null, null);

        service.create(req);

        ArgumentCaptor<Registrar> captor = ArgumentCaptor.forClass(Registrar.class);
        verify(registrarRepository).save(captor.capture());
        assertThat(captor.getValue().getIndividual()).isNull();
        verify(individualRepository, never()).findById(any(UUID.class));
    }

    // ───── add-registrar-three-elements bug fix: 修复后端误清空 RA/Individual + individualName 三情形 ─────

    @Test
    void applyRequest_registrationAuthorityIdNull_keepsExistingRA() {
        // B3 fix: 编辑表单不传 RA id 时，service 不应清空 entity 现有 RA 关联。
        // service.update() 内部会 loadOrThrow(id) → load 现有 Registrar，
        // 然后 applyRequest() → 期望 RA 字段不被清空。
        RegistrationAuthority existingRa = ra();
        Organization organization = org(UUID.randomUUID());
        UUID registrarId = UUID.randomUUID();
        Registrar existing = new Registrar(existingRa);
        existing.setId(registrarId);
        existing.setOrganization(organization);
        when(registrarRepository.findById(registrarId)).thenReturn(Optional.of(existing));
        when(organizationRepository.findById(organization.getId())).thenReturn(Optional.of(organization));
        when(registrarRepository.save(any(Registrar.class))).thenAnswer(inv -> inv.getArgument(0));

        RegistrarRequest req = new RegistrarRequest(
                null, null, organization.getId(), null, null, null);

        service.update(registrarId, req);

        assertThat(existing.getRegistrationAuthority()).isSameAs(existingRa);
    }

    @Test
    void applyRequest_individualBothBlank_keepsExistingIndividual() {
        // 编辑表单 individualId+individualName 都为空时，entity 现有 Individual 关联必须保留。
        Organization organization = org(UUID.randomUUID());
        UUID registrarId = UUID.randomUUID();
        RegistrationAuthority existingRa = ra();
        Registrar existing = new Registrar(existingRa);
        existing.setId(registrarId);
        existing.setOrganization(organization);
        Individual existingIndividual = individual(UUID.randomUUID());
        existingIndividual.setName(
                io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign.of("李雷"));
        existing.setIndividual(existingIndividual);
        when(registrarRepository.findById(registrarId)).thenReturn(Optional.of(existing));
        when(organizationRepository.findById(organization.getId())).thenReturn(Optional.of(organization));
        when(registrarRepository.save(any(Registrar.class))).thenAnswer(inv -> inv.getArgument(0));

        RegistrarRequest req = new RegistrarRequest(
                null, null, organization.getId(),
                null, null, null);

        service.update(registrarId, req);

        // entity Individual 关联保持不变
        assertThat(existing.getIndividual()).isSameAs(existingIndividual);
        verify(individualRepository, never()).save(any(Individual.class));
    }

    @Test
    void applyRequest_individualNameProvided_createsNewIndividual() {
        Organization organization = org(UUID.randomUUID());
        when(organizationRepository.findById(organization.getId())).thenReturn(Optional.of(organization));
        when(individualRepository.save(any(Individual.class))).thenAnswer(inv -> {
            Individual i = inv.getArgument(0);
            i.setId(UUID.randomUUID());
            return i;
        });

        // individualId null + individualName="张习" → 创建新 Individual 并关联
        RegistrarRequest req = new RegistrarRequest(
                null, null, organization.getId(),
                null, "张习", null);

        service.create(req);

        ArgumentCaptor<Registrar> captor = ArgumentCaptor.forClass(Registrar.class);
        verify(registrarRepository).save(captor.capture());
        assertThat(captor.getValue().getIndividual()).isNotNull();
        assertThat(captor.getValue().getIndividual().getName().getValue()).isEqualTo("张习");
        verify(individualRepository).save(any(Individual.class));
    }

    @Test
    void applyRequest_individualNameProvided_existingIndividual_updatesName() {
        // individualId 非空 + individualName="新名字" → 改现有 Individual.name
        Organization organization = org(UUID.randomUUID());
        Individual existing = individual(UUID.randomUUID());
        existing.setName(io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign.of("旧名字"));
        when(organizationRepository.findById(organization.getId())).thenReturn(Optional.of(organization));
        when(individualRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(individualRepository.save(any(Individual.class))).thenAnswer(inv -> inv.getArgument(0));

        RegistrarRequest req = new RegistrarRequest(
                null, null, organization.getId(),
                existing.getId(), "新名字", null);

        service.create(req);

        assertThat(existing.getName().getValue()).isEqualTo("新名字");
        verify(individualRepository).save(existing);
    }

    @Test
    void applyRequest_individualIdNull_individualNameBlank_createPathDoesNotSetIndividual() {
        // create 路径：individualId + individualName 都为空 → 不创建 Individual（与 update 不同语义）
        Organization organization = org(UUID.randomUUID());
        when(organizationRepository.findById(organization.getId())).thenReturn(Optional.of(organization));

        RegistrarRequest req = new RegistrarRequest(
                null, null, organization.getId(),
                null, null, null);

        service.create(req);

        ArgumentCaptor<Registrar> captor = ArgumentCaptor.forClass(Registrar.class);
        verify(registrarRepository).save(captor.capture());
        assertThat(captor.getValue().getIndividual()).isNull();
        verify(individualRepository, never()).save(any(Individual.class));
    }

    // ─────────────────── createRegistrarForUser ───────────────────

    @Test
    void createRegistrarForUser_individualNameProvided_createsIndividual() {
        RegistrationAuthority authority = ra();
        Role registrarRole = role(UUID.randomUUID(), "注册管理员");
        Account account = new Account();
        account.setUsername("admin");
        when(accountRepository.findById(any(UUID.class))).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
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
        // 同一 Individual 必须同时挂到 Account：代表账户本人，避免"我的"保存时再产生第二个
        assertThat(account.getIndividual()).isSameAs(saved.getIndividual());
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
        // 未创建 Individual 时不得触碰 Account 关联
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void createRegistrarForUser_individualNameBlank_accountHasIndividual_reusesAccountIndividual() {
        RegistrationAuthority authority = ra();
        Role existingRole = role(UUID.randomUUID(), "注册管理员");
        Individual own = individual(UUID.randomUUID());
        own.setName(
                io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign.of("汪汇"));
        Account account = new Account();
        account.setUsername("admin");
        account.setIndividual(own);
        when(accountRepository.findById(any(UUID.class))).thenReturn(Optional.of(account));
        when(roleRepository.findByRoleNameIgnoreCase("注册管理员")).thenReturn(Optional.of(existingRole));
        when(registrarRepository.save(any(Registrar.class))).thenAnswer(inv -> {
            Registrar r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        Registrar saved = service.createRegistrarForUser(
                UUID.randomUUID(), "admin", null, "注册管理员", null, authority);

        // 第二个注册机构自动建立的默认 Registrar：直接关联当前用户自己的 Individual
        assertThat(saved.getIndividual()).isSameAs(own);
        // 复用而非新建；回退路径不得触碰 Account 关联
        verify(individualRepository, never()).save(any(Individual.class));
        verify(accountRepository, never()).save(any(Account.class));
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
