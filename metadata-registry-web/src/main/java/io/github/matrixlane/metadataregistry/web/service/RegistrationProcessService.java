package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministrativeStatus;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.ApproverMode;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.QuorumType;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.Registrar;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationState;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationTransitionApproval;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationTransitionRule;
import io.github.matrixlane.metadataregistry.web.auth.AppUserRole;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.registration.ApprovalResultResponse;
import io.github.matrixlane.metadataregistry.web.dto.registration.AvailableTransitionView;
import io.github.matrixlane.metadataregistry.web.dto.registration.RegistrationProcessResponse;
import io.github.matrixlane.metadataregistry.web.repository.AdministeredItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationTransitionApprovalRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationTransitionRuleRepository;
import io.github.matrixlane.metadataregistry.web.service.AdministrativeStatusService;
import io.github.matrixlane.metadataregistry.web.service.ItemAdministrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 注册流程服务（registration-process-designer）。
 *
 * <p>以标准注册状态生命周期为骨架，按注册机构作用域管理
 * 迁移规则（白名单、审核点、审核人模式、通过规则）并驱动两种状态迁移路径：</p>
 * <ul>
 *   <li>执行（execute）：无需审核的迁移由具备现行 ADMIN/REGISTRAR 权限的用户直接生效；</li>
 *   <li>批准（approve）：需审核的迁移由合格 Registrar 累积批准，达 quorum 在同一
 *       事务内自动生效并清除该项全部审批记录；状态经其他路径变化时清除陈旧审批。</li>
 * </ul>
 *
 * <p>规则查询直接走 Repository（每 RA 20 条标准规则 + 定制规则，不做缓存）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RegistrationProcessService {

    /**
     * 默认需审核迁移集合：标准强制的向上迁移（→QUALIFIED /
     * →STANDARD / →PREFERRED_STANDARD）+ 标准强制的 Qualified+ 降级
     * （共 10 处，见 {@link RegistrationLifecycleService#DEFAULT_APPROVAL_TRANSITIONS}）。
     * 判定与回退守卫统一走
     * {@link RegistrationLifecycleService#isDefaultApprovalTransition}（按迁移对，
     * 非目标状态）。无归属注册机构的条目无批准主体：底线回退路径对集合内迁移
     * 拒绝直推并标记需审核。
     */

    private final RegistrationTransitionRuleRepository ruleRepository;
    private final RegistrationTransitionApprovalRepository approvalRepository;
    private final AdministeredItemRepository administeredItemRepository;
    private final RegistrationAuthorityRepository registrationAuthorityRepository;
    private final ActiveRegistrarService activeRegistrarService;
    private final RegistrationLifecycleService registrationLifecycleService;
    private final AdministrativeStatusService administrativeStatusService;
    private final ItemAdministrationService itemAdministrationService;

    // =========================================================================
    // 流程定义（查询 + 种子）
    // =========================================================================

    /**
     * 当前注册机构的完整流程定义（注册流程页数据源）。
     *
     * <p>当前 RA 解析顺序：当前账号的活动 Registrar 身份所属 RA → 库内首个 RA；
     * 均不存在时抛出业务错误（1123）。</p>
     */
    public RegistrationProcessResponse getCurrentProcess() {
        UUID authorityId = resolveCurrentAuthorityId();
        return getProcess(authorityId);
    }

    /** 指定注册机构的完整流程定义（含幂等播种）。 */
    public RegistrationProcessResponse getProcess(UUID authorityId) {
        ensureSeed(authorityId);
        List<RegistrationProcessResponse.TransitionRuleView> transitions = ruleRepository
                .findByRegistrationAuthorityId(authorityId).stream()
                .map(RegistrationProcessService::toRuleView)
                .sorted(Comparator.comparing(RegistrationProcessResponse.TransitionRuleView::from)
                        .thenComparing(RegistrationProcessResponse.TransitionRuleView::to))
                .toList();
        return new RegistrationProcessResponse(authorityId, transitions);
    }

    /**
     * 调和式幂等播种标准默认规则（设计文档 §2.5：共 20 条迁移、10 处默认审核点）。
     *
     * <p>对种子规则（displayName 为空即未被定制）执行调和：插入标准集内缺失项、
     * 删除标准集外的旧种子项、字段漂移归位——2026-09-18 标准核验将迁移集由 21 条
     * 纠正为 20 条；2026-09-19 依据 D.4.2/D.4.3/D.2.9 将 7 条 Qualified+ 降级迁移
     * 纳入默认审核点（CANDIDATE→RECORDED 曾同日加严为审核点，经 4.4 c) 原文复核后
     * 撤销——submitter/steward 补齐强制属性即可晋升，无需批准）；
     * 已播种旧规则的库在下次 ensureSeed 时自动收敛（requiresApproval 归位为
     * 默认集合定义值）。displayName 非空（未来定制入口编辑过）的规则
     * 一律不动；规则来源为 {@link RegistrationLifecycleService#standardTransitions()}
     * 标准底线与 {@link RegistrationLifecycleService#isDefaultApprovalTransition}。</p>
     */
    public void ensureSeed(UUID authorityId) {
        RegistrationAuthority authority = registrationAuthorityRepository.findById(authorityId)
                .orElseThrow(() -> new ResourceNotFoundException("RegistrationAuthority", authorityId));

        Map<RegistrationStatus, Set<RegistrationStatus>> standard =
                RegistrationLifecycleService.standardTransitions();

        // 调和①③：遍历既有规则——标准集外的种子删除（displayName 非空 = 定制规则，
        // 不动）；标准集内的种子做字段漂移归位（未定制规则的审核配置以默认定义为准）。
        // ElementCollection 子表随父记录级联清理；与下方插入共用同一事务。
        for (RegistrationTransitionRule existing : ruleRepository.findByRegistrationAuthorityId(authorityId)) {
            if (existing.getDisplayName() != null) {
                continue;
            }
            boolean stillStandard = standard.getOrDefault(existing.getFromStatus(), Set.of())
                    .contains(existing.getToStatus());
            if (!stillStandard) {
                log.info("注册流程规则 {} → {} 已不在标准迁移集内，删除旧种子规则",
                        existing.getFromStatus(), existing.getToStatus());
                ruleRepository.delete(existing);
                continue;
            }
            // 字段漂移归位：requiresApproval 以默认需审核集合为准，
            // 其余审核配置归位到种子默认（全部 Registrar / 任一人 / 无 quorum_count / 启用）。
            boolean defaultApproval = RegistrationLifecycleService.isDefaultApprovalTransition(
                    existing.getFromStatus(), existing.getToStatus());
            boolean changed = false;
            if (existing.isRequiresApproval() != defaultApproval) {
                existing.setRequiresApproval(defaultApproval);
                changed = true;
            }
            if (existing.getApproverMode() != ApproverMode.ALL_REGISTRARS) {
                existing.setApproverMode(ApproverMode.ALL_REGISTRARS);
                changed = true;
            }
            if (existing.getQuorumType() != QuorumType.ANY_ONE) {
                existing.setQuorumType(QuorumType.ANY_ONE);
                changed = true;
            }
            if (existing.getQuorumCount() != null) {
                existing.setQuorumCount(null);
                changed = true;
            }
            if (!existing.isEnabled()) {
                existing.setEnabled(true);
                changed = true;
            }
            if (changed) {
                log.info("注册流程种子规则 {} → {} 字段漂移，已调和回默认定义",
                        existing.getFromStatus(), existing.getToStatus());
                ruleRepository.saveAndFlush(existing);
            }
        }

        // 调和②：插入标准集内缺失的种子规则（幂等；并发播种撞唯一约束按已播种跳过）
        for (Map.Entry<RegistrationStatus, Set<RegistrationStatus>> e : standard.entrySet()) {
            for (RegistrationStatus to : e.getValue()) {
                if (ruleRepository.existsByRegistrationAuthorityIdAndFromStatusAndToStatus(
                        authorityId, e.getKey(), to)) {
                    continue;
                }
                RegistrationTransitionRule rule = new RegistrationTransitionRule();
                rule.setRegistrationAuthority(authority);
                rule.setFromStatus(e.getKey());
                rule.setToStatus(to);
                rule.setRequiresApproval(
                        RegistrationLifecycleService.isDefaultApprovalTransition(e.getKey(), to));
                rule.setApproverMode(ApproverMode.ALL_REGISTRARS);
                rule.setQuorumType(QuorumType.ANY_ONE);
                rule.setEnabled(true);
                try {
                    // saveAndFlush：让唯一约束冲突在本方法内暴露（否则延迟到事务提交无法捕获）
                    ruleRepository.saveAndFlush(rule);
                } catch (DataIntegrityViolationException ex) {
                    // 并发播种（启动 Runner 与首请求同时）撞唯一约束：视为已播种，幂等跳过
                    log.warn("注册流程规则并发播种冲突，按已存在跳过：{} → {}", e.getKey(), to);
                }
            }
        }
    }

    /**
     * 解析当前 RA：优先当前账号的活动 Registrar 身份；无身份（或未登录）时
     * 回退到库内首个 RA（本期一个应用即一个注册机构）。
     */
    UUID resolveCurrentAuthorityId() {
        Optional<Registrar> active = activeRegistrarService.resolveForCurrentUser();
        if (active.isPresent() && active.get().getRegistrationAuthority() != null) {
            return active.get().getRegistrationAuthority().getId();
        }
        List<RegistrationAuthority> all =
                registrationAuthorityRepository.findAll(PageRequest.of(0, 1)).getContent();
        if (!all.isEmpty()) {
            return all.get(0).getId();
        }
        throw new BusinessException(ApiCodes.REGISTRATION_AUTHORITY_NOT_FOUND,
                "未找到注册机构，无法提供注册流程");
    }

    private static RegistrationProcessResponse.TransitionRuleView toRuleView(RegistrationTransitionRule rule) {
        return new RegistrationProcessResponse.TransitionRuleView(
                rule.getFromStatus(),
                rule.getToStatus(),
                rule.getDisplayName(),
                rule.isRequiresApproval(),
                rule.getApproverMode(),
                rule.getQuorumType(),
                rule.getQuorumCount(),
                List.copyOf(rule.getApproverRegistrarIds()),
                rule.isEnabled());
    }

    // =========================================================================
    // 迁移动作视图
    // =========================================================================

    /**
     * 该项当前状态的可达迁移动作视图（推进流程面板数据源）。
     *
     * <p>每条迁移给出目标状态、操作名、是否需审核、审批进度与当前用户权限
     * （可否执行/可否批准/是否已批准）。无归属注册机构的条目回退标准底线，
     * 视图全部视为无需审核。</p>
     *
     * @param itemId    注册项
     * @param accountId 当前账号（可为 null：仅进度只读视图）
     */
    public List<AvailableTransitionView> availableTransitions(UUID itemId, UUID accountId) {
        AdministeredItem ai = loadItem(itemId);
        RegistrationStatus from = currentStatusOf(ai);
        UUID authorityId = authorityIdOf(ai);

        List<RegistrationTransitionRule> rules;
        if (authorityId != null) {
            ensureSeed(authorityId);
            rules = ruleRepository.findByRegistrationAuthorityIdAndFromStatusAndEnabledTrue(authorityId, from);
        } else {
            rules = new ArrayList<>();
            for (RegistrationStatus to :
                    RegistrationLifecycleService.standardTransitions().getOrDefault(from, Set.of())) {
                RegistrationTransitionRule fallback = new RegistrationTransitionRule();
                fallback.setFromStatus(from);
                fallback.setToStatus(to);
                // M1：默认需审核迁移在无批准主体的回退视图中标记需审核且不可直推；
                // 按迁移对判定，非目标状态
                fallback.setRequiresApproval(
                        RegistrationLifecycleService.isDefaultApprovalTransition(from, to));
                fallback.setEnabled(true);
                rules.add(fallback);
            }
        }

        AppUserRole role = registrationLifecycleService.resolveCurrentRole();
        Optional<Registrar> activeRegistrar = accountId == null
                ? Optional.empty()
                : activeRegistrarService.resolveActiveRegistrar(accountId);

        List<AvailableTransitionView> views = new ArrayList<>();
        for (RegistrationTransitionRule rule : rules) {
            List<RegistrationTransitionApproval> approvals = approvalRepository
                    .findByAdministeredItemIdAndFromStatusAndToStatus(itemId, from, rule.getToStatus());
            int quorum = quorumOf(rule);
            boolean approvedByMe = activeRegistrar
                    .map(r -> approvals.stream().anyMatch(a -> r.getId().equals(registrarIdOf(a))))
                    .orElse(false);
            // canExecute 对齐规则表：requires_approval（或回退下无批准主体）的目标不可直推；
            // 角色矩阵另经 canExecuteByRole 判定（白名单已由规则列表表达，不再查静态 Map）
            boolean canExecute = !rule.isRequiresApproval()
                    && registrationLifecycleService.canExecuteByRole(from, rule.getToStatus(), role);
            boolean canApprove = rule.isRequiresApproval()
                    && isQualifiedApprover(activeRegistrar, authorityId, rule)
                    && !approvedByMe;
            views.add(new AvailableTransitionView(
                    rule.getToStatus(),
                    rule.getDisplayName(),
                    rule.isRequiresApproval(),
                    approvals.size(),
                    quorum,
                    canExecute,
                    canApprove,
                    approvedByMe));
        }
        return views;
    }

    private static UUID registrarIdOf(RegistrationTransitionApproval approval) {
        return approval.getRegistrar() == null ? null : approval.getRegistrar().getId();
    }

    // =========================================================================
    // 执行（无需审核迁移）
    // =========================================================================

    /**
     * 直接执行无需审核的迁移。
     *
     * <p>沿用现行 ADMIN/REGISTRAR 角色矩阵与 Recorded+ 强制属性校验
     * （委托 {@link RegistrationLifecycleService#transition}）。需审核的迁移
     * 被拒绝（1120）；非法迁移由生命周期服务按源状态返回具体错误码。
     * 迁移生效后清除该项全部未生效审批（防陈旧）。</p>
     */
    public AdministeredItem execute(UUID itemId, RegistrationStatus to, String note, UUID accountId) {
        if (to == null) {
            throw new BusinessException(ApiCodes.TARGET_STATE_REQUIRED, "目标状态不能为空");
        }
        AdministeredItem ai = loadItem(itemId);
        RegistrationStatus from = currentStatusOf(ai);
        UUID authorityId = authorityIdOf(ai);

        RegistrationTransitionRule rule = null;
        if (authorityId != null) {
            ensureSeed(authorityId);
            rule = ruleRepository
                    .findByRegistrationAuthorityIdAndFromStatusAndToStatus(authorityId, from, to)
                    .orElse(null);
            if (rule != null && rule.isEnabled()) {
                if (rule.isRequiresApproval()) {
                    throw new BusinessException(ApiCodes.TRANSITION_REQUIRES_APPROVAL,
                            "迁移 %s → %s 需经注册机构批准后方可生效，请使用批准操作".formatted(from, to));
                }
            } else {
                // 无启用规则：与生命周期服务同一数据源，此处抛出非法迁移错误
                registrationLifecycleService.validateTransition(from, to, authorityId);
                throw new BusinessException(ApiCodes.INVALID_STATE_TRANSITION,
                        "非法的状态迁移：%s → %s".formatted(from, to));
            }
        } else {
            registrationLifecycleService.validateTransition(from, to, null); // 静态底线
            // M1：无批准主体（未归属注册机构）时默认需审核迁移拒绝直推
            //（与生命周期服务守卫一致）
            if (RegistrationLifecycleService.isDefaultApprovalTransition(from, to)) {
                throw new BusinessException(ApiCodes.TRANSITION_REQUIRES_APPROVAL,
                        ("条目未归属注册机构，无法经注册机构审核推进到 %s（%s → %s）；"
                                + "请先为条目归属注册机构，再经推进流程批准生效")
                                .formatted(to, from, to));
            }
        }

        // Submitter 白名单：INCOMPLETE→CANDIDATE 允许归属本用户的 Submitter 直推
        if (from == RegistrationStatus.INCOMPLETE && to == RegistrationStatus.CANDIDATE
                && activeRegistrarService.isSubmitter(accountId)) {
            if (ai.getStewardshipRecord() == null) {
                throw new BusinessException(ApiCodes.CANDIDATE_MINIMUM_REQUIREMENT_NOT_MET,
                        "INCOMPLETE→CANDIDATE 白名单迁移要求管理项具有主管记录");
            }
            if (itemAdministrationService.isItemOwnedByAccount(ai, accountId)) {
                AdministeredItem saved = registrationLifecycleService.applyTransition(itemId, to, note);
                approvalRepository.deleteByAdministeredItemId(itemId);
                return saved;
            }
        }

        AdministeredItem saved = registrationLifecycleService.transition(itemId, to, note);
        approvalRepository.deleteByAdministeredItemId(itemId);
        return saved;
    }

    // =========================================================================
    // 批准（需审核迁移，累积达 quorum 自动生效）
    // =========================================================================

    /**
     * 批准需审核的迁移。
     *
     * <p>批准人资格：当前账号的活动 Registrar 身份归属该项所属注册机构，且在
     * 规则审核人集合内（ALL_REGISTRARS 时即该机构全部 Registrar）。同一
     * Registrar 对同一迁移只计一次（1122 拒绝重复）。去重后批准人数达到
     * quorum 时，在同一事务内执行状态迁移并清除该项全部审批记录；生效时的
     * 强制属性校验失败使整个事务回滚。</p>
     *
     * @return 批准结果（是否生效 + 该项最新迁移动作视图）
     */
    public ApprovalResultResponse approve(UUID itemId, RegistrationStatus to, UUID accountId) {
        if (to == null) {
            throw new BusinessException(ApiCodes.TARGET_STATE_REQUIRED, "目标状态不能为空");
        }
        AdministeredItem ai = loadItem(itemId);
        RegistrationStatus from = currentStatusOf(ai);
        UUID authorityId = authorityIdOf(ai);
        if (authorityId == null) {
            registrationLifecycleService.validateTransition(from, to, null);
            throw new BusinessException(ApiCodes.TRANSITION_APPROVAL_NOT_REQUIRED,
                    "该注册项未归属注册机构，迁移无需审核，请使用执行操作");
        }
        ensureSeed(authorityId);
        RegistrationTransitionRule rule = ruleRepository
                .findByRegistrationAuthorityIdAndFromStatusAndToStatus(authorityId, from, to)
                .orElse(null);
        if (rule == null || !rule.isEnabled() || !rule.isRequiresApproval()) {
            throw new BusinessException(ApiCodes.TRANSITION_APPROVAL_NOT_REQUIRED,
                    "迁移 %s → %s 不存在或无需审核，请使用执行操作".formatted(from, to));
        }

        Registrar approver = (accountId == null ? Optional.<Registrar>empty()
                : activeRegistrarService.resolveActiveRegistrar(accountId))
                .filter(r -> r.getRegistrationAuthority() != null
                        && authorityId.equals(r.getRegistrationAuthority().getId()))
                .filter(r -> rule.getApproverMode() == ApproverMode.ALL_REGISTRARS
                        || rule.getApproverRegistrarIds().contains(r.getId()))
                .orElseThrow(() -> new AccessDeniedException(
                        "仅该项所属注册机构审核人集合内的活动 Registrar 可批准该迁移"));

        if (approvalRepository.existsByAdministeredItemIdAndFromStatusAndToStatusAndRegistrarId(
                itemId, from, to, approver.getId())) {
            throw new BusinessException(ApiCodes.DUPLICATE_APPROVAL,
                    "您已批准过该迁移，请勿重复批准");
        }

        long approvalsAfter;
        try {
            RegistrationTransitionApproval approval = new RegistrationTransitionApproval();
            approval.setAdministeredItem(ai);
            approval.setFromStatus(from);
            approval.setToStatus(to);
            approval.setRegistrar(approver);
            approval.setAccountId(accountId);
            approval.setApprovedAt(LocalDateTime.now());
            approvalRepository.save(approval);
            // countBy 触发自动 flush：并发批准撞 (item, from, to, registrar) 唯一约束在此暴露
            approvalsAfter = approvalRepository.countByAdministeredItemIdAndFromStatusAndToStatus(
                    itemId, from, to);
        } catch (DataIntegrityViolationException e) {
            // 并发批准同一迁移：另一事务已为本 Registrar 写入批准记录（先查后写竞态），
            // 唯一约束兜底。映射为重复批准（1122）而非 500，与显式查重语义一致。
            throw new BusinessException(ApiCodes.DUPLICATE_APPROVAL,
                    "您已批准过该迁移，请勿重复批准");
        }

        boolean executed = false;
        if (approvalsAfter >= quorumOf(rule)) {
            // 达 quorum：清除该项全部未生效审批（含本迁移），随后在同一事务内生效
            approvalRepository.deleteByAdministeredItemId(itemId);
            registrationLifecycleService.applyTransition(itemId, to,
                    "批准生效：%s → %s（%d 人批准）".formatted(from, to, quorumOf(rule)));
            executed = true;
        }

        return new ApprovalResultResponse(executed, availableTransitions(itemId, accountId));
    }

    // =========================================================================
    // 管理状态操作（工作空间流程）
    // =========================================================================

    /**
     * 驳回进行中的 RECORDED→QUALIFIED 申请。
     *
     * <p>仅当管理项处于 PROVISIONALLY_QUALIFIED 且存在未生效的 RECORDED→QUALIFIED
     * 批准记录时可驳回；操作者须为所属 RA 审核人集合内的活动 Registrar。
     * 驳回后注册状态保持 RECORDED，管理状态变为 REJECTED，该迁移的未生效批准记录
     * 被作废。</p>
     */
    public AdministeredItem reject(UUID itemId, String note, UUID accountId) {
        AdministeredItem ai = loadItem(itemId);
        RegistrationStatus from = currentStatusOf(ai);
        UUID authorityId = authorityIdOf(ai);

        if (from != RegistrationStatus.RECORDED) {
            throw new BusinessException(resolveTransitionErrorCode(from),
                    "仅 RECORDED 状态的管理项可被驳回申请");
        }
        if (authorityId == null) {
            throw new BusinessException(ApiCodes.REGISTRATION_AUTHORITY_NOT_FOUND,
                    "未归属注册机构的管理项无批准主体，无法驳回");
        }
        if (ai.getRegistrationState() == null
                || ai.getRegistrationState().getAdministrativeStatus() != AdministrativeStatus.PROVISIONALLY_QUALIFIED) {
            throw new BusinessException(ApiCodes.INVALID_ADMINISTRATIVE_STATUS_FOR_REJECTION,
                    "管理项未处于申请合格中（PROVISIONALLY_QUALIFIED），无法驳回");
        }

        ensureSeed(authorityId);
        RegistrationTransitionRule rule = ruleRepository
                .findByRegistrationAuthorityIdAndFromStatusAndToStatus(
                        authorityId, RegistrationStatus.RECORDED, RegistrationStatus.QUALIFIED)
                .orElse(null);
        if (rule == null || !rule.isEnabled() || !rule.isRequiresApproval()) {
            throw new BusinessException(ApiCodes.TRANSITION_APPROVAL_NOT_REQUIRED,
                    "迁移 RECORDED → QUALIFIED 不存在或无需审核，无法驳回");
        }

        List<RegistrationTransitionApproval> pendingApprovals = approvalRepository
                .findByAdministeredItemIdAndFromStatusAndToStatus(
                        itemId, RegistrationStatus.RECORDED, RegistrationStatus.QUALIFIED);
        if (pendingApprovals.isEmpty()) {
            throw new BusinessException(ApiCodes.NO_PENDING_QUALIFICATION_APPLICATION,
                    "无进行中的 RECORDED → QUALIFIED 申请，无法驳回");
        }

        Registrar approver = (accountId == null ? Optional.<Registrar>empty()
                : activeRegistrarService.resolveActiveRegistrar(accountId))
                .filter(r -> r.getRegistrationAuthority() != null
                        && authorityId.equals(r.getRegistrationAuthority().getId()))
                .filter(r -> rule.getApproverMode() == ApproverMode.ALL_REGISTRARS
                        || rule.getApproverRegistrarIds().contains(r.getId()))
                .orElseThrow(() -> new AccessDeniedException(
                        "仅该项所属注册机构审核人集合内的活动 Registrar 可驳回该申请"));

        administrativeStatusService.setRejected(ai);
        approvalRepository.deleteByAdministeredItemIdAndFromStatusAndToStatus(
                itemId, RegistrationStatus.RECORDED, RegistrationStatus.QUALIFIED);
        ai.setLastChangeDateTime(LocalDateTime.now());
        ai.setChangeDescription("驳回申请：RECORDED → QUALIFIED" + (note == null ? "" : "（" + note + "）"));
        return administeredItemRepository.save(ai);
    }

    /**
     * 主管申请合格：置管理状态为 PROVISIONALLY_QUALIFIED，启动 RECORDED→QUALIFIED 审核流。
     *
     * <p>前置：注册状态为 RECORDED，管理状态不是 PROVISIONALLY_QUALIFIED，
     * 当前用户为 item.stewardshipRecord.contact 所绑定的账号。</p>
     */
    public AdministeredItem requestQualification(UUID itemId, UUID accountId) {
        AdministeredItem ai = loadItem(itemId);
        RegistrationStatus from = currentStatusOf(ai);
        UUID authorityId = authorityIdOf(ai);

        if (from != RegistrationStatus.RECORDED) {
            throw new BusinessException(resolveTransitionErrorCode(from),
                    "仅 RECORDED 状态的管理项可申请合格");
        }
        if (authorityId == null) {
            throw new BusinessException(ApiCodes.REGISTRATION_AUTHORITY_NOT_FOUND,
                    "未归属注册机构的管理项无法申请合格");
        }
        if (ai.getRegistrationState() != null
                && ai.getRegistrationState().getAdministrativeStatus() == AdministrativeStatus.PROVISIONALLY_QUALIFIED) {
            throw new BusinessException(ApiCodes.ALREADY_PROVISIONALLY_QUALIFIED,
                    "该管理项已处于申请合格中，请勿重复申请");
        }
        if (!itemAdministrationService.isItemStewardedByAccount(ai, accountId)) {
            throw new BusinessException(ApiCodes.NOT_ITEM_STEWARD,
                    "仅该管理项的主管可申请合格");
        }

        administrativeStatusService.setProvisionallyQualified(ai);
        ai.setLastChangeDateTime(LocalDateTime.now());
        ai.setChangeDescription("主管申请合格：RECORDED → QUALIFIED");
        return administeredItemRepository.save(ai);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * 通过人数：任一人 = 1；至少 N 人取 quorumCount。
     *
     * <p>AT_LEAST 但未配置有效 quorumCount（null 或 ≤0）属规则数据异常：按 1 兜底
     * （退化为任一人通过），并输出 warn 日志提示修复规则数据，避免静默放行。</p>
     */
    static int quorumOf(RegistrationTransitionRule rule) {
        if (rule.getQuorumType() == QuorumType.AT_LEAST) {
            if (rule.getQuorumCount() != null && rule.getQuorumCount() > 0) {
                return rule.getQuorumCount();
            }
            log.warn("迁移规则 {} → {} 通过规则为 AT_LEAST 但未配置有效 quorum_count，按 1 兜底",
                    rule.getFromStatus(), rule.getToStatus());
            return 1;
        }
        return 1;
    }

    /**
     * 判定指定 Registrar 是否属于某条规则的审核人集合（供工作空间等外部服务复用）。
     */
    public static boolean isQualifiedApprover(Optional<Registrar> activeRegistrar,
                                               UUID authorityId,
                                               RegistrationTransitionRule rule) {
        return activeRegistrar
                .filter(r -> r.getRegistrationAuthority() != null
                        && r.getRegistrationAuthority().getId().equals(authorityId))
                .filter(r -> rule.getApproverMode() == ApproverMode.ALL_REGISTRARS
                        || rule.getApproverRegistrarIds().contains(r.getId()))
                .isPresent();
    }

    private AdministeredItem loadItem(UUID itemId) {
        return administeredItemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(
                        ApiCodes.ADMINISTERED_ITEM_NOT_FOUND, "管理项不存在: " + itemId));
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

    private static int resolveTransitionErrorCode(RegistrationStatus from) {
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
}
