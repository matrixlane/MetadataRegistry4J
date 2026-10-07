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
 * 用户可同时持有多个 Registrar 身份，但同一时刻只有一个活动身份；激活恒随账号的
 * 默认注册人员身份（activation-follows-default），{@code active_registrar_id} 仅为
 * 同步缓存，默认身份变更后下一次解析自动跟随（默认绑定缺失或非 Registrar 时回退
 * 首个有效身份）。
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ActiveRegistrarService {

    private final RegistrarRepository registrarRepository;
    private final ContactAccountBindingService contactAccountBindingService;
    private final AccountRepository accountRepository;
    private final AdministeredItemRepository administeredItemRepository;
    private final io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository registrationAuthorityRepository;

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
     * 解析账号的活动 Registrar 身份（activation-follows-default：激活恒随默认注册人员身份）。
     * 目标身份 = 账号默认绑定对应的有效 Registrar 身份（默认绑定缺失或非 Registrar 时回退
     * 首个有效身份）；{@code active_registrar_id} 仅作同步缓存——与目标不一致时写回。
     * 无任何身份时返回空。
     */
    public Optional<Registrar> resolveActiveRegistrar(UUID accountId) {
        List<Registrar> identities = contactAccountBindingService.registrarsOfAccount(accountId).stream()
                .filter(r -> r.getRegistrationAuthority() != null)
                .toList();
        if (identities.isEmpty()) {
            return Optional.empty();
        }
        Registrar active = contactAccountBindingService.defaultBindingOf(accountId)
                .map(io.github.matrixlane.metadataregistry.ext.basicandcore.ContactAccountBinding::getContactId)
                .flatMap(contactId -> identities.stream()
                        .filter(r -> r.getId().equals(contactId))
                        .findFirst())
                .orElseGet(() -> identities.get(0));
        Account account = accountRepository.findById(accountId).orElse(null);
        if (account != null && !active.getId().equals(account.getActiveRegistrarId())) {
            account.setActiveRegistrarId(active.getId());
            accountRepository.save(account);
        }
        return Optional.of(active);
    }

    /**
     * 解析活动 Registrar 身份并在事务内映射为视图对象。供控制器使用：
     * {@link #toIdentity(Registrar)} 需要访问注册机构的名称集合（懒加载），
     * 必须在会话存活期间完成映射，否则控制器层会抛 LazyInitializationException。
     * 不能声明 readOnly：解析链在派生时会写回 {@code active_registrar_id}。
     */
    public Optional<RegistrarIdentity> resolveActiveIdentity(UUID accountId) {
        return resolveActiveRegistrar(accountId).map(ActiveRegistrarService::toIdentity);
    }

    /**
     * 当前登录用户的活动 Registrar；无认证或无身份时返回空。
     * 不能声明 readOnly：解析链在派生时会写回 {@code active_registrar_id}。
     */
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
        // 激活恒随默认注册人员身份：显式激活同步把该身份设为默认绑定，
        // 使下一次解析（含其他会话）仍落回该身份。
        contactAccountBindingService.markDefault(accountId, registrar.getId());
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
        // Submitter 通道（add-submitter-contact-identity）：无活动 Registrar 但已登记
        // 为 Contact 的账号，对归属系统唯一注册机构的条目放行写操作（当系统中
        // 恰好只有一个注册机构时按该唯一机构归属）。
        if (!allowed && contactAccountBindingService.hasAnyContact(accountId)) {
            List<RegistrationAuthority> all = registrationAuthorityRepository.findAll();
            allowed = all.size() == 1 && all.get(0).getId().equals(owner.getId());
        }
        if (!allowed) {
            throw new AccessDeniedException(
                    "仅条目所属注册机构的活动 Registrar 或已登记联系人可修改该条目；如持有该机构的 Registrar 身份，请先切换为活动身份");
        }
    }

    /**
     * Submitter 通道：账号已登记为 Contact（任一绑定）即视为 Submitter。
     */
    @Transactional(readOnly = true)
    public boolean isSubmitter(UUID accountId) {
        return accountId != null && contactAccountBindingService.hasAnyContact(accountId);
    }

    /**
     * 系统唯一的注册机构（当且仅当系统中恰好存在一个注册机构时）；不存在或不唯一时返回空。
     */
    @Transactional(readOnly = true)
    public Optional<RegistrationAuthority> systemUniqueRegistrationAuthority() {
        List<RegistrationAuthority> all = registrationAuthorityRepository.findAll();
        return all.size() == 1 ? Optional.of(all.get(0)) : Optional.empty();
    }

    public static RegistrarIdentity toIdentity(Registrar registrar) {
        RegistrationAuthority ra = registrar.getRegistrationAuthority();
        String raName = ra.getName() == null ? null
                : ra.getName().stream().findFirst().map(n -> n.getValue()).orElse(null);
        return new RegistrarIdentity(registrar.getId(), ra.getId(), raName);
    }
}
