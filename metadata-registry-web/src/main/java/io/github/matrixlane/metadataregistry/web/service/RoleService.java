package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Role;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.PhoneNumber;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.PostalAddress;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.role.RoleOption;
import io.github.matrixlane.metadataregistry.web.dto.role.RoleRequest;
import io.github.matrixlane.metadataregistry.web.dto.role.RoleResponse;
import io.github.matrixlane.metadataregistry.web.repository.ContactRepository;
import io.github.matrixlane.metadataregistry.web.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Role 管理（属性 + 引用计数与删除保护）。
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RoleService {

    private final RoleRepository roleRepository;
    private final ContactRepository contactRepository;

    @Transactional(readOnly = true)
    public Page<RoleResponse> list(String q, Pageable pageable) {
        if (q == null || q.isBlank()) {
            return roleRepository.findAll(pageable).map(this::toResponse);
        }
        return roleRepository.findByRoleNameContainingIgnoreCase(q.trim(), pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public RoleResponse get(UUID id) {
        return toResponse(loadOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<RoleOption> options() {
        return roleRepository.findAll().stream()
                .sorted(Comparator.comparing(r -> r.getRoleName() == null ? "" : r.getRoleName(),
                        String.CASE_INSENSITIVE_ORDER))
                .map(r -> new RoleOption(r.getId(), r.getRoleName()))
                .toList();
    }

    public RoleResponse create(RoleRequest request) {
        String roleName = normalizeName(request.roleName());
        assertNameAvailable(roleName, null);
        Role entity = new Role();
        applyRequest(entity, request, roleName);
        return toResponse(roleRepository.save(entity));
    }

    public RoleResponse update(UUID id, RoleRequest request) {
        Role entity = loadOrThrow(id);
        String roleName = normalizeName(request.roleName());
        assertNameAvailable(roleName, id);
        applyRequest(entity, request, roleName);
        return toResponse(roleRepository.save(entity));
    }

    public void delete(UUID id) {
        loadOrThrow(id);
        long count = contactRepository.countByRoleId(id);
        if (count > 0) {
            throw new BusinessException("角色正被 %d 个联系人引用，不能删除".formatted(count));
        }
        roleRepository.deleteById(id);
    }

    private Role loadOrThrow(UUID id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role", id));
    }

    private String normalizeName(String roleName) {
        return roleName == null ? null : roleName.trim();
    }

    private void assertNameAvailable(String roleName, UUID selfId) {
        roleRepository.findByRoleNameIgnoreCase(roleName)
                .filter(other -> !other.getId().equals(selfId))
                .ifPresent(other -> {
                    throw new BusinessException("Role roleName 已存在：" + other.getRoleName());
                });
    }

    private void applyRequest(Role entity, RoleRequest request, String roleName) {
        entity.setRoleName(roleName);
        entity.setMailAddress(request.mailAddress() == null || request.mailAddress().isBlank()
                ? new PostalAddress()
                : PostalAddress.of(request.mailAddress().trim()));
        entity.setEmailAddress(new ArrayList<>(normalizeStrings(request.emailAddress())));
        entity.setPhoneNumber(new ArrayList<>(normalizeStrings(request.phoneNumber()).stream()
                .map(PhoneNumber::of)
                .toList()));
    }

    private List<String> normalizeStrings(List<String> values) {
        if (values == null) return List.of();
        return values.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .toList();
    }

    private RoleResponse toResponse(Role entity) {
        long contactCount = contactRepository.countByRoleId(entity.getId());
        return new RoleResponse(
                entity.getId(),
                entity.getRoleName(),
                entity.getMailAddress() == null ? null : entity.getMailAddress().getValue(),
                entity.getEmailAddress() == null ? List.of() : List.copyOf(entity.getEmailAddress()),
                entity.getPhoneNumber() == null
                        ? List.of()
                        : entity.getPhoneNumber().stream().map(PhoneNumber::getValue).toList(),
                contactCount,
                contactCount > 0);
    }
}
