package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationState;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.web.auth.AppUserRole;
import io.github.matrixlane.metadataregistry.web.auth.AuthenticatedUser;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.registration.RegistrationStateHistoryEntry;
import io.github.matrixlane.metadataregistry.web.repository.AdministeredItemRepository;
import io.github.matrixlane.metadataregistry.web.auth.AccountRepository;
import io.github.matrixlane.metadataregistry.web.repository.ContactRepository;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Contact;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 注册生命周期状态机服务。
 *
 * <p>管理 ISO/IEC 11179 注册状态的转换，包含：</p>
 * <ul>
 *   <li>状态转换合法性校验</li>
 *   <li>角色权限控制（ADMIN/REGISTRAR/VIEWER）</li>
 *   <li>状态变更历史记录（链式 previousState）</li>
 *   <li>明确的业务错误码（1100-1199 区间）</li>
 * </ul>
 *
 * <p>状态机严格遵循 ISO/IEC 11179-6:2023 标准注册状态生命周期：</p>
 * <ul>
 *   <li>Incomplete → Submitted → Recorded → Qualified → Standard → Preferred</li>
 *   <li>Standard/Superseded/Preferred 可降至 Retired</li>
 *   <li>Retired 可归档至 Historical</li>
 * </ul>
 *
 * <p><b>WI-009-1 方向性决策：</b>采用"双层状态"架构——ISO 注册状态为主状态，
 * 业务审批状态（Draft/Submitted/Under Review/Approved/Published）将在 WI-009-2 中
 * 作为正交维度引入。</p>
 *
 * <p><b>权限实现说明：</b></p>
 * <p>当前采用 {@link SecurityContextHolder} 手动校验角色，标注为临时方案。
 * V1 阶段将重构为 {@code @PreAuthorize} + 自定义 PermissionEvaluator。</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RegistrationLifecycleService {

    private static final Map<RegistrationStatus, Set<RegistrationStatus>> ALLOWED_TRANSITIONS;

    /**
     * 角色-状态转换权限矩阵。
     *
     * <p>SUPERSEDED 创建（从 STANDARD/PREFERRED_STANDARD 降级）和
     * SUPERSEDED → HISTORICAL 属于重大管理决策，仅限 ADMIN。</p>
     */
    private static final Map<RegistrationStatus, Map<RegistrationStatus, Set<AppUserRole>>> ROLE_PERMISSIONS;

    static {
        EnumMap<RegistrationStatus, Set<RegistrationStatus>> map = new EnumMap<>(RegistrationStatus.class);
        // ISO/IEC 11179-6:2023 标准状态转换
        // Application — ISO/IEC 11179-6:2023 中用于标识处于应用系统但尚未逻辑规范化的项
        map.put(RegistrationStatus.INCOMPLETE,
                EnumSet.of(RegistrationStatus.CANDIDATE, RegistrationStatus.APPLICATION, RegistrationStatus.RETIRED));
        map.put(RegistrationStatus.CANDIDATE,
                EnumSet.of(RegistrationStatus.RECORDED, RegistrationStatus.APPLICATION, RegistrationStatus.RETIRED));
        map.put(RegistrationStatus.APPLICATION,
                EnumSet.of(RegistrationStatus.RECORDED, RegistrationStatus.RETIRED));
        map.put(RegistrationStatus.RECORDED,
                EnumSet.of(RegistrationStatus.QUALIFIED, RegistrationStatus.RETIRED));
        map.put(RegistrationStatus.QUALIFIED,
                EnumSet.of(RegistrationStatus.STANDARD, RegistrationStatus.RETIRED));
        map.put(RegistrationStatus.STANDARD,
                EnumSet.of(RegistrationStatus.PREFERRED_STANDARD, RegistrationStatus.SUPERSEDED,
                        RegistrationStatus.RETIRED));
        map.put(RegistrationStatus.PREFERRED_STANDARD,
                EnumSet.of(RegistrationStatus.STANDARD, RegistrationStatus.SUPERSEDED,
                        RegistrationStatus.RETIRED));
        map.put(RegistrationStatus.SUPERSEDED,
                EnumSet.of(RegistrationStatus.RETIRED, RegistrationStatus.HISTORICAL));
        map.put(RegistrationStatus.RETIRED,
                EnumSet.of(RegistrationStatus.HISTORICAL));
        map.put(RegistrationStatus.HISTORICAL, EnumSet.noneOf(RegistrationStatus.class));
        ALLOWED_TRANSITIONS = Map.copyOf(map);

        // 初始化角色权限矩阵
        EnumMap<RegistrationStatus, Map<RegistrationStatus, Set<AppUserRole>>> roleMap =
                new EnumMap<>(RegistrationStatus.class);

        // INCOMPLETE
        EnumMap<RegistrationStatus, Set<AppUserRole>> fromIncomplete = new EnumMap<>(RegistrationStatus.class);
        fromIncomplete.put(RegistrationStatus.CANDIDATE, EnumSet.of(AppUserRole.ADMIN, AppUserRole.REGISTRAR));
        fromIncomplete.put(RegistrationStatus.APPLICATION, EnumSet.of(AppUserRole.ADMIN, AppUserRole.REGISTRAR));
        fromIncomplete.put(RegistrationStatus.RETIRED, EnumSet.of(AppUserRole.ADMIN, AppUserRole.REGISTRAR));
        roleMap.put(RegistrationStatus.INCOMPLETE, Map.copyOf(fromIncomplete));

        // CANDIDATE
        EnumMap<RegistrationStatus, Set<AppUserRole>> fromSubmitted = new EnumMap<>(RegistrationStatus.class);
        fromSubmitted.put(RegistrationStatus.RECORDED, EnumSet.of(AppUserRole.ADMIN, AppUserRole.REGISTRAR));
        fromSubmitted.put(RegistrationStatus.APPLICATION, EnumSet.of(AppUserRole.ADMIN, AppUserRole.REGISTRAR));
        fromSubmitted.put(RegistrationStatus.RETIRED, EnumSet.of(AppUserRole.ADMIN, AppUserRole.REGISTRAR));
        roleMap.put(RegistrationStatus.CANDIDATE, Map.copyOf(fromSubmitted));

        // APPLICATION
        EnumMap<RegistrationStatus, Set<AppUserRole>> fromApplication = new EnumMap<>(RegistrationStatus.class);
        fromApplication.put(RegistrationStatus.RECORDED, EnumSet.of(AppUserRole.ADMIN, AppUserRole.REGISTRAR));
        fromApplication.put(RegistrationStatus.RETIRED, EnumSet.of(AppUserRole.ADMIN, AppUserRole.REGISTRAR));
        roleMap.put(RegistrationStatus.APPLICATION, Map.copyOf(fromApplication));

        // RECORDED
        EnumMap<RegistrationStatus, Set<AppUserRole>> fromRecorded = new EnumMap<>(RegistrationStatus.class);
        fromRecorded.put(RegistrationStatus.QUALIFIED, EnumSet.of(AppUserRole.ADMIN, AppUserRole.REGISTRAR));
        fromRecorded.put(RegistrationStatus.RETIRED, EnumSet.of(AppUserRole.ADMIN, AppUserRole.REGISTRAR));
        roleMap.put(RegistrationStatus.RECORDED, Map.copyOf(fromRecorded));

        // QUALIFIED
        EnumMap<RegistrationStatus, Set<AppUserRole>> fromQualified = new EnumMap<>(RegistrationStatus.class);
        fromQualified.put(RegistrationStatus.STANDARD, EnumSet.of(AppUserRole.ADMIN, AppUserRole.REGISTRAR));
        fromQualified.put(RegistrationStatus.RETIRED, EnumSet.of(AppUserRole.ADMIN, AppUserRole.REGISTRAR));
        roleMap.put(RegistrationStatus.QUALIFIED, Map.copyOf(fromQualified));

        // STANDARD
        EnumMap<RegistrationStatus, Set<AppUserRole>> fromStandard = new EnumMap<>(RegistrationStatus.class);
        fromStandard.put(RegistrationStatus.PREFERRED_STANDARD, EnumSet.of(AppUserRole.ADMIN, AppUserRole.REGISTRAR));
        fromStandard.put(RegistrationStatus.SUPERSEDED, EnumSet.of(AppUserRole.ADMIN));
        fromStandard.put(RegistrationStatus.RETIRED, EnumSet.of(AppUserRole.ADMIN, AppUserRole.REGISTRAR));
        roleMap.put(RegistrationStatus.STANDARD, Map.copyOf(fromStandard));

        // PREFERRED
        EnumMap<RegistrationStatus, Set<AppUserRole>> fromPreferred = new EnumMap<>(RegistrationStatus.class);
        fromPreferred.put(RegistrationStatus.STANDARD, EnumSet.of(AppUserRole.ADMIN, AppUserRole.REGISTRAR));
        fromPreferred.put(RegistrationStatus.SUPERSEDED, EnumSet.of(AppUserRole.ADMIN));
        fromPreferred.put(RegistrationStatus.RETIRED, EnumSet.of(AppUserRole.ADMIN, AppUserRole.REGISTRAR));
        roleMap.put(RegistrationStatus.PREFERRED_STANDARD, Map.copyOf(fromPreferred));

        // SUPERSEDED
        EnumMap<RegistrationStatus, Set<AppUserRole>> fromSuperseded = new EnumMap<>(RegistrationStatus.class);
        fromSuperseded.put(RegistrationStatus.RETIRED, EnumSet.of(AppUserRole.ADMIN, AppUserRole.REGISTRAR));
        fromSuperseded.put(RegistrationStatus.HISTORICAL, EnumSet.of(AppUserRole.ADMIN));
        roleMap.put(RegistrationStatus.SUPERSEDED, Map.copyOf(fromSuperseded));

        // RETIRED
        EnumMap<RegistrationStatus, Set<AppUserRole>> fromRetired = new EnumMap<>(RegistrationStatus.class);
        fromRetired.put(RegistrationStatus.HISTORICAL, EnumSet.of(AppUserRole.ADMIN, AppUserRole.REGISTRAR));
        roleMap.put(RegistrationStatus.RETIRED, Map.copyOf(fromRetired));

        // HISTORICAL — 无允许转换
        roleMap.put(RegistrationStatus.HISTORICAL, Map.of());

        ROLE_PERMISSIONS = Map.copyOf(roleMap);
    }

    private final AdministeredItemRepository administeredItemRepository;
    private final AccountRepository accountRepository;
    private final ContactRepository contactRepository;
    private final ContactAccountBindingService contactAccountBindingService;

    /**
     * 执行状态转换。
     *
     * @param administeredItemId 管理项 ID
     * @param to                 目标状态
     * @param administrativeNote 管理备注
     * @return 更新后的管理项
     * @throws ResourceNotFoundException 管理项不存在时抛出（code = 1104）
     * @throws BusinessException         状态转换非法或无权转换时抛出
     */
    public AdministeredItem transition(UUID administeredItemId,
                                       RegistrationStatus to,
                                       String administrativeNote) {
        if (to == null) {
            throw new BusinessException(ApiCodes.TARGET_STATE_REQUIRED, "目标状态不能为空");
        }

        AdministeredItem ai = administeredItemRepository.findById(administeredItemId)
                .orElseThrow(() -> new BusinessException(
                        ApiCodes.ADMINISTERED_ITEM_NOT_FOUND,
                        "管理项不存在: " + administeredItemId));

        RegistrationState current = ai.getRegistrationState();
        RegistrationStatus from = current == null ? RegistrationStatus.INCOMPLETE
                : current.getRegistrationStatus();

        // 校验状态转换合法性
        validateTransition(from, to);

        // 校验角色权限
        AppUserRole currentRole = resolveCurrentRole();
        validateRolePermission(from, to, currentRole);

        // 11179-3:2023 5.1:最小出现次数约束在注册状态 Recorded 及以上时强制;
        // 11179-31:2023 7.5.3.2:Measure_Class 须关联 1..* Unit_of_Measure。
        validateMandatoryAssociations(ai.getItem(), to);

        LocalDateTime now = LocalDateTime.now();
        if (current != null) {
            current.setUntilDate(now);
        }
        RegistrationState next = new RegistrationState();
        next.setRegistrationStatus(to);
        next.setEffectiveDate(now);
        next.setPreviousState(current);
        next.setAdministrativeNote(administrativeNote);
        ai.setRegistrationState(next);
        ai.setLastChangeDateTime(now);
        ai.setChangeDescription("状态迁移：%s → %s".formatted(from, to));

        return administeredItemRepository.save(ai);
    }

    /**
     * 查询状态变更历史（按 administered_item_id 过滤，分页）。
     *
     * <p>基于 {@link RegistrationState#previousState} 链式结构遍历。
     * WI-009-1 阶段使用正向遍历（从新到旧），结果按时间倒序排列。</p>
     *
     * <p>性能说明：深度历史链存在 N+1 查询风险。当前实现采用链式遍历，
     * 如需优化可在后续版本引入 {@code EntityGraph} 或扁平化历史表。</p>
     *
     * @param administeredItemId 管理项 ID
     * @param pageable           分页参数
     * @return 状态变更历史分页数据
     */
    @Transactional(readOnly = true)
    public Page<RegistrationStateHistoryEntry> getStateHistory(UUID administeredItemId, Pageable pageable) {
        AdministeredItem ai = administeredItemRepository.findById(administeredItemId)
                .orElseThrow(() -> new BusinessException(
                        ApiCodes.ADMINISTERED_ITEM_NOT_FOUND,
                        "管理项不存在: " + administeredItemId));

        List<RegistrationStateHistoryEntry> allEntries = new ArrayList<>();
        RegistrationState current = ai.getRegistrationState();

        if (current != null) {
            collectHistoryEntries(current, allEntries, administeredItemId);
        }

        // 分页处理
        int total = allEntries.size();
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), total);

        List<RegistrationStateHistoryEntry> pageContent =
                (start >= total) ? Collections.emptyList() : allEntries.subList(start, end);

        return new PageImpl<>(pageContent, pageable, total);
    }

    /**
     * 递归收集历史记录条目（从新到旧）。
     */
    private void collectHistoryEntries(RegistrationState state,
                                       List<RegistrationStateHistoryEntry> entries,
                                       UUID administeredItemId) {
        if (state == null) {
            return;
        }

        RegistrationStateHistoryEntry entry = new RegistrationStateHistoryEntry(
                state.getId(),
                administeredItemId,
                state.getRegistrationStatus(),
                state.getEffectiveDate(),
                state.getUntilDate(),
                state.getAdministrativeNote()
        );
        entries.add(entry);

        collectHistoryEntries(state.getPreviousState(), entries, administeredItemId);
    }

    /**
     * 校验状态转换的合法性（仅校验状态机规则，不校验权限）。
     *
     * @param from 当前状态
     * @param to   目标状态
     * @throws BusinessException 转换非法时抛出
     */
    /** 注册状态达到 Recorded 及以上(11179-3:2023 状态层级)。 */
    private static final java.util.Set<RegistrationStatus> RECORDED_OR_HIGHER =
            EnumSet.of(RegistrationStatus.RECORDED, RegistrationStatus.QUALIFIED,
                    RegistrationStatus.STANDARD, RegistrationStatus.PREFERRED_STANDARD);

    private void validateMandatoryAssociations(
            io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item item,
            RegistrationStatus to) {
        if (!RECORDED_OR_HIGHER.contains(to)) {
            return;
        }
        if (item instanceof io.github.matrixlane.metadataregistry.dataspecification.MeasureClass mc
                && mc.getUnitOfMeasures().isEmpty()) {
            throw new BusinessException(
                    "Measure_Class 必须关联至少一个 Unit_of_Measure(ISO/IEC 11179-31:2023 7.5.3.2)");
        }
    }

    void validateTransition(RegistrationStatus from, RegistrationStatus to) {
        if (from == to) {
            throw new BusinessException(ApiCodes.ALREADY_IN_TARGET_STATE,
                    "当前状态已经是 " + to + "，无需迁移");
        }

        Set<RegistrationStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(
                from, EnumSet.noneOf(RegistrationStatus.class));
        if (!allowed.contains(to)) {
            int errorCode = resolveTransitionErrorCode(from);
            throw new BusinessException(errorCode,
                    "非法的状态迁移：%s → %s".formatted(from, to));
        }
    }

    /**
     * 校验角色是否有权执行此状态转换。
     *
     * <p>临时方案：通过 {@link SecurityContextHolder} 获取当前认证用户角色。</p>
     *
     * @param from       当前状态
     * @param to         目标状态
     * @param currentRole 当前用户角色
     * @throws BusinessException 无权转换时抛出（code = 1102）
     */
    void validateRolePermission(RegistrationStatus from, RegistrationStatus to, AppUserRole currentRole) {
        // ADMIN 拥有所有权限
        if (currentRole == AppUserRole.ADMIN) {
            return;
        }

        Map<RegistrationStatus, Set<AppUserRole>> fromMap = ROLE_PERMISSIONS.get(from);
        if (fromMap == null) {
            throw new BusinessException(ApiCodes.UNAUTHORIZED_STATE_TRANSITION,
                    "无权执行状态迁移 %s → %s：当前角色 %s 无权限".formatted(from, to, currentRole));
        }

        Set<AppUserRole> allowedRoles = fromMap.get(to);
        if (allowedRoles == null || !allowedRoles.contains(currentRole)) {
            throw new BusinessException(ApiCodes.UNAUTHORIZED_STATE_TRANSITION,
                    "无权执行状态迁移 %s → %s：当前角色 %s 无权限".formatted(from, to, currentRole));
        }
    }

    /**
     * 解析当前用户的角色。
     *
     * <p>临时方案，V1 重构为 Spring Security 方法级安全注解。</p>
     *
     * @return 当前用户角色，未认证时返回 VIEWER（最严格权限）
     */
    AppUserRole resolveCurrentRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return AppUserRole.VIEWER;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthenticatedUser user) {
            if (user.role() == SystemRole.SUPER_ADMIN) {
                return AppUserRole.ADMIN;
            }
            // Check if they are a registrar contact
            Account account = accountRepository.findById(user.id()).orElse(null);
            if (account != null) {
                List<Contact> contacts = contactAccountBindingService.contactsOfAccount(account);
                boolean isRegistrar = contacts.stream()
                        .anyMatch(c -> c.getRole() != null && "注册管理员".equals(c.getRole().getRoleName()));
                if (isRegistrar) {
                    return AppUserRole.REGISTRAR;
                }
            }
            return AppUserRole.VIEWER;
        }

        // Fallback or authority checks
        return authentication.getAuthorities().stream()
                .map(grantedAuthority -> {
                    String auth = grantedAuthority.getAuthority();
                    if (auth.startsWith("ROLE_")) {
                        try {
                            String roleName = auth.substring(5);
                            if ("SUPER_ADMIN".equals(roleName)) {
                                return AppUserRole.ADMIN;
                            }
                            return AppUserRole.valueOf(roleName);
                        } catch (IllegalArgumentException ignored) {
                            return null;
                        }
                    }
                    return null;
                })
                .filter(role -> role != null)
                .findFirst()
                .orElse(AppUserRole.VIEWER);
    }

    /**
     * 根据源状态解析具体的错误码，便于客户端精确处理。
     */
    private int resolveTransitionErrorCode(RegistrationStatus from) {
        return switch (from) {
            case INCOMPLETE -> ApiCodes.INVALID_STATE_TRANSITION_FROM_INCOMPLETE;
            case CANDIDATE -> ApiCodes.INVALID_STATE_TRANSITION_FROM_CANDIDATE;
            case RECORDED -> ApiCodes.INVALID_STATE_TRANSITION_FROM_RECORDED;
            case QUALIFIED -> ApiCodes.INVALID_STATE_TRANSITION_FROM_QUALIFIED;
            case STANDARD -> ApiCodes.INVALID_STATE_TRANSITION_FROM_STANDARD;
            case PREFERRED_STANDARD -> ApiCodes.INVALID_STATE_TRANSITION_FROM_PREFERRED_STANDARD;
            case SUPERSEDED -> ApiCodes.INVALID_STATE_TRANSITION_FROM_SUPERSEDED;
            case RETIRED -> ApiCodes.INVALID_STATE_TRANSITION_FROM_RETIRED;
            case HISTORICAL -> ApiCodes.INVALID_STATE_TRANSITION_FROM_HISTORICAL;
            case APPLICATION -> ApiCodes.INVALID_STATE_TRANSITION_FROM_APPLICATION;
        };
    }

    /**
     * 判断指定角色是否有权执行某状态转换（公开工具方法，供前端权限预览使用）。
     *
     * @param from 源状态
     * @param to   目标状态
     * @param role 角色
     * @return 是否有权限
     */
    @Transactional(readOnly = true)
    public boolean canTransition(RegistrationStatus from, RegistrationStatus to, AppUserRole role) {
        // 先校验状态机规则（所有角色通用）
        Set<RegistrationStatus> allowedStates = ALLOWED_TRANSITIONS.getOrDefault(
                from, EnumSet.noneOf(RegistrationStatus.class));
        if (!allowedStates.contains(to)) {
            return false;
        }

        // ADMIN 拥有所有合法转换的权限
        if (role == AppUserRole.ADMIN) {
            return true;
        }

        Map<RegistrationStatus, Set<AppUserRole>> fromMap = ROLE_PERMISSIONS.get(from);
        if (fromMap == null) {
            return false;
        }

        Set<AppUserRole> allowedRoles = fromMap.get(to);
        return allowedRoles != null && allowedRoles.contains(role);
    }
}
