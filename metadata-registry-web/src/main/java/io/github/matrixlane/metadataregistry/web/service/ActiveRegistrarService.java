package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.Registrar;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.web.auth.AuthenticatedUser;
import io.github.matrixlane.metadataregistry.web.repository.AdministeredItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrarRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 活动 Registrar 身份解析（active-registrar-ownership，personal-no-login-registrar-default 重写）。
 *
 * <p>激活状态保存在浏览器端：前端将选择写入本地存储并随请求以
 * {@code X-Active-Registrar-Id} 头传递。服务端解析链（纯读，MUST NOT 写回任何
 * 持久化状态）：
 * <ol>
 *   <li>请求头所选身份（属于当前账号且关联注册机构，否则忽略）；</li>
 *   <li>账号默认注册人员身份（{@code ext.registrar.is_default}）；</li>
 *   <li>首个有效身份。</li>
 * </ol>
 * 无任何身份时返回空。注册工作、变更权限约束、归属推导均消费本解析结果。
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ActiveRegistrarService {

    /** 浏览器端激活 Registrar 选择的传递头。 */
    public static final String ACTIVE_REGISTRAR_HEADER = "X-Active-Registrar-Id";

    private final RegistrarRepository registrarRepository;
    private final ContactAccountService contactAccountService;
    private final AdministeredItemRepository administeredItemRepository;
    private final RegistrationAuthorityRepository registrationAuthorityRepository;

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
        return contactAccountService.registrarsOfAccount(accountId).stream()
                .filter(r -> r.getRegistrationAuthority() != null)
                .map(ActiveRegistrarService::toIdentity)
                .toList();
    }

    /**
     * 解析账号的活动 Registrar 身份（纯读）：请求头 → 默认注册人员 → 首个有效身份。
     * 无任何身份时返回空。
     */
    @Transactional(readOnly = true)
    public Optional<Registrar> resolveActiveRegistrar(UUID accountId) {
        List<Registrar> identities = contactAccountService.registrarsOfAccount(accountId).stream()
                .filter(r -> r.getRegistrationAuthority() != null)
                .toList();
        if (identities.isEmpty()) {
            return Optional.empty();
        }
        Optional<Registrar> headerSelected = selectedViaHeader(identities);
        if (headerSelected.isPresent()) {
            return headerSelected;
        }
        Optional<UUID> defaultId = contactAccountService.defaultRegistrarIdOf(accountId);
        if (defaultId.isPresent()) {
            Optional<Registrar> hit = identities.stream()
                    .filter(r -> r.getId().equals(defaultId.get()))
                    .findFirst();
            if (hit.isPresent()) {
                return hit;
            }
        }
        return Optional.of(identities.get(0));
    }

    /**
     * 浏览器端激活选择：读取 {@code X-Active-Registrar-Id} 头，校验其属于当前账号的
     * 有效身份列表；无请求上下文（非 Web 线程）、头缺失/非法/不属于该账号时返回空。
     */
    private Optional<Registrar> selectedViaHeader(List<Registrar> identities) {
        UUID selectedId = currentHeaderRegistrarId();
        if (selectedId == null) {
            return Optional.empty();
        }
        return identities.stream()
                .filter(r -> r.getId().equals(selectedId))
                .findFirst();
    }

    private UUID currentHeaderRegistrarId() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return null;
        }
        String header = attrs.getRequest().getHeader(ACTIVE_REGISTRAR_HEADER);
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(header.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * 账号默认注册人员的 id（供控制器组装响应）；未设置时返回空。
     */
    @Transactional(readOnly = true)
    public Optional<UUID> defaultRegistrarIdOf(UUID accountId) {
        return contactAccountService.defaultRegistrarIdOf(accountId);
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
        if (!allowed && contactAccountService.hasAnyContact(accountId)) {
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
        return accountId != null && contactAccountService.hasAnyContact(accountId);
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
