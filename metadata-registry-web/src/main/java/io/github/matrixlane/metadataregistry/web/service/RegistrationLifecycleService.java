package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationState;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationTransitionRule;
import io.github.matrixlane.metadataregistry.web.auth.AppUserRole;
import io.github.matrixlane.metadataregistry.web.auth.AuthenticatedUser;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.registration.RegistrationStateHistoryEntry;
import io.github.matrixlane.metadataregistry.web.repository.AdministeredItemRepository;
import io.github.matrixlane.metadataregistry.web.auth.AccountRepository;
import io.github.matrixlane.metadataregistry.web.repository.ContactRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationTransitionRuleRepository;
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
 *   <li>Incomplete → Candidate → Recorded → Qualified → Standard → Preferred Standard；</li>
 *   <li>Recorded 及以上可降入 Superseded/Retired；其中 Qualified 及以上降级属默认审核点
 *   （D.4.2/D.4.3：registrar 经 control committee 审议后执行），须经批准生效，
 *   Recorded 级降级保持直推；</li>
 *   <li>Application 可回到 Candidate；Incomplete/Candidate 可直接归档 Historical；</li>
 *   <li>Retired 与 Historical 为终态，无任何出边。</li>
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
     * <p>与 {@link #ALLOWED_TRANSITIONS} 对齐。
     * 本矩阵只约束静态底线路径（无归属注册机构条目）的直推角色；归属注册机构的
     * 条目中，Qualified 及以上降级（含创建 SUPERSEDED，4.3.3.1.8/4.3.3.1.9）已纳入
     * 默认审核点（{@link #DEFAULT_APPROVAL_TRANSITIONS}，D.4.2/D.4.3），直推对所有
     * 角色关闭，改由该机构 Registrar quorum 批准后生效。</p>
     */
    private static final Map<RegistrationStatus, Map<RegistrationStatus, Set<AppUserRole>>> ROLE_PERMISSIONS;

    static {
        EnumMap<RegistrationStatus, Set<RegistrationStatus>> map = new EnumMap<>(RegistrationStatus.class);
        // ISO/IEC 11179-6:2023 标准状态转换（逐状态 Possible progression，2026-09-18 标准核验，
        // 替代此前以旧版代码为准的 21 条迁移集；RETIRED/HISTORICAL 无任何出边）。
        // Application — ISO/IEC 11179-6:2023 中用于标识处于应用系统但尚未逻辑规范化的项
        map.put(RegistrationStatus.INCOMPLETE,
                EnumSet.of(RegistrationStatus.CANDIDATE, RegistrationStatus.HISTORICAL,
                        RegistrationStatus.APPLICATION));
        map.put(RegistrationStatus.CANDIDATE,
                EnumSet.of(RegistrationStatus.RECORDED, RegistrationStatus.HISTORICAL,
                        RegistrationStatus.APPLICATION));
        map.put(RegistrationStatus.APPLICATION,
                EnumSet.of(RegistrationStatus.CANDIDATE));
        map.put(RegistrationStatus.RECORDED,
                EnumSet.of(RegistrationStatus.QUALIFIED, RegistrationStatus.RETIRED,
                        RegistrationStatus.SUPERSEDED));
        map.put(RegistrationStatus.QUALIFIED,
                EnumSet.of(RegistrationStatus.STANDARD, RegistrationStatus.RETIRED,
                        RegistrationStatus.SUPERSEDED));
        map.put(RegistrationStatus.STANDARD,
                EnumSet.of(RegistrationStatus.PREFERRED_STANDARD, RegistrationStatus.RETIRED,
                        RegistrationStatus.SUPERSEDED));
        map.put(RegistrationStatus.PREFERRED_STANDARD,
                EnumSet.of(RegistrationStatus.STANDARD, RegistrationStatus.RETIRED,
                        RegistrationStatus.SUPERSEDED));
        map.put(RegistrationStatus.SUPERSEDED,
                EnumSet.of(RegistrationStatus.RETIRED));
        map.put(RegistrationStatus.RETIRED, EnumSet.noneOf(RegistrationStatus.class));
        map.put(RegistrationStatus.HISTORICAL, EnumSet.noneOf(RegistrationStatus.class));
        ALLOWED_TRANSITIONS = Map.copyOf(map);

        // 初始化角色权限矩阵（与标准迁移集对齐；仅约束静态底线路径直推角色；
        // Qualified+ 降级为默认审核点，归属注册机构的条目经 quorum 批准生效，见
        // DEFAULT_APPROVAL_TRANSITIONS 与 RegistrationProcessService.approve）
        EnumMap<RegistrationStatus, Map<RegistrationStatus, Set<AppUserRole>>> roleMap =
                new EnumMap<>(RegistrationStatus.class);

        // INCOMPLETE
        EnumMap<RegistrationStatus, Set<AppUserRole>> fromIncomplete = new EnumMap<>(RegistrationStatus.class);
        fromIncomplete.put(RegistrationStatus.CANDIDATE, EnumSet.of(AppUserRole.ADMIN, AppUserRole.REGISTRAR));
        fromIncomplete.put(RegistrationStatus.HISTORICAL, EnumSet.of(AppUserRole.ADMIN, AppUserRole.REGISTRAR));
        fromIncomplete.put(RegistrationStatus.APPLICATION, EnumSet.of(AppUserRole.ADMIN, AppUserRole.REGISTRAR));
        roleMap.put(RegistrationStatus.INCOMPLETE, Map.copyOf(fromIncomplete));

        // CANDIDATE
        EnumMap<RegistrationStatus, Set<AppUserRole>> fromSubmitted = new EnumMap<>(RegistrationStatus.class);
        fromSubmitted.put(RegistrationStatus.RECORDED, EnumSet.of(AppUserRole.ADMIN, AppUserRole.REGISTRAR));
        fromSubmitted.put(RegistrationStatus.HISTORICAL, EnumSet.of(AppUserRole.ADMIN, AppUserRole.REGISTRAR));
        fromSubmitted.put(RegistrationStatus.APPLICATION, EnumSet.of(AppUserRole.ADMIN, AppUserRole.REGISTRAR));
        roleMap.put(RegistrationStatus.CANDIDATE, Map.copyOf(fromSubmitted));

        // APPLICATION
        EnumMap<RegistrationStatus, Set<AppUserRole>> fromApplication = new EnumMap<>(RegistrationStatus.class);
        fromApplication.put(RegistrationStatus.CANDIDATE, EnumSet.of(AppUserRole.ADMIN, AppUserRole.REGISTRAR));
        roleMap.put(RegistrationStatus.APPLICATION, Map.copyOf(fromApplication));

        // RECORDED
        EnumMap<RegistrationStatus, Set<AppUserRole>> fromRecorded = new EnumMap<>(RegistrationStatus.class);
        fromRecorded.put(RegistrationStatus.QUALIFIED, EnumSet.of(AppUserRole.ADMIN, AppUserRole.REGISTRAR));
        fromRecorded.put(RegistrationStatus.RETIRED, EnumSet.of(AppUserRole.ADMIN, AppUserRole.REGISTRAR));
        fromRecorded.put(RegistrationStatus.SUPERSEDED, EnumSet.of(AppUserRole.ADMIN));
        roleMap.put(RegistrationStatus.RECORDED, Map.copyOf(fromRecorded));

        // QUALIFIED
        EnumMap<RegistrationStatus, Set<AppUserRole>> fromQualified = new EnumMap<>(RegistrationStatus.class);
        fromQualified.put(RegistrationStatus.STANDARD, EnumSet.of(AppUserRole.ADMIN, AppUserRole.REGISTRAR));
        fromQualified.put(RegistrationStatus.RETIRED, EnumSet.of(AppUserRole.ADMIN, AppUserRole.REGISTRAR));
        fromQualified.put(RegistrationStatus.SUPERSEDED, EnumSet.of(AppUserRole.ADMIN));
        roleMap.put(RegistrationStatus.QUALIFIED, Map.copyOf(fromQualified));

        // STANDARD
        EnumMap<RegistrationStatus, Set<AppUserRole>> fromStandard = new EnumMap<>(RegistrationStatus.class);
        fromStandard.put(RegistrationStatus.PREFERRED_STANDARD, EnumSet.of(AppUserRole.ADMIN, AppUserRole.REGISTRAR));
        fromStandard.put(RegistrationStatus.RETIRED, EnumSet.of(AppUserRole.ADMIN, AppUserRole.REGISTRAR));
        fromStandard.put(RegistrationStatus.SUPERSEDED, EnumSet.of(AppUserRole.ADMIN));
        roleMap.put(RegistrationStatus.STANDARD, Map.copyOf(fromStandard));

        // PREFERRED
        EnumMap<RegistrationStatus, Set<AppUserRole>> fromPreferred = new EnumMap<>(RegistrationStatus.class);
        fromPreferred.put(RegistrationStatus.STANDARD, EnumSet.of(AppUserRole.ADMIN, AppUserRole.REGISTRAR));
        fromPreferred.put(RegistrationStatus.RETIRED, EnumSet.of(AppUserRole.ADMIN, AppUserRole.REGISTRAR));
        fromPreferred.put(RegistrationStatus.SUPERSEDED, EnumSet.of(AppUserRole.ADMIN));
        roleMap.put(RegistrationStatus.PREFERRED_STANDARD, Map.copyOf(fromPreferred));

        // SUPERSEDED
        EnumMap<RegistrationStatus, Set<AppUserRole>> fromSuperseded = new EnumMap<>(RegistrationStatus.class);
        fromSuperseded.put(RegistrationStatus.RETIRED, EnumSet.of(AppUserRole.ADMIN, AppUserRole.REGISTRAR));
        roleMap.put(RegistrationStatus.SUPERSEDED, Map.copyOf(fromSuperseded));

        // RETIRED — 无允许转换（终态）
        roleMap.put(RegistrationStatus.RETIRED, Map.of());

        // HISTORICAL — 无允许转换
        roleMap.put(RegistrationStatus.HISTORICAL, Map.of());

        ROLE_PERMISSIONS = Map.copyOf(roleMap);
    }

    private final AdministeredItemRepository administeredItemRepository;
    private final AccountRepository accountRepository;
    private final ContactRepository contactRepository;
    private final ContactAccountBindingService contactAccountBindingService;
    private final RegistrationTransitionRuleRepository transitionRuleRepository;
    private final AdministrativeStatusService administrativeStatusService;

    /**
     * 执行状态转换（交互路径）。
     *
     * <p>校验迁移白名单（规则表，registration-process-designer）与角色权限；
     * 目标迁移配置了 requires_approval 时拒绝直接迁移，须经批准累积达 quorum
     * 由 {@link RegistrationProcessService#approve} 生效。审批生效路径使用
     * {@link #applyTransition}（跳过交互角色与审核点校验）。</p>
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

        AdministeredItem ai = loadItem(administeredItemId);
        RegistrationStatus from = currentStatusOf(ai);

        // 校验状态转换合法性（规则表；无归属注册机构的条目回退标准静态底线）
        RegistrationTransitionRule rule = validateTransition(from, to, authorityIdOf(ai));

        // 审核点迁移拒绝直接 transition（提示需经批准流程生效）
        if (rule != null && rule.isRequiresApproval()) {
            throw new BusinessException(ApiCodes.TRANSITION_REQUIRES_APPROVAL,
                    "迁移 %s → %s 需经注册机构批准后方可生效，请使用批准操作"
                            .formatted(from, to));
        }
        // M1 静态底线回退守卫：默认需审核迁移须注册机构批准；无归属注册机构的
        // 条目没有批准主体，一律拒绝直推。
        if (rule == null && ai.getRegistrationAuthority() == null
                && isDefaultApprovalTransition(from, to)) {
            throw new BusinessException(ApiCodes.TRANSITION_REQUIRES_APPROVAL,
                    ("条目未归属注册机构，无法经注册机构审核推进到 %s（%s → %s）；"
                            + "请先为条目归属注册机构，再经推进流程批准生效")
                            .formatted(to, from, to));
        }

        // 校验角色权限
        AppUserRole currentRole = resolveCurrentRole();
        validateRolePermission(from, to, currentRole);

        return writeTransition(ai, from, to, administrativeNote);
    }

    /**
     * 审批生效路径的底层状态写入（registration-process-designer）。
     *
     * <p>迁移合法性（规则表）与 Recorded+ 强制属性校验照常执行；不重复校验
     * 交互角色与审核点——授权已由"合格 Registrar 批准达 quorum"表达。
     * 由 {@link RegistrationProcessService#approve} 在同一事务内调用。</p>
     */
    AdministeredItem applyTransition(UUID administeredItemId,
                                     RegistrationStatus to,
                                     String administrativeNote) {
        AdministeredItem ai = loadItem(administeredItemId);
        RegistrationStatus from = currentStatusOf(ai);
        validateTransition(from, to, authorityIdOf(ai));
        return writeTransition(ai, from, to, administrativeNote);
    }

    private AdministeredItem loadItem(UUID administeredItemId) {
        return administeredItemRepository.findById(administeredItemId)
                .orElseThrow(() -> new BusinessException(
                        ApiCodes.ADMINISTERED_ITEM_NOT_FOUND,
                        "管理项不存在: " + administeredItemId));
    }

    private static RegistrationStatus currentStatusOf(AdministeredItem ai) {
        RegistrationState current = ai.getRegistrationState();
        return current == null ? RegistrationStatus.INCOMPLETE
                : current.getRegistrationStatus();
    }

    private static UUID authorityIdOf(AdministeredItem ai) {
        return ai.getRegistrationAuthority() == null ? null
                : ai.getRegistrationAuthority().getId();
    }

    /**
     * 状态写入：历史链（旧状态置 until_date、新状态链接 previous_state）、
     * 生效时间与强制属性校验。
     */
    private AdministeredItem writeTransition(AdministeredItem ai,
                                             RegistrationStatus from,
                                             RegistrationStatus to,
                                             String administrativeNote) {
        RegistrationState current = ai.getRegistrationState();

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
        // 注册状态迁移后清空管理状态（不新建状态行、不动 previous_state 链）
        administrativeStatusService.clear(ai);
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

    /**
     * 默认需审核迁移集合（"FROM→TO"），共 10 处，两类来源：
     * <ul>
     *   <li>标准强制的向上迁移（目标 Qualified 及以上须经
     *   注册机构批准）：RECORDED→QUALIFIED、QUALIFIED→STANDARD、
     *   STANDARD→PREFERRED_STANDARD；</li>
     *   <li>标准强制的 Qualified 及以上降级：
     *   "changed to 'Retired'/'Superseded' by the registrar … after presentation to the
     *   control committee"，以及 SUPERSEDED→RETIRED（Provisionally Retired
     *   经委员会批准），共 7 条。</li>
     * </ul>
     *
     * <p>按迁移对判定（非目标状态）：如 PREFERRED_STANDARD→STANDARD 目标为 STANDARD
     * 但不属审核点，不得误伤。CANDIDATE→RECORDED 由 submitter/steward
     * 补齐强制属性即可晋升、无需批准，<b>不在</b>集合内（2026-09-19 曾列为治理加严
     * 审核点，经标准原文复核撤销；标准建议的 steward 复审/registrar 筛查
     * 为建议性实践，本系统以 REGISTRAR 角色门槛近似——无独立 Submitter/Steward
     * 角色）。同理标准明确 Recorded 级降级无需审查，RECORDED→RETIRED /
     * RECORDED→SUPERSEDED 不在集合内，保持直推。无归属注册机构的条目没有批准主体，
     * 底线回退路径对集合内迁移一律拒绝直推（M1）。</p>
     */
    static final Set<String> DEFAULT_APPROVAL_TRANSITIONS = Set.of(
            // 4.4 d) 向上迁移（3 处）
            "RECORDED→QUALIFIED",
            "QUALIFIED→STANDARD",
            "STANDARD→PREFERRED_STANDARD",
            // D.4.2/D.4.3 Qualified 及以上降级 + D.2.9 SUPERSEDED→RETIRED（7 处）
            "QUALIFIED→RETIRED",
            "STANDARD→RETIRED",
            "PREFERRED_STANDARD→RETIRED",
            "QUALIFIED→SUPERSEDED",
            "STANDARD→SUPERSEDED",
            "PREFERRED_STANDARD→SUPERSEDED",
            "SUPERSEDED→RETIRED");

    static boolean isDefaultApprovalTransition(RegistrationStatus from, RegistrationStatus to) {
        return DEFAULT_APPROVAL_TRANSITIONS.contains(from + "→" + to);
    }

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

    /**
     * 校验状态转换的合法性（仅校验状态机规则，不校验权限）。
     *
     * <p>registration-process-designer：白名单数据源为规则表——指定注册机构
     * 下存在 enabled 规则即允许；无归属注册机构的条目回退
     * {@link #standardTransitions()} 静态底线（含种子播种与旧数据兼容）。</p>
     *
     * @param from        当前状态
     * @param to          目标状态
     * @param authorityId 管理项归属注册机构；null 时走静态底线
     * @return 命中的迁移规则；静态底线路径返回 null
     * @throws BusinessException 转换非法时抛出
     */
    RegistrationTransitionRule validateTransition(RegistrationStatus from, RegistrationStatus to,
                                                  UUID authorityId) {
        if (from == to) {
            throw new BusinessException(ApiCodes.ALREADY_IN_TARGET_STATE,
                    "当前状态已经是 " + to + "，无需迁移");
        }

        if (authorityId != null) {
            RegistrationTransitionRule rule = transitionRuleRepository
                    .findByRegistrationAuthorityIdAndFromStatusAndToStatus(authorityId, from, to)
                    .orElse(null);
            if (rule == null || !rule.isEnabled()) {
                throw new BusinessException(resolveTransitionErrorCode(from),
                        "非法的状态迁移：%s → %s".formatted(from, to));
            }
            return rule;
        }

        Set<RegistrationStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(
                from, EnumSet.noneOf(RegistrationStatus.class));
        if (!allowed.contains(to)) {
            throw new BusinessException(resolveTransitionErrorCode(from),
                    "非法的状态迁移：%s → %s".formatted(from, to));
        }
        return null;
    }

    void validateTransition(RegistrationStatus from, RegistrationStatus to) {
        validateTransition(from, to, null);
    }

    /**
     * 标准迁移底线（逐状态 Possible
     * progression，共 20 条；2026-09-18 标准核验，替代此前以旧版代码为准的 21 条）。
     *
     * <p>registration-process-designer：作为规则表种子数据来源（ensureSeed 调和），
     * 以及无归属注册机构条目的白名单回退。</p>
     */
    public static Map<RegistrationStatus, Set<RegistrationStatus>> standardTransitions() {
        return ALLOWED_TRANSITIONS;
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
     * 仅按角色矩阵判断可否执行迁移（不校验迁移白名单）。
     *
     * <p>registration-process-designer：白名单以规则表为准（{@code availableTransitions}
     * 只列入 enabled 规则），本方法只回答"该角色在现行 ADMIN/REGISTRAR 矩阵下有无
     * 执行权"，供执行视图组装 canExecute 使用。</p>
     *
     * @param from 源状态
     * @param to   目标状态
     * @param role 角色
     * @return 角色矩阵内是否有执行权
     */
    public boolean canExecuteByRole(RegistrationStatus from, RegistrationStatus to, AppUserRole role) {
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
            // Registrar 身份由 Contact 子类 Registrar 实例确定（类型即身份，与 Role 无关）
            if (!contactAccountBindingService.registrarsOfAccount(user.id()).isEmpty()) {
                return AppUserRole.REGISTRAR;
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
