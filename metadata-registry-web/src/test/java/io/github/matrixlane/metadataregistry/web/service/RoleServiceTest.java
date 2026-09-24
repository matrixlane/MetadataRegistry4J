package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Role;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.role.RoleOption;
import io.github.matrixlane.metadataregistry.web.dto.role.RoleRequest;
import io.github.matrixlane.metadataregistry.web.dto.role.RoleResponse;
import io.github.matrixlane.metadataregistry.web.repository.ContactRepository;
import io.github.matrixlane.metadataregistry.web.repository.RoleRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private ContactRepository contactRepository;

    @InjectMocks
    private RoleService service;

    private UUID roleId;

    @BeforeEach
    void setUp() {
        roleId = UUID.randomUUID();
    }

    private Role role(String name) {
        Role r = new Role();
        r.setId(roleId);
        r.setRoleName(name);
        return r;
    }

    private RoleRequest request(String name, List<String> emails, List<String> phones) {
        return new RoleRequest(name, "北京市朝阳区", emails, phones);
    }

    @Test
    void list_shouldReturnPageWithContactCount() {
        Role r = role("注册管理员");
        when(roleRepository.findAll(PageRequest.of(0, 10))).thenReturn(new PageImpl<>(List.of(r)));
        when(contactRepository.countByRoleId(roleId)).thenReturn(2L);

        Page<RoleResponse> result = service.list(null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        RoleResponse row = result.getContent().get(0);
        assertThat(row.roleName()).isEqualTo("注册管理员");
        assertThat(row.contactCount()).isEqualTo(2);
        assertThat(row.inUse()).isTrue();
    }

    @Test
    void list_shouldSearchByKeyword() {
        when(roleRepository.findByRoleNameContainingIgnoreCase(eq("管理"), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(role("注册管理员"))));
        when(contactRepository.countByRoleId(roleId)).thenReturn(0L);

        Page<RoleResponse> result = service.list(" 管理 ", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).roleName()).isEqualTo("注册管理员");
    }

    @Test
    void get_shouldThrowWhenNotFound() {
        when(roleRepository.findById(roleId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(roleId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_shouldSaveAndNormalizeMultivalues() {
        when(roleRepository.findByRoleNameIgnoreCase("数据管理员")).thenReturn(Optional.empty());
        when(contactRepository.countByRoleId(roleId)).thenReturn(0L);
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> {
            Role r = inv.getArgument(0);
            r.setId(roleId);
            return r;
        });

        RoleResponse result = service.create(request(" 数据管理员 ",
                java.util.Arrays.asList(" a@x.com ", "", null), List.of("010-1234")));

        assertThat(result.roleName()).isEqualTo("数据管理员");
        assertThat(result.mailAddress()).isEqualTo("北京市朝阳区");
        assertThat(result.emailAddress()).containsExactly("a@x.com");
        assertThat(result.phoneNumber()).containsExactly("010-1234");
        assertThat(result.inUse()).isFalse();
    }

    @Test
    void create_shouldRejectDuplicateNameIgnoringCase() {
        when(roleRepository.findByRoleNameIgnoreCase("注册管理员"))
                .thenReturn(Optional.of(role("注册管理员")));

        assertThatThrownBy(() -> service.create(request("注册管理员", List.of(), List.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已存在");
    }

    @Test
    void update_shouldAllowKeepingOwnName() {
        Role existing = role("注册管理员");
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(existing));
        when(roleRepository.findByRoleNameIgnoreCase("注册管理员"))
                .thenReturn(Optional.of(existing));
        when(contactRepository.countByRoleId(roleId)).thenReturn(0L);
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));

        RoleResponse result = service.update(roleId, request("注册管理员", List.of(), List.of()));

        assertThat(result.roleName()).isEqualTo("注册管理员");
    }

    @Test
    void update_shouldRejectNameTakenByOther() {
        UUID otherId = UUID.randomUUID();
        Role other = role("数据管理员");
        other.setId(otherId);
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role("注册管理员")));
        when(roleRepository.findByRoleNameIgnoreCase("数据管理员")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.update(roleId, request("数据管理员", List.of(), List.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已存在");
    }

    @Test
    void delete_shouldRejectWhenReferencedByContacts() {
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role("注册管理员")));
        when(contactRepository.countByRoleId(roleId)).thenReturn(3L);

        assertThatThrownBy(() -> service.delete(roleId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("3");
        verify(roleRepository, never()).deleteById(any(UUID.class));
    }

    @Test
    void delete_shouldRemoveUnusedRole() {
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role("注册管理员")));
        when(contactRepository.countByRoleId(roleId)).thenReturn(0L);

        service.delete(roleId);

        verify(roleRepository).deleteById(roleId);
    }

    @Test
    void options_shouldSortCaseInsensitive() {
        Role a = role("admin");
        a.setId(UUID.randomUUID());
        Role b = role("Registrar");
        b.setId(UUID.randomUUID());
        when(roleRepository.findAll()).thenReturn(List.of(b, a));

        List<RoleOption> result = service.options();

        assertThat(result).extracting(RoleOption::roleName).containsExactly("admin", "Registrar");
    }
}
