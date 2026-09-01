package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.Registrar;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.web.auth.AccountRepository;
import io.github.matrixlane.metadataregistry.web.auth.AuthenticatedUser;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.repository.AdministeredItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrarRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 活动 Registrar 身份解析（active-registrar-ownership）。
 *
 * <p>Registrar 是注册工作的执行身份：注册 Administered_Item 必须持有活动
 * Registrar 身份，所注册条目一定关联该身份所在的 Registration_Authority；
 * 修改/删除已归属条目时，当前用户必须以归属机构的 Registrar 身份为活动身份。
 * 用户可同时持有多个 Registrar 身份，但同一时刻只有一个活动身份；选择持久化
 * 在账号的 {@code active_registrar_id}，失效（身份被撤销）时回退到首个有效身份。
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ActiveRegistrarService {

    private final RegistrarRepository registrarRepository;
    private final ContactAccountBindingService contactAccountBindingService;
    private final AccountRepository accountRepository;
    private final AdministeredItemRepository administeredItemRepository;

    public record RegistrarIdentity(UUID registrarId, UUID registrationAuthorityId,
                                    String registrationAuthorityName) {
    }

    /**
     * 当前登录账号 id；无认证（系统/种子路径）时返回 null。
     */
    public UUID currentAccountIdOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return user.id();
        }
        return null;
    }

    /**
     * 账号持有的全部 Registrar 身份（仅计关联了注册机构的有效身份）。
     */
    @Transactional(readOnly = true)
    public List<RegistrarIdentity> listIdentities(UUID accountId) {
        return contactAccountBindingService.registrarsOfAccount(accountId).stream()
                .filter(r -> r.getRegistrationAuthority() != null)
                .map(ActiveRegistrarService::toIdentity)
                .toList();
    }

    /**
     * 解析账号的活动 Registrar 身份：优先账号上持久化的选择；未选择或已选身份
     * 失效（绑定被撤销）时回退到首个有效身份。无任何身份时返回空。
     */
    @Transactional(readOnly = true)
    public Optional<Registrar> resolveActiveRegistrar(UUID accountId) {
        List<Registrar> identities = contactAccountBindingService.registrarsOfAccount(accountId).stream()
                .filter(r -> r.getRegistrationAuthority() != null)
                .toList();
        if (identities.isEmpty()) {
            return Optional.empty();
        }
        Account account = accountRepository.findById(accountId).orElse(null);
        UUID activeId = account != null ? account.getActiveRegistrarId() : null;
        if (activeId != null) {
            Optional<Registrar> selected = identities.stream()
                    .filter(r -> r.getId().equals(activeId))
                    .findFirst();
            if (selected.isPresent()) {
                return selected;
            }
        }
        return Optional.of(identities.get(0));
    }

    /**
     * 解析活动 Registrar 身份并在事务内映射为视图对象。供控制器使用：
     * {@link #toIdentity(Registrar)} 需要访问注册机构的名称集合（懒加载），
     * 必须在会话存活期间完成映射，否则控制器层会抛 LazyInitializationException。
     */
    @Transactional(readOnly = true)
    public Optional<RegistrarIdentity> resolveActiveIdentity(UUID accountId) {
        return resolveActiveRegistrar(accountId).map(ActiveRegistrarService::toIdentity);
    }

    /**
     * 当前登录用户的活动 Registrar；无认证或无身份时返回空。
     */
    @Transactional(readOnly = true)
    public Optional<Registrar> resolveForCurrentUser() {
        UUID accountId = currentAccountIdOrNull();
        return accountId == null ? Optional.empty() : resolveActiveRegistrar(accountId);
    }

    /**
     * 切换活动 Registrar 身份并持久化。目标身份必须属于该账号且关联注册机构。
     */
    public RegistrarIdentity selectActiveRegistrar(UUID accountId, UUID registrarId) {
        Registrar registrar = registrarRepository.findById(registrarId)
                .orElseThrow(() -> new ResourceNotFoundException("Registrar", registrarId));
        java.util.Optional<java.util.UUID> boundAccountId = contactAccountBindingService.accountIdOfContact(registrar.getId());
        if (boundAccountId.isEmpty() || !accountId.equals(boundAccountId.get())) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "所选 Registrar 身份不属于当前账号");
        }
        if (registrar.getRegistrationAuthority() == null) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "所选 Registrar 身份未关联注册机构，无法激活");
        }
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        account.setActiveRegistrarId(registrarId);
        accountRepository.save(account);
        return toIdentity(registrar);
    }

    /**
     * 变更权限约束：修改/删除条目时调用。条目归属注册机构时，当前用户必须以
     * 该机构的 Registrar 身份为活动身份；无归属（历史遗留）或无认证（系统路径）
     * 时维持既有行为。超级管理员不豁免。
     */
    @Transactional(readOnly = true)
    public void assertCanModifyItem(UUID itemId) {
        UUID accountId = currentAccountIdOrNull();
        if (accountId == null) {
            return;
        }
        AdministeredItem ai = administeredItemRepository.findByItem_Id(itemId).orElse(null);
        if (ai == null || ai.getRegistrationAuthority() == null) {
            return;
        }
        assertActiveIdentityOf(ai.getRegistrationAuthority(), accountId);
    }

    /**
     * 变更权限约束（实体已在手时的变体）。
     */
    @Transactional(readOnly = true)
    public void assertCanModify(AdministeredItem ai) {
        if (ai == null || ai.getRegistrationAuthority() == null) {
            return;
        }
        UUID accountId = currentAccountIdOrNull();
        if (accountId == null) {
            return;
        }
        assertActiveIdentityOf(ai.getRegistrationAuthority(), accountId);
    }

    private void assertActiveIdentityOf(RegistrationAuthority owner, UUID accountId) {
        Optional<Registrar> active = resolveActiveRegistrar(accountId);
        boolean allowed = active.isPresent()
                && active.get().getRegistrationAuthority() != null
                && owner.getId().equals(active.get().getRegistrationAuthority().getId());
        if (!allowed) {
            throw new AccessDeniedException(
                    "仅条目所属注册机构的活动 Registrar 可修改该条目；如持有该机构的 Registrar 身份，请先切换为活动身份");
        }
    }

    public static RegistrarIdentity toIdentity(Registrar registrar) {
        RegistrationAuthority ra = registrar.getRegistrationAuthority();
        String raName = ra.getName() == null ? null
                : ra.getName().stream().findFirst().map(n -> n.getValue()).orElse(null);
        return new RegistrarIdentity(registrar.getId(), ra.getId(), raName);
    }
}
