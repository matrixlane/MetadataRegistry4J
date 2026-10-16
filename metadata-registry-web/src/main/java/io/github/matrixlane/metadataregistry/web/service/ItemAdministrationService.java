package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.ReferenceDocument;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Item;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Text;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Definition;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.DefinitionContext;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Designation;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.DesignationContext;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.NamingConvention;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.IdentifiedItem;
import io.github.matrixlane.metadataregistry.ext.identification.IdentifierCreationMode;
import io.github.matrixlane.metadataregistry.ext.identification.IdentifierInputMode;
import io.github.matrixlane.metadataregistry.ext.identification.ScopedIdentifierLifecycle;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
import io.github.matrixlane.metadataregistry.ext.identification.Versions;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministrativeStatus;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationState;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegisteredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.StewardshipRecord;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.SubmissionRecord;
import io.github.matrixlane.metadataregistry.web.dto.common.SubmissionRecordSummary;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Contact;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Organization;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.common.ItemMetaResponse;
import io.github.matrixlane.metadataregistry.web.dto.common.ItemRegistrationRequest;
import io.github.matrixlane.metadataregistry.web.dto.common.SubmissionInfo;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLine;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLineContextResponse;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLineResponse;
import io.github.matrixlane.metadataregistry.web.repository.AdministeredItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.ContactRepository;
import io.github.matrixlane.metadataregistry.web.repository.DesignationRepository;
import io.github.matrixlane.metadataregistry.web.repository.OrganizationRepository;
import io.github.matrixlane.metadataregistry.web.repository.ScopedIdentifierRepository;
import io.github.matrixlane.metadataregistry.web.repository.StewardshipRecordRepository;
import io.github.matrixlane.metadataregistry.web.repository.SubmissionRecordRepository;
import io.github.matrixlane.metadataregistry.web.repository.ContextRepository;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamingConventionRepository;
import io.github.matrixlane.metadataregistry.web.repository.ReferenceDocumentRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrySpecificationRepository;
import io.github.matrixlane.metadataregistry.web.federation.GlobalNamespaceService;
import io.github.matrixlane.metadataregistry.web.service.namingconvention.NamingConventionEngine;
import io.github.matrixlane.metadataregistry.web.service.namingconvention.NamingConventionRuleResult;
import io.github.matrixlane.metadataregistry.web.service.identifier.IdentifierInputPolicyService;
import io.github.matrixlane.metadataregistry.web.service.namespace.NamespaceConstraintValidator;
import io.github.matrixlane.metadataregistry.web.util.ItemNamingHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ItemAdministrationService {

    /** 回退恢复期间的副作用规避标记：置位时跳过 markDraft、recordSubmission。 */
    private static final ThreadLocal<Boolean> RESTORE_IN_PROGRESS = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private final LanguageIdentificationRepository languageRepository;
    private final ReferenceDocumentRepository referenceDocumentRepository;
    private final NamespaceRepository namespaceRepository;
    private final RegistrationAuthorityRepository registrationAuthorityRepository;
    private final ContextRepository contextRepository;
    private final NamingConventionEngine namingConventionEngine;
    private final ScopedIdentifierRepository scopedIdentifierRepository;
    private final AdministeredItemRepository administeredItemRepository;
    private final DesignationRepository designationRepository;
    private final IdentifierInputPolicyService identifierInputPolicyService;
    private final NamingConventionRepository namingConventionRepository;
    private final NamespaceConstraintValidator namespaceConstraintValidator;
    private final ActiveRegistrarService activeRegistrarService;
    private final RegistrySpecificationRepository registrySpecificationRepository;
    private final ScopedIdentifierLifecycleService lifecycleService;
    private final NamespaceIdentifierSettingsService namespaceIdentifierSettingsService;
    private final SubmissionRecordRepository submissionRecordRepository;
    private final StewardshipRecordRepository stewardshipRecordRepository;
    private final ContactRepository contactRepository;
    private final OrganizationRepository organizationRepository;
    private final ContactService contactService;
    private final ContactAccountService contactAccountService;
    private final AdministrativeStatusService administrativeStatusService;

    /**
     * 创建时允许的显式初始注册状态白名单（initial-status-incomplete）。
     * 仅 INCOMPLETE（幂等默认）与文档态入口 APPLICATION/HISTORICAL；
     * 生命周期态（CANDIDATE/RECORDED/QUALIFIED/…）须自 INCOMPLETE 经推进流程获得，拒绝（1125）。
     */
    private static final Set<RegistrationStatus> ALLOWED_INITIAL_STATUSES = Set.of(
            RegistrationStatus.INCOMPLETE,
            RegistrationStatus.APPLICATION,
            RegistrationStatus.HISTORICAL);

    /** 当前是否处于回退恢复流程中。 */
    public boolean isRestoreInProgress() {
        return Boolean.TRUE.equals(RESTORE_IN_PROGRESS.get());
    }

    /**
     * 进入/退出回退恢复流程。由 {@link WorkspaceRollbackService} 在恢复写回前设置，
     * 写回后必须清理，避免污染同线程后续操作。
     */
    public void setRestoreInProgress(boolean value) {
        RESTORE_IN_PROGRESS.set(value);
    }

    public void applyDesignations(Item item, List<TextLine> designations) {
        applyDesignations(item, designations, null);
    }

    /**
     * Apply designations to an item and validate them against the given namespace's
     * naming convention constraints when the namespace enforces mandatory naming conventions.
     *
     * <p>按 id 就地 reconcile（而非清空重建）：携带 id 的 payload 行就地更新、保留行 id
     * （被配对引用的行不会因重建被 DELETE，避免触发 designation_definition_pairing
     * 外键冲突）；未携带 id 的行为新建；既有中未被 payload 保留的行删除前须无配对引用。
     */
    public void applyDesignations(Item item, List<TextLine> designations, UUID namespaceId) {
        if (designations == null) return;

        // 回退恢复期间跳过内容校验：恢复的目标是忠实还原基线快照的历史内容，
        // 即使其不满足现行规则（孤立行/命名规范/首选唯一性等校验均为后加入），也不应阻断回退。
        boolean restore = isRestoreInProgress();
        if (!restore) {
            validateIsolatedLines(designations, "指称");
            validateNamingConventions(designations);
            validatePreferredUniqueness(designations, resolveLanguages(designations), "指称");
        }

        Map<UUID, Designation> existingById = new HashMap<>();
        for (Designation d : item.getDesignations()) {
            if (d.getId() != null) {
                existingById.put(d.getId(), d);
            }
        }

        Set<UUID> keptIds = new java.util.HashSet<>();
        // 本次循环新建的行：命名空间约束校验（oneItemPerName 冲突检测等）会触发
        // auto-flush，可能让未提交的新行提前获得 id；删除 reconcile 必须按行身份
        // 排除它们，否则新建行会被误判为「payload 未携带的既有行」而遭移除
        //（表现为：带命名空间保存时指称静默丢失）。
        Set<Designation> created = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (int i = 0; i < designations.size(); i++) {
            TextLine line = designations.get(i);
            Designation d = line.id() != null ? existingById.get(line.id()) : null;
            if (d == null) {
                d = new Designation();
                d.setItem(item);
                item.getDesignations().add(d);
                created.add(d);
            } else {
                keptIds.add(d.getId());
            }
            d.setSign(Sign.of(line.value()));
            d.setLanguage(resolveLanguage(line.languageId()));
            d.setSource(loadSourceOrNull(line.sourceId()));
            d.getNamingConventions().clear();
            applyNamingConventions(d, line.namingConventionIds());
            d.getDesignationContexts().clear();
            if (line.contexts() != null) {
                for (var ce : line.contexts()) {
                    DesignationContext dc = new DesignationContext();
                    dc.setDesignation(d);
                    dc.setContext(loadContext(item, ce.contextId()));
                    dc.setAcceptability(ce.acceptability());
                    d.getDesignationContexts().add(dc);
                }
            }
            if (!restore && namespaceId != null) {
                namespaceConstraintValidator.validateDesignation(namespaceId, d, d.getId());
            }
        }

        List<Designation> toRemove = new ArrayList<>();
        for (Designation d : item.getDesignations()) {
            if (created.contains(d)) {
                continue;
            }
            if (d.getId() == null || keptIds.contains(d.getId())) {
                continue;
            }
            if (!d.getDesignationDefinitionPairings().isEmpty()) {
                throw new BusinessException(ApiCodes.CONFLICT,
                        "指称「" + (d.getSign() == null ? "" : d.getSign().getValue())
                                + "」仍被配对引用，不能删除；请先删除相关配对");
            }
            toRemove.add(d);
        }
        item.getDesignations().removeAll(toRemove);
    }

    private void applyNamingConventions(Designation d, List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        List<NamingConvention> conventions = namingConventionRepository.findAllById(ids);
        if (conventions.size() != ids.size()) {
            List<UUID> found = conventions.stream().map(NamingConvention::getId).toList();
            List<UUID> missing = ids.stream().filter(id -> !found.contains(id)).toList();
            throw new ResourceNotFoundException("NamingConvention", missing);
        }
        d.getNamingConventions().addAll(conventions);
    }

    private void validateNamingConventions(List<TextLine> designations) {
        for (TextLine line : designations) {
            Sign sign = Sign.of(line.value());
            List<NamingConventionRuleResult> results = namingConventionEngine.validate(sign);
            List<String> failures = results.stream()
                    .filter(r -> !r.isPassed())
                    .map(r -> r.getRuleName() + ": " + String.join(", ", r.getMessages()))
                    .toList();
            if (!failures.isEmpty()) {
                throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                        "命名规范校验失败 [" + line.value() + "]: " + String.join("; ", failures));
            }
        }
    }

    private void validateIsolatedLines(List<TextLine> lines, String label) {
        for (int i = 0; i < lines.size(); i++) {
            TextLine line = lines.get(i);
            boolean hasContext = line.contexts() != null && !line.contexts().isEmpty();
            boolean excepted = Boolean.TRUE.equals(line.isolatedException());
            if (!hasContext && !excepted) {
                throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                        "第 " + (i + 1) + " 条" + label + "既没有上下文也未标记为例外");
            }
        }
    }

    private void validatePreferredUniqueness(List<TextLine> lines, List<LanguageIdentification> resolvedLanguages, String label) {
        Map<String, Integer> preferredCountByLanguageContext = new HashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            TextLine line = lines.get(i);
            if (line.contexts() == null) continue;
            LanguageIdentification lang = resolvedLanguages.get(i);
            String languageIdStr = lang == null ? "null" : lang.getId().toString();
            for (var ce : line.contexts()) {
                if (ce.acceptability() == io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Acceptability.PREFERRED) {
                    String key = languageIdStr + "|" + (ce.contextId() == null ? "null" : ce.contextId());
                    preferredCountByLanguageContext.merge(key, 1, Integer::sum);
                }
            }
        }
        for (Map.Entry<String, Integer> entry : preferredCountByLanguageContext.entrySet()) {
            if (entry.getValue() > 1) {
                throw new BusinessException(ApiCodes.CONFLICT,
                        "同一语言+上下文下只能有一个首选" + label + " (PREFERRED): " + entry.getKey());
            }
        }
    }

    /**
     * Apply definitions to an item. 与 {@link #applyDesignations(Item, List, UUID)}
     * 同构：按 id 就地 reconcile，被配对引用的行不得删除。
     */
    public void applyDefinitions(Item item, List<TextLine> definitions) {
        if (definitions == null) return;

        boolean restore = isRestoreInProgress();
        if (!restore) {
            validateIsolatedLines(definitions, "定义");
            validatePreferredUniqueness(definitions, resolveLanguages(definitions), "定义");
        }

        Map<UUID, Definition> existingById = new HashMap<>();
        for (Definition d : item.getDefinitions()) {
            if (d.getId() != null) {
                existingById.put(d.getId(), d);
            }
        }

        Set<UUID> keptIds = new java.util.HashSet<>();
        // 与 applyDesignations 同理：中途查询触发的 auto-flush 可能让新建行提前获得
        // id，删除 reconcile 按行身份排除本次新建行。
        Set<Definition> created = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (int i = 0; i < definitions.size(); i++) {
            TextLine line = definitions.get(i);
            Definition d = line.id() != null ? existingById.get(line.id()) : null;
            if (d == null) {
                d = new Definition();
                d.setItem(item);
                item.getDefinitions().add(d);
                created.add(d);
            } else {
                keptIds.add(d.getId());
            }
            d.setText(Text.of(line.value()));
            d.setLanguage(resolveLanguage(line.languageId()));
            d.setSource(loadSourceOrNull(line.sourceId()));
            d.getDefinitionContexts().clear();
            if (line.contexts() != null) {
                for (var ce : line.contexts()) {
                    DefinitionContext dc = new DefinitionContext();
                    dc.setDefinition(d);
                    dc.setContext(loadContext(item, ce.contextId()));
                    dc.setAcceptability(ce.acceptability());
                    d.getDefinitionContexts().add(dc);
                }
            }
        }

        List<Definition> toRemove = new ArrayList<>();
        for (Definition d : item.getDefinitions()) {
            if (created.contains(d)) {
                continue;
            }
            if (d.getId() == null || keptIds.contains(d.getId())) {
                continue;
            }
            if (!d.getDesignationDefinitionPairings().isEmpty()) {
                throw new BusinessException(ApiCodes.CONFLICT,
                        "定义「" + (d.getText() == null ? "" : d.getText().getValue())
                                + "」仍被配对引用，不能删除；请先删除相关配对");
            }
            toRemove.add(d);
        }
        item.getDefinitions().removeAll(toRemove);
    }

    private String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return "system";
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof io.github.matrixlane.metadataregistry.web.auth.AuthenticatedUser user) {
            return user.username();
        }
        return auth.getName();
    }

    public AdministeredItem ensureAdministeredItem(Item item, ItemRegistrationRequest req) {
        return ensureAdministeredItem(item, req, null);
    }

    /**
     * @param explicitAuthority 系统/联邦路径显式指定归属注册机构（如合并时 RAI
     *                          匹配的目的方机构、种子数据）；为 null 时按活动
     *                          Registrar 身份推导。API 请求体不再接受归属字段。
     */
    public AdministeredItem ensureAdministeredItem(Item item, ItemRegistrationRequest req,
                                                   RegistrationAuthority explicitAuthority) {
        if (req == null) {
            return null;
        }
        AdministeredItem existing = (item.getIdentifiedItem() instanceof AdministeredItem ai0) ? ai0 : null;
        AdministeredItem ai = existing != null ? existing : new AdministeredItem(item);

        Namespace ns = req.namespaceId() == null ? null
                : namespaceRepository.findById(req.namespaceId())
                .orElseThrow(() -> new ResourceNotFoundException("Namespace", req.namespaceId()));

        // 默认命名空间推导（default-namespace-by-active-registrar）：
        // 调用方未携带 namespaceId 时按活动 Registrar 所属 RA 的自有命名空间推导。
        // 联邦/合并（explicitAuthority != null）路径继续以外部权威字段为准。
        if (ns == null && explicitAuthority == null) {
            ns = resolveDefaultNamespace().orElse(null);
        }

        enforceRaNamespacePrecondition(ns);

        if (ai.getScopedIdentifiers() == null || ai.getScopedIdentifiers().isEmpty()) {
            ScopedIdentifier si = new ScopedIdentifier();
            String identifier;
            try {
                identifier = identifierInputPolicyService.resolveIdentifier(ns, req.identifier(),
                        org.hibernate.Hibernate.getClass(item).getSimpleName(), req.variables());
            } catch (io.github.matrixlane.metadataregistry.ext.identification.segment.SegmentTemplateException e) {
                throw new BusinessException("标识生成失败：" + e.getMessage());
            }
            si.setIdentifier(identifier);
            si.setVersion(identifierInputPolicyService.resolveVersion(ns, req.version()));
            // 持久化创建时用户选择的 AUTO/MANUAL：请求未携带 mode 时按 identifier / version
            // 是否有显式值反推（空 → AUTO，非空 → MANUAL）。mode 创建即固化，后续编辑不变更。
            // 创建方式与生命周期属性写入卫星实体（级联 SI 提交前才有 ID，经 stageNew 延迟落库）。
            ScopedIdentifierLifecycle lifecycle = new ScopedIdentifierLifecycle();
            lifecycle.setIdentifierCreationMode(req.identifierMode() != null
                    ? req.identifierMode()
                    : ItemRegistrationRequest.resolveMode(req.identifier()));
            lifecycle.setVersionCreationMode(req.versionMode() != null
                    ? req.versionMode()
                    : ItemRegistrationRequest.resolveMode(req.version()));
            lifecycle.setCreatedAt(java.time.LocalDateTime.now());
            lifecycleService.stageNew(si, lifecycle);
            si.setNamespace(ns);
            si.setIdentifiedItem(ai);
            ai.getScopedIdentifiers().add(si);
        } else {
            ScopedIdentifier si = selectEditableIdentifier(ai, ns);

            // 标识符：AUTO 时 MUST 拒绝任何手动修改；MANUAL / NULL 时沿用单 SI 可改逻辑
            String requestedIdentifier = req.identifier();
            if (requestedIdentifier != null && !requestedIdentifier.isBlank()
                    && !Objects.equals(requestedIdentifier, si.getIdentifier())) {
                if (lifecycleService.getOrDefault(si.getId()).getIdentifierCreationMode() == IdentifierCreationMode.AUTO) {
                    throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                            "该标识符创建时选择自动生成，不允许手动修改");
                }
                if (ai.getScopedIdentifiers().size() == 1) {
                    si.setIdentifier(requestedIdentifier);
                }
            }

            // 版本：AUTO 时 MUST 忽略请求体显式值并按命名空间版本策略递增；MANUAL / NULL 时
            // 显式携带不同版本 → 按策略校验并采用；其余（回显同值/留空）时 MANUAL 一律保持
            // 原版本（纯手动创建方式系统不做自动生成），mode 缺失（NULL，迁移前既存/裸导入
            // 记录）按命名空间版本输入模式回退：MANUAL_ONLY 命名空间保持原版本，其余维持
            // registry-merge-strategy D4/D5 的本地编辑递增语义。
            // 联邦合并路径不经由此处，版本由合并执行器显式维护。
            String requestedVersion = req.version();
            IdentifierCreationMode versionCreationMode =
                    lifecycleService.getOrDefault(si.getId()).getVersionCreationMode();
            var nsSettings = ns != null ? namespaceIdentifierSettingsService.getOrDefault(ns.getId()) : null;
            boolean legacyAutoBump = versionCreationMode == null
                    && (nsSettings == null || !IdentifierInputMode.MANUAL_ONLY.name()
                            .equals(String.valueOf(nsSettings.getVersionInputMode()).trim().toUpperCase()));
            if (versionCreationMode == IdentifierCreationMode.AUTO) {
                si.setVersion(Versions.bump(si.getVersion(),
                        nsSettings != null ? nsSettings.getVersioningPolicy() : null));
            } else if (requestedVersion != null && !requestedVersion.isBlank()
                    && !requestedVersion.equals(si.getVersion())) {
                identifierInputPolicyService.validateManualVersion(ns, requestedVersion);
                si.setVersion(requestedVersion);
            } else if (legacyAutoBump) {
                si.setVersion(Versions.bump(si.getVersion(),
                        nsSettings != null ? nsSettings.getVersioningPolicy() : null));
            }
            // else：MANUAL（或 MANUAL_ONLY 命名空间下的存量记录）未显式变更版本 → 保持原版本
            if (ai.getScopedIdentifiers().size() == 1 && ns != null) {
                // 单标识条目维持既有行为；多标识（联邦迁移双标识）条目不重挂命名空间，
                // 避免改写来源命名空间下的标识（合并基）
                si.setNamespace(ns);
            }
        }

        // 所有权（active-registrar-ownership）：
        // - 新建：归属由活动 Registrar 身份推导（或系统/联邦路径显式指定），
        //   所注册条目一定关联活动身份所在的注册机构；
        // - 既有：归属冻结，绝不改写；且当前用户必须以归属机构的 Registrar
        //   身份为活动身份方可修改。
        if (existing == null) {
            RegistrationAuthority owner = explicitAuthority != null
                    ? explicitAuthority
                    : resolveOwnershipForCreate(ns);
            if (owner != null) {
                ai.setRegistrationAuthority(owner);
            }
        } else if (explicitAuthority == null) {
            activeRegistrarService.assertCanModify(ai);
        }

        boolean stateCreatedNow = ai.getRegistrationState() == null;
        if (stateCreatedNow) {
            RegistrationState state = new RegistrationState();
            // Incomplete 为注册生命周期入口态；
            // Recorded 需全部强制属性完成并经确认（4.3.3.1.4），只能经推进流程获得。
            state.setRegistrationStatus(RegistrationStatus.INCOMPLETE);
            state.setEffectiveDate(LocalDateTime.now());
            ai.setRegistrationState(state);
        }
        // Registration_State 其余属性（ISO/IEC 11179-3:2023 表 22）：NULL = 不变更；
        // 文本属性空串 = 清空。既有条目尚无状态时先落默认（INCOMPLETE/当前时间）。
        // 注册状态（registration_status）不再经编辑表单变更
        // （registration-process-designer D6）：既有条目携带与当前不同的状态一律拒绝，
        // 状态只能经推进流程（POST /administered-items/{id}/transitions/execute|approve）完成。
        // 显式初始状态标定仅限真正新建（existing == null）且仅允许 INCOMPLETE（幂等默认）与
        // 文档态入口 APPLICATION/HISTORICAL（initial-status-incomplete）：生命周期态
        // （CANDIDATE/RECORDED/QUALIFIED/…）一律拒绝（1125），须自 INCOMPLETE 经推进流程
        // 获得。既有但无 RegistrationState 的遗留条目不是新建，携带不同状态同样拒绝（M2 表单后门）。
        RegistrationState state = ai.getRegistrationState();
        if (req.registrationStatus() != null) {
            if (existing == null) {
                if (!ALLOWED_INITIAL_STATUSES.contains(req.registrationStatus())) {
                    throw new BusinessException(ApiCodes.INVALID_INITIAL_STATUS,
                            ("非法的初始注册状态 %s：新建条目自 INCOMPLETE（不完整）开始，"
                                    + "APPLICATION/HISTORICAL 之外的初始状态须经推进流程获得")
                                    .formatted(req.registrationStatus()));
                }
                state.setRegistrationStatus(req.registrationStatus());
            } else if (req.registrationStatus() != state.getRegistrationStatus()) {
                throw new BusinessException(ApiCodes.STATUS_CHANGE_VIA_FORM_FORBIDDEN,
                        "注册状态不可经编辑表单变更（%s → %s），请在注册项列表的推进流程中执行状态迁移"
                                .formatted(state.getRegistrationStatus(), req.registrationStatus()));
            }
        }
        // 管理状态由工作空间流程系统治理，请求体字段保留但不再生效
        if (req.effectiveDate() != null) {
            state.setEffectiveDate(req.effectiveDate());
        }
        if (req.untilDate() != null) {
            state.setUntilDate(req.untilDate());
        }
        if (req.administrativeNote() != null) {
            state.setAdministrativeNote(req.administrativeNote());
        }
        if (req.unresolvedIssue() != null) {
            state.setUnresolvedIssue(req.unresolvedIssue());
        }
        // 解释性注释：NULL = 不变更，空串 = 清空（与 Registration_State 文本属性语义一致）
        if (req.explanatoryComment() != null) {
            ai.setExplanatoryComment(req.explanatoryComment());
        }
        applyStewardshipRecord(ai, req);
        if (ai.getCreationDateTime() == null) {
            ai.setCreationDateTime(LocalDateTime.now());
        }
        ai.setLastChangeDateTime(LocalDateTime.now());

        // 新建与编辑保存后统一置为 Draft（不改动注册状态，不新建状态行）。
        // 回退恢复流程中跳过，避免恢复旧内容后又重新进入草稿。
        if (!isRestoreInProgress()) {
            administrativeStatusService.markDraft(ai);
        }

        item.setIdentifiedItem(ai);
        return ai;
    }

    /**
     * 提交记录（Submission_Record，add-submitter-contact-identity / align-submission-record-to-iso）：
     * 工作空间提交（单条 / request-candidate）调用本方法创建一条 SubmissionRecord 并关联单个
     * AdministeredItem。
     *
     * <p>编辑保存路径不再调用本方法（参见 add-submitter-contact-identity 旧版历史与
     * align-submission-record-to-iso 重构）。批量提交应使用 {@link #recordSubmissionForBatch}。</p>
     *
     * <p>联系人与所属组织取 {@code submission} 块；未显式提供时按提交者默认联系人及其所属组织预填。
     * 显式给出的联系人 MUST 属于当前账号绑定的 Contact。无 AdministeredItem 或无认证（系统路径）时跳过。</p>
     */
    public void recordSubmission(Item item, ItemRegistrationRequest req) {
        if (req == null || item == null || item.getId() == null || isRestoreInProgress()) {
            return;
        }
        UUID accountId = activeRegistrarService.currentAccountIdOrNull();
        if (accountId == null) {
            return;
        }
        AdministeredItem administeredItem = administeredItemRepository.findByItem_Id(item.getId())
                .orElse(null);
        if (administeredItem == null) {
            return;
        }
        ContactOrganizationPair pair = resolveContactAndOrganization(req.submission(), accountId);
        if (pair == null) {
            return;
        }
        persistSubmissionRecord(List.of(administeredItem), pair.contact(), pair.organization());
    }

    /**
     * 批量提交记录（align-submission-record-to-iso）：工作空间批量提交一次调用本方法，
     * 创建一条 SubmissionRecord 关联所有传入 Item；按 ISO/IEC 11179-3:2023 表 27"一次提交动作"语义。
     *
     * @param items 至少一条 Item；空集合抛错
     */
    public void recordSubmissionForBatch(List<Item> items, SubmissionInfo submission) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "批量提交至少包含一条管理项");
        }
        if (isRestoreInProgress()) {
            return;
        }
        UUID accountId = activeRegistrarService.currentAccountIdOrNull();
        if (accountId == null) {
            return;
        }
        List<AdministeredItem> administeredItems = new ArrayList<>(items.size());
        for (Item item : items) {
            if (item == null || item.getId() == null) {
                continue;
            }
            administeredItemRepository.findByItem_Id(item.getId()).ifPresent(administeredItems::add);
        }
        if (administeredItems.isEmpty()) {
            return;
        }
        ContactOrganizationPair pair = resolveContactAndOrganization(submission, accountId);
        if (pair == null) {
            return;
        }
        persistSubmissionRecord(administeredItems, pair.contact(), pair.organization());
    }

    private record ContactOrganizationPair(Contact contact, Organization organization) {
    }

    /**
     * 提交联系人与所属组织解析（acg-submission）：与
     * {@link WorkspaceService#validateSubmissionInfo} 保持同一套规则——
     * organizationId 显式给出时，先存在性校验，再从当前账号绑定的身份中解析
     * contact.organization.id == organizationId 的身份作为提交联系人；
     * contactId 同时给出时保留"联系人必须属于当前账号"校验并追加从属校验
     * （联系人须属于所选组织）。仅给 contactId 时保持历史行为。
     * {@link ContactService#resolveDefaultSubmissionContext} 不变。
     */
    private ContactOrganizationPair resolveContactAndOrganization(SubmissionInfo submission, UUID accountId) {
        Contact contact = null;
        Organization organization = null;
        if (submission != null && submission.organizationId() != null) {
            organization = organizationRepository.findById(submission.organizationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Organization", submission.organizationId()));
            contact = resolveOwnIdentityInOrganization(accountId, submission.organizationId());
            if (submission.contactId() != null) {
                Contact explicit = contactRepository.findById(submission.contactId())
                        .orElseThrow(() -> new ResourceNotFoundException("Contact", submission.contactId()));
                java.util.Optional<UUID> boundAccount =
                        contactAccountService.accountIdOfContact(explicit.getId());
                if (boundAccount.isEmpty() || !accountId.equals(boundAccount.get())) {
                    throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                            "提交信息中的联系人必须是当前账号绑定的联系人身份");
                }
                if (explicit.getOrganization() == null
                        || !submission.organizationId().equals(explicit.getOrganization().getId())) {
                    throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                            "提交信息中的联系人必须属于所选提交组织");
                }
                contact = explicit;
            }
        } else if (submission != null && submission.contactId() != null) {
            contact = contactRepository.findById(submission.contactId())
                    .orElseThrow(() -> new ResourceNotFoundException("Contact", submission.contactId()));
            java.util.Optional<UUID> boundAccount = contactAccountService.accountIdOfContact(contact.getId());
            if (boundAccount.isEmpty() || !accountId.equals(boundAccount.get())) {
                throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                        "提交信息中的联系人必须是当前账号绑定的联系人身份");
            }
            organization = contact.getOrganization();
        } else {
            java.util.Optional<ContactService.SubmissionContext> context =
                    contactService.resolveDefaultSubmissionContext(accountId);
            if (context.isPresent()) {
                contact = context.get().contact();
                organization = context.get().organization();
            }
        }
        if (contact == null || organization == null) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "无法确定提交联系人与所属组织：请先在「我的」中登记联系人身份，或在提交信息中显式选择");
        }
        return new ContactOrganizationPair(contact, organization);
    }

    /**
     * 当前账号绑定的身份中，organization.id == organizationId 的联系人身份；无则 422。
     * 确定性排序（minor-1）：默认注册人员优先，其余按绑定顺序（contactId 升序，即最早绑定在前）——
     * findAllById 的返回顺序未定义，同一组织多身份时不得直接 findFirst。
     */
    private Contact resolveOwnIdentityInOrganization(UUID accountId, UUID organizationId) {
        List<UUID> contactIds = contactAccountService.boundContactIdsOfAccount(accountId);
        if (contactIds.isEmpty()) {
            throw noIdentityInOrganization();
        }
        java.util.Optional<UUID> defaultRegistrarId = contactAccountService.defaultRegistrarIdOf(accountId);
        Map<UUID, Contact> byId = new HashMap<>();
        contactRepository.findAllById(contactIds).forEach(c -> byId.put(c.getId(), c));
        return contactIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .filter(c -> c.getOrganization() != null && organizationId.equals(c.getOrganization().getId()))
                .sorted(java.util.Comparator.comparing(
                        c -> defaultRegistrarId.filter(id -> id.equals(c.getId())).isEmpty()))
                .findFirst()
                .orElseThrow(this::noIdentityInOrganization);
    }

    private BusinessException noIdentityInOrganization() {
        return new BusinessException(ApiCodes.VALIDATION_FAILED,
                "提交组织必须是当前账号具有联系人身份的组织，请先创建为该机构联系人");
    }

    private void persistSubmissionRecord(List<AdministeredItem> administeredItems,
                                        Contact contact, Organization organization) {
        SubmissionRecord record = new SubmissionRecord();
        record.setContact(contact);
        record.setOrganization(organization);
        // SubmissionRecord.registeredItems 是 @ManyToMany 关联到 RegisteredItem；
        // AdministeredItem 继承 RegisteredItem，可直接放入。
        List<RegisteredItem> itemsAsRegistered = new ArrayList<>(administeredItems.size());
        for (AdministeredItem ai : administeredItems) {
            itemsAsRegistered.add(ai);
        }
        record.setRegisteredItems(itemsAsRegistered);
        submissionRecordRepository.save(record);
        for (AdministeredItem ai : administeredItems) {
            if (ai.getSubmissionRecords() == null) {
                ai.setSubmissionRecords(new ArrayList<>());
            }
            ai.getSubmissionRecords().add(record);
        }
    }

    /**
     * 主管记录（Stewardship_Record）：
     * 请求显式携带主管联系人与所属组织时，复用或新建 StewardshipRecord 并关联到
     * AdministeredItem。NULL = 不变更（不清除）。显式给出的联系人 MUST 属于当前账号
     * 绑定的 Contact；organizationId 缺省时取联系人所属组织。
     */
    private void applyStewardshipRecord(AdministeredItem ai, ItemRegistrationRequest req) {
        if (req == null || req.stewardship() == null || req.stewardship().contactId() == null) {
            return;
        }
        UUID accountId = activeRegistrarService.currentAccountIdOrNull();
        if (accountId == null) {
            // 系统/种子路径无当前账号，不修改主管记录
            return;
        }
        Contact contact = contactRepository.findById(req.stewardship().contactId())
                .orElseThrow(() -> new ResourceNotFoundException("Contact", req.stewardship().contactId()));
        java.util.Optional<UUID> boundAccount = contactAccountService.accountIdOfContact(contact.getId());
        if (boundAccount.isEmpty() || !accountId.equals(boundAccount.get())) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "主管信息中的联系人必须是当前账号绑定的联系人身份");
        }
        Organization organization;
        if (req.stewardship().organizationId() != null) {
            organization = organizationRepository.findById(req.stewardship().organizationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Organization", req.stewardship().organizationId()));
        } else {
            organization = contact.getOrganization();
        }
        if (organization == null) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "无法确定主管记录所属组织：请为联系人选择或登记所属组织");
        }
        StewardshipRecord record = stewardshipRecordRepository
                .findFirstByContact_IdAndOrganization_Id(contact.getId(), organization.getId())
                .orElseGet(() -> {
                    StewardshipRecord sr = new StewardshipRecord();
                    sr.setContact(contact);
                    sr.setOrganization(organization);
                    return stewardshipRecordRepository.save(sr);
                });
        ai.setStewardshipRecord(record);
    }

    /**
     * 判定当前账号是否归属该管理项。
     *
     * <p>归属规则：当前账号绑定联系人 ∈ {item 主管记录联系人（stewardshipRecord.contact），
     * item 最近提交记录联系人}。供 Submitter 白名单迁移（INCOMPLETE→CANDIDATE）
     * 与 T5 request-candidate 端点复用。</p>
     */
    public boolean isItemOwnedByAccount(AdministeredItem item, UUID accountId) {
        if (item == null || accountId == null) {
            return false;
        }
        Set<UUID> accountContactIds = new java.util.HashSet<>(contactAccountService.boundContactIdsOfAccount(accountId));
        if (accountContactIds.isEmpty()) {
            return false;
        }
        if (item.getStewardshipRecord() != null && item.getStewardshipRecord().getContact() != null
                && accountContactIds.contains(item.getStewardshipRecord().getContact().getId())) {
            return true;
        }
        return submissionRecordRepository.findLatestByRegisteredItemId(item.getId())
                .map(SubmissionRecord::getContact)
                .filter(contact -> accountContactIds.contains(contact.getId()))
                .isPresent();
    }

    /**
     * 判定当前账号是否为该管理项的主管（stewardshipRecord.contact）。
     *
     * <p>供申请合格（request-qualification）端点校验。</p>
     */
    public boolean isItemStewardedByAccount(AdministeredItem item, UUID accountId) {
        if (item == null || accountId == null || item.getStewardshipRecord() == null) {
            return false;
        }
        Contact steward = item.getStewardshipRecord().getContact();
        if (steward == null || steward.getId() == null) {
            return false;
        }
        return contactAccountService.boundContactIdsOfAccount(accountId).stream()
                .anyMatch(steward.getId()::equals);
    }

    /**
     * 更新路径定位可编辑的标识：优先请求命名空间下的标识；多标识条目其次取
     * 注册机构自有命名空间下的标识，避免触碰来源命名空间标识（合并基）。
     */
    private ScopedIdentifier selectEditableIdentifier(AdministeredItem ai, Namespace ns) {
        if (ns != null && ns.getId() != null) {
            var match = ai.getScopedIdentifiers().stream()
                    .filter(s -> s.getNamespace() != null && ns.getId().equals(s.getNamespace().getId()))
                    .findFirst();
            if (match.isPresent()) {
                return match.get();
            }
        }
        if (ai.getScopedIdentifiers().size() > 1) {
            var raOwn = ai.getScopedIdentifiers().stream()
                    .filter(s -> s.getNamespace() != null && s.getNamespace().getRegistrationAuthority() != null)
                    .findFirst();
            if (raOwn.isPresent()) {
                return raOwn.get();
            }
        }
        return ai.getScopedIdentifiers().get(0);
    }

    /**
     * 新建条目的归属推导：当前账号持有活动 Registrar 身份时归属该身份所在机构
     * （注册机构自有命名空间所属机构必须与活动身份所在机构一致，防止跨机构写入）；
     * 无活动 Registrar 但已登记为 Contact 的 Submitter 归属系统唯一注册机构
     * （add-submitter-contact-identity，系统唯一机构隐式推导——恰好一个机构时按该机构归属）；两者皆无则拒绝。
     * 无认证（种子/系统路径）时返回 null，维持既有行为。
     */
    private RegistrationAuthority resolveOwnershipForCreate(Namespace ns) {
        if (activeRegistrarService.currentAccountIdOrNull() == null) {
            return null;
        }
        var active = activeRegistrarService.resolveForCurrentUser();
        if (active.isPresent()) {
            RegistrationAuthority activeRa = active.get().getRegistrationAuthority();
            if (ns != null && ns.getRegistrationAuthority() != null
                    && !ns.getRegistrationAuthority().getId().equals(activeRa.getId())) {
                throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                        "目标命名空间属于其他注册机构，请先将活动 registrar身份切换为该机构的身份");
            }
            return activeRa;
        }
        // Submitter 通道：归属系统唯一注册机构
        UUID accountId = activeRegistrarService.currentAccountIdOrNull();
        if (activeRegistrarService.isSubmitter(accountId)) {
            RegistrationAuthority unique = activeRegistrarService.systemUniqueRegistrationAuthority()
                    .orElseThrow(() -> new BusinessException(ApiCodes.VALIDATION_FAILED,
                            "当前系统不存在唯一的注册机构，Submitter 无法确定条目归属"));
            if (ns != null && ns.getRegistrationAuthority() != null
                    && !ns.getRegistrationAuthority().getId().equals(unique.getId())) {
                throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                        "目标命名空间属于其他注册机构，无法在此命名空间下创建条目");
            }
            return unique;
        }
        throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                "当前账号没有 Registrar 身份，无法注册 Administered_Item；请先被指派为 Registrar");
    }

    /**
     * 自有数据标准的命名空间前置条件：系统中必须存在至少一个与注册机构关联的
     * 命名空间，否则阻止创建自有数据标准并引导先补建命名空间。
     *
     * <p>外部命名空间（无关联注册机构）用于导入/编目外部组织的数据标准，
     * 不受此限制。
     */
    private void enforceRaNamespacePrecondition(Namespace ns) {
        boolean externalNamespace = ns != null && ns.getRegistrationAuthority() == null;
        if (externalNamespace) {
            return;
        }
        if (!namespaceRepository.existsByRegistrationAuthorityIsNotNull()) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "创建自有数据标准前，请先创建基于注册机构的命名空间");
        }
    }

    /**
     * 默认命名空间推导（default-namespace-by-active-registrar）：
     * 按当前活动 Registrar 所属 Registration_Authority 的自有命名空间返回；
     * 无活动身份或对应 RA 尚未建立自有命名空间时返回空，交由
     * {@link #enforceRaNamespacePrecondition(Namespace)} 在前置校验中拒绝。
     */
    private java.util.Optional<Namespace> resolveDefaultNamespace() {
        if (activeRegistrarService.currentAccountIdOrNull() == null) {
            return java.util.Optional.empty();
        }
        return activeRegistrarService.resolveForCurrentUser()
                .flatMap(active -> active.getRegistrationAuthority() == null
                        ? java.util.Optional.empty()
                        : namespaceRepository.findFirstByRegistrationAuthority_Id(
                                active.getRegistrationAuthority().getId()));
    }

    public ItemMetaResponse buildItemMeta(Item item) {
        return buildItemMeta(item, null, true);
    }

    /** 用整页预取组装单条元数据；{@code preload} 为 null/空时退化为逐条查询。 */
    public ItemMetaResponse buildItemMeta(Item item, ItemMetaPreload preload) {
        return buildItemMeta(item, preload, true);
    }

    /**
     * 删除前置校验：仅注册状态为 Recorded 之前（Incomplete/Candidate，或尚无受管链）
     * 的条目允许直接删除；Recorded 及之后（含 Retired/Superseded 等）已纳入版本控制，
     * 只能经注册状态推进流程转为 Retired，不得直接删除。
     */
    public void assertDeletable(Item item) {
        if (!(item.getIdentifiedItem() instanceof AdministeredItem administeredItem)) {
            return;
        }
        RegistrationState state = administeredItem.getRegistrationState();
        if (state != null && state.getRegistrationStatus() != null
                && state.getRegistrationStatus().ordinal() >= RegistrationStatus.RECORDED.ordinal()) {
            throw new BusinessException(ApiCodes.CONFLICT,
                    "条目当前注册状态为「" + state.getRegistrationStatus().name() + "」，"
                            + "Recorded 及之后的状态已纳入版本控制，不能直接删除；"
                            + "请通过注册状态推进流程将条目转为 Retired");
        }
    }

    /**
     * 删除条目前解除其受管链上的提交记录入边：registered_item_submission_record
     * 连接表由 SubmissionRecord 侧拥有（RegisteredItem.submissionRecords 为 inverse
     * 无级联），不先移除连接行，删除 registered_item 即被外键阻塞（409 数据冲突）。
     * 关联的 RegisteredItem 全部移除后 SubmissionRecord 本身一并删除（提交履历随
     * 条目消亡，不保留空壳）；尚有其它条目关联的 SubmissionRecord 保留。
     */
    public void removeFromSubmissionRecords(Item item) {
        RegisteredItem registeredItem = item.getIdentifiedItem();
        if (registeredItem == null) {
            return;
        }
        for (SubmissionRecord record : new ArrayList<>(registeredItem.getSubmissionRecords())) {
            record.getRegisteredItems().remove(registeredItem);
            if (record.getRegisteredItems().isEmpty()) {
                submissionRecordRepository.delete(record);
            }
        }
    }

    /**
     * 条目注册元数据的整页预取结果（列表页组装用，避免逐条查询）。
     *
     * @param metaByItemId      itemId → {@link AdministeredItemRepository#findMetaByItemIds} 的投影行
     * @param identifiersByItem itemId → 该条目的标识符标量行列表
     *                          （{@link ScopedIdentifierRepository#findScalarByItemIds}）
     * @param lifecycles        scopedIdentifierId → 生命周期卫星行
     */
    public record ItemMetaPreload(
            Map<UUID, Object[]> metaByItemId,
            Map<UUID, List<Object[]>> identifiersByItem,
            Map<UUID, ScopedIdentifierLifecycle> lifecycles,
            Map<UUID, Namespace> namespacesById,
            Map<UUID, List<UUID>> referenceDocumentIdsByItem) {

        static ItemMetaPreload empty() {
            return new ItemMetaPreload(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
    }

    /**
     * 整页预取（列表页用）。
     *
     * <p>一次批量查询把本页所需的受管元数据与标识符全部取回，再由
     * {@link #buildItemMeta(Item, ItemMetaPreload)} 逐条组装。沿用项目既有批量范式
     * （见 {@code LanguageExtensionService#mapByLanguageIds}「列表页组装用，避免 N+1」）。</p>
     */
    @Transactional(readOnly = true)
    public ItemMetaPreload preloadItemMeta(Collection<? extends Item> items) {
        if (items == null || items.isEmpty()) {
            return ItemMetaPreload.empty();
        }
        List<UUID> itemIds = items.stream()
                .filter(Objects::nonNull).map(Item::getId)
                .filter(Objects::nonNull).distinct().toList();
        if (itemIds.isEmpty()) {
            return ItemMetaPreload.empty();
        }
        Map<UUID, Object[]> metaByItemId = new HashMap<>();
        for (Object[] row : administeredItemRepository.findMetaByItemIds(itemIds)) {
            metaByItemId.put((UUID) row[0], row);
        }
        Map<UUID, List<Object[]>> identifiersByItem = new HashMap<>();
        for (Object[] row : scopedIdentifierRepository.findScalarByItemIds(itemIds)) {
            identifiersByItem.computeIfAbsent((UUID) row[0], k -> new ArrayList<>()).add(row);
        }
        Map<UUID, ScopedIdentifierLifecycle> lifecycles = lifecycleService.mapByScopedIdentifierIds(
                identifiersByItem.values().stream().flatMap(List::stream)
                        .map(r -> (UUID) r[1]).filter(Objects::nonNull).distinct().toList());
        // 主标识偏好（preferPrimaryIdentifier）依赖命名空间的归属属性，批量版须一并取回
        List<UUID> nsIds = identifiersByItem.values().stream().flatMap(List::stream)
                .map(r -> (UUID) r[4]).filter(Objects::nonNull).distinct().toList();
        Map<UUID, Namespace> namespacesById = nsIds.isEmpty() ? Map.of()
                : namespaceRepository.findAllById(nsIds).stream()
                        .collect(Collectors.toMap(Namespace::getId, Function.identity()));
        Map<UUID, List<UUID>> referenceDocIdsByItem = new HashMap<>();
        for (Object[] row : administeredItemRepository.findReferenceDocumentIdsByItemIds(itemIds)) {
            referenceDocIdsByItem.computeIfAbsent((UUID) row[0], k -> new ArrayList<>())
                    .add((UUID) row[1]);
        }
        return new ItemMetaPreload(metaByItemId, identifiersByItem, lifecycles, namespacesById,
                referenceDocIdsByItem);
    }

    /**
     * 批量预取一批条目的首选指称（itemId → signValue），列表页组装关联条目标签用。
     *
     * <p>纯标量查询（designation 表 + language 排序），不触碰 Item 继承代理——
     * 避免 {@code ItemNamingHelper.preferredDesignation(dec)} 之类的实体路径在
     * 列表页初始化 DEC/VD 代理级联触发 identifiedItem 加载（JOINED 继承 +
     * mappedBy 方 LAZY OneToOne 无字节码增强即 EAGER，单次重 join 实测 ~37ms）。</p>
     *
     * @return itemId → 首选指称（按语言优先级取每组首行）
     */
    @Transactional(readOnly = true)
    public Map<UUID, String> preloadPreferredDesignations(Collection<UUID> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> result = new HashMap<>();
        for (Object[] row : designationRepository.findSignValuesByItemIdsOrdered(itemIds)) {
            // 每组首行即语言优先级最高的指称，后续行忽略
            result.putIfAbsent((UUID) row[0], (String) row[1]);
        }
        return result;
    }

    /**
     * 用整页预取组装单条元数据；{@code preload} 为 null/空时退化为逐条查询。
     *
     * @param includeSubmissionRecords 是否组装提交记录摘要。列表页表格不展示该数据，
     *                                 传 {@code false} 可跳过逐条查询（每条目 2 次：
     *                                 findAllByRegisteredItemId + countRegisteredItems），
     *                                 20 行/页即 40 次 SQL 往返。
     */
    public ItemMetaResponse buildItemMeta(Item item, ItemMetaPreload preload, boolean includeSubmissionRecords) {
        if (item == null) {
            return null;
        }
        boolean batched = preload != null && !preload.metaByItemId().isEmpty();

        UUID administeredItemId = null;
        RegistrationStatus registrationStatus = null;
        UUID registrationAuthorityId = null;
        String registrationAuthorityName = null;
        UUID nsId = null;
        String nsLabel = null;
        String identifier = null;
        String version = null;
        String scopedIdentifierStr = null;
        IdentifierCreationMode identifierCreationMode = null;
        IdentifierCreationMode versionCreationMode = null;
        LocalDateTime stateEffectiveDate = null;
        LocalDateTime stateUntilDate = null;
        String administrativeStatus = null;
        String administrativeNote = null;
        String unresolvedIssue = null;
        UUID stewardshipRecordId = null;
        UUID stewardshipContactId = null;
        UUID stewardshipOrganizationId = null;
        List<UUID> referenceDocumentIds = List.of();

        // 受管元数据走标量投影（单条版 12 列 / 批量版首列为 itemId），不加载 AdministeredItem 实体。
        // 本方法在列表响应中逐条调用，若加载实体，其 EAGER 关联会把 Item 子类继承树一并拉进
        // 单条 SQL 的 join——实测单次 44ms、731 逻辑读（2026-10-09，见 design D1'）。
        Object[] metaRow = batched
                ? preload.metaByItemId().get(item.getId())
                : administeredItemRepository.findMetaByItemId(item.getId()).stream().findFirst().orElse(null);
        if (metaRow != null) {
            int o = batched ? 1 : 0;
            administeredItemId = (UUID) metaRow[o];
            registrationStatus = (RegistrationStatus) metaRow[o + 1];
            registrationAuthorityId = (UUID) metaRow[o + 2];
            registrationAuthorityName = (String) metaRow[o + 3];
            stateEffectiveDate = (LocalDateTime) metaRow[o + 4];
            stateUntilDate = (LocalDateTime) metaRow[o + 5];
            administrativeStatus = metaRow[o + 6] == null ? null
                    : ((AdministrativeStatus) metaRow[o + 6]).standardValue();
            administrativeNote = (String) metaRow[o + 7];
            unresolvedIssue = (String) metaRow[o + 8];
            stewardshipRecordId = (UUID) metaRow[o + 9];
            stewardshipContactId = (UUID) metaRow[o + 10];
            stewardshipOrganizationId = (UUID) metaRow[o + 11];
            if (batched) {
                referenceDocumentIds = preload.referenceDocumentIdsByItem()
                        .getOrDefault(item.getId(), List.of());
            } else {
                var ai = administeredItemRepository.findByItem_Id(item.getId()).orElse(null);
                referenceDocumentIds = ai == null || ai.getReferenceDocuments() == null
                        ? List.of()
                        : ai.getReferenceDocuments().stream().map(ReferenceDocument::getId).toList();
            }
        }

        // 标识符：批量走标量投影（ScopedIdentifier 实体的 identifiedItem 是默认 EAGER 的
        // @ManyToOne，加载它即触发继承展开）；单条沿用既有查询。
        List<Object[]> siRows = null;
        if (batched) {
            siRows = preload.identifiersByItem().get(item.getId());
        }
        if (siRows != null && !siRows.isEmpty()) {
            Object[] siRow = preferPrimaryScalarIdentifier(siRows, preload.namespacesById());
            UUID siId = (UUID) siRow[1];
            identifier = (String) siRow[2];
            version = (String) siRow[3];
            nsId = (UUID) siRow[4];
            ScopedIdentifierLifecycle siLifecycle = preload.lifecycles().get(siId);
            if (siLifecycle != null) {
                identifierCreationMode = siLifecycle.getIdentifierCreationMode();
                versionCreationMode = siLifecycle.getVersionCreationMode();
            }
            Namespace ns = preload.namespacesById().get(nsId);
            nsLabel = ns == null ? null : ItemNamingHelper.preferredDesignation(ns);
            scopedIdentifierStr = buildScopedIdentifierStr(nsLabel, identifier, version);
        } else if (!batched) {
            List<ScopedIdentifier> siList = scopedIdentifierRepository.findByItemId(item.getId());
            if (siList != null && !siList.isEmpty()) {
                ScopedIdentifier si = preferPrimaryIdentifier(siList);
                identifier = si.getIdentifier();
                version = si.getVersion();
                ScopedIdentifierLifecycle siLifecycle = lifecycleService.getOrDefault(si.getId());
                identifierCreationMode = siLifecycle.getIdentifierCreationMode();
                versionCreationMode = siLifecycle.getVersionCreationMode();
                if (si.getNamespace() != null) {
                    nsId = si.getNamespace().getId();
                    nsLabel = ItemNamingHelper.preferredDesignation(si.getNamespace());
                }
                scopedIdentifierStr = buildScopedIdentifierStr(nsLabel, identifier, version);
            }
        }

        // 关联 SubmissionRecord 列表（align-submission-record-to-iso）：编辑表单"提交记录"只读面板数据源。
        // 列表页（includeSubmissionRecords=false）不组装——表格不展示该数据，逐条查询是
        // 列表接口的主要 SQL 来源之一（每条目 2 次，20 行/页即 40 次往返）。
        List<SubmissionRecordSummary> submissionRecordSummaries = List.of();
        if (includeSubmissionRecords && administeredItemId != null) {
            List<SubmissionRecord> srs = submissionRecordRepository
                    .findAllByRegisteredItemIdOrderByIdDesc(administeredItemId);
            Map<UUID, Long> itemCountBySubmission =
                    submissionRecordRepository.countRegisteredItemsBySubmissionRecordIds(
                            srs.stream().map(SubmissionRecord::getId).toList());
            submissionRecordSummaries = srs.stream()
                    .map(sr -> SubmissionRecordMapper.toSummary(
                            sr, itemCountBySubmission.getOrDefault(sr.getId(), 0L)))
                    .toList();
        }

        return new ItemMetaResponse(
                item.getId(),
                ItemNamingHelper.preferredDesignation(item),
                ItemNamingHelper.preferredDefinition(item),
                scopedIdentifierStr,
                nsId,
                nsLabel,
                identifier,
                version,
                registrationStatus,
                administeredItemId,
                registrationAuthorityId,
                registrationAuthorityName,
                identifierCreationMode,
                versionCreationMode,
                stateEffectiveDate,
                stateUntilDate,
                administrativeStatus,
                administrativeNote,
                unresolvedIssue,
                stewardshipRecordId,
                stewardshipContactId,
                stewardshipOrganizationId,
                referenceDocumentIds,
                submissionRecordSummaries);
    }

    /**
     * 标识符标量行的主标识偏好，与 {@link #preferPrimaryIdentifier(List)} 同规则：
     * 优先注册机构自有命名空间下的标识；其次非全局、非迁入副本命名空间下的标识。
     */
    private static Object[] preferPrimaryScalarIdentifier(List<Object[]> siRows, Map<UUID, Namespace> namespaces) {
        return siRows.stream()
                .filter(r -> namespaces.get((UUID) r[4]) != null
                        && namespaces.get((UUID) r[4]).getRegistrationAuthority() != null)
                .findFirst()
                .orElseGet(() -> siRows.stream()
                        .filter(r -> {
                            Namespace ns = namespaces.get((UUID) r[4]);
                            return ns != null
                                    && !GlobalNamespaceService.isGlobalNamespace(ns)
                                    && !GlobalNamespaceService.isImportedNamespace(ns);
                        })
                        .findFirst()
                        .orElse(siRows.get(0)));
    }

    private static String buildScopedIdentifierStr(String nsLabel, String identifier, String version) {
        StringBuilder sb = new StringBuilder();
        if (nsLabel != null) {
            sb.append(nsLabel).append(':');
        }
        sb.append(identifier == null ? "" : identifier);
        if (version != null && !version.isBlank()) {
            sb.append('@').append(version);
        }
        return sb.toString();
    }

    private String nsLabelOf(UUID nsId) {
        if (nsId == null) {
            return null;
        }
        return namespaceRepository.findById(nsId)
                .map(ItemNamingHelper::preferredDesignation)
                .orElse(null);
    }

    /**
     * 条目主标识偏好：优先注册机构自有命名空间下的标识（本地编辑与展示的
     * 主标识）；其次非全局、非迁入副本命名空间下的标识。联邦迁移后的双标识
     * 条目据此展示目的方标识而非来源标识。
     */
    private ScopedIdentifier preferPrimaryIdentifier(List<ScopedIdentifier> siList) {
        return siList.stream()
                .filter(s -> s.getNamespace() != null && s.getNamespace().getRegistrationAuthority() != null)
                .findFirst()
                .orElseGet(() -> siList.stream()
                        .filter(s -> s.getNamespace() != null
                                && !GlobalNamespaceService.isGlobalNamespace(s.getNamespace())
                                && !GlobalNamespaceService.isImportedNamespace(s.getNamespace()))
                        .findFirst()
                        .orElse(siList.get(0)));
    }

    public List<TextLineResponse> mapDesignations(Item item) {
        if (item == null || item.getDesignations() == null) {
            return Collections.emptyList();
        }
        List<TextLineResponse> out = new ArrayList<>(item.getDesignations().size());
        for (Designation d : item.getDesignations()) {
            out.add(new TextLineResponse(
                    d.getId(),
                    d.getLanguage() == null ? null : d.getLanguage().getId(),
                    d.getLanguage() == null ? null : d.getLanguage().getLanguageIdentifier(),
                    d.getSign() == null ? null : d.getSign().getValue(),
                    d.getSource() == null ? null : d.getSource().getId(),
                    d.getSource() == null || d.getSource().getTitle() == null
                            ? null : d.getSource().getTitle().getValue(),
                    mapDesignationContexts(d),
                    d.getNamingConventions() == null ? null
                            : d.getNamingConventions().stream()
                                    .map(NamingConvention::getId).toList()));
        }
        return out;
    }

    public List<TextLineResponse> mapDefinitions(Item item) {
        if (item == null || item.getDefinitions() == null) {
            return Collections.emptyList();
        }
        List<TextLineResponse> out = new ArrayList<>(item.getDefinitions().size());
        for (Definition d : item.getDefinitions()) {
            out.add(new TextLineResponse(
                    d.getId(),
                    d.getLanguage() == null ? null : d.getLanguage().getId(),
                    d.getLanguage() == null ? null : d.getLanguage().getLanguageIdentifier(),
                    d.getText() == null ? null : d.getText().getValue(),
                    d.getSource() == null ? null : d.getSource().getId(),
                    d.getSource() == null || d.getSource().getTitle() == null
                            ? null : d.getSource().getTitle().getValue(),
                    mapDefinitionContexts(d),
                    null));
        }
        return out;
    }

    private List<TextLineContextResponse> mapDesignationContexts(Designation d) {
        if (d.getDesignationContexts() == null || d.getDesignationContexts().isEmpty()) {
            return Collections.emptyList();
        }
        return d.getDesignationContexts().stream()
                .map(dc -> new TextLineContextResponse(
                        dc.getContext() == null ? null : dc.getContext().getId(),
                        dc.getContext() == null ? null : ItemNamingHelper.preferredDesignation(dc.getContext()),
                        dc.getAcceptability() == null ? null : dc.getAcceptability().name()))
                .toList();
    }

    private List<TextLineContextResponse> mapDefinitionContexts(Definition d) {
        if (d.getDefinitionContexts() == null || d.getDefinitionContexts().isEmpty()) {
            return Collections.emptyList();
        }
        return d.getDefinitionContexts().stream()
                .map(dc -> new TextLineContextResponse(
                        dc.getContext() == null ? null : dc.getContext().getId(),
                        dc.getContext() == null ? null : ItemNamingHelper.preferredDesignation(dc.getContext()),
                        dc.getAcceptability() == null ? null : dc.getAcceptability().name()))
                .toList();
    }

    private LanguageIdentification loadLanguage(UUID id) {
        if (id == null) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "designation/definition 缺少 languageId");
        }
        return languageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("LanguageIdentification", id));
    }

    private ReferenceDocument loadSourceOrNull(UUID id) {
        if (id == null) return null;
        return referenceDocumentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ReferenceDocument", id));
    }

    /** 指称/定义上下文自引用哨兵：新建上下文时条目尚未持久化、无真实 id，
     *  前端以此 NIL UUID 表示"关联到条目自身"；后端仅接受 Context 条目使用。 */
    public static final UUID SELF_CONTEXT_REFERENCE =
            UUID.fromString("00000000-0000-0000-0000-000000000000");

    private io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Context loadContext(UUID id) {
        return contextRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Context", id));
    }

    /**
     * 解析指称/定义关联的上下文。支持自引用：上下文条目的指称/定义可关联其自身
     * （否则无法创建第一个根上下文）。新建时条目尚无 id，以前端哨兵
     * {@link #SELF_CONTEXT_REFERENCE} 表示；编辑时直接引用自身 id 亦可。
     * 非 Context 条目使用自引用（哨兵或自身 id）一律拒绝。
     */
    private io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Context loadContext(
            Item item, UUID id) {
        boolean selfReference = SELF_CONTEXT_REFERENCE.equals(id)
                || (id != null && id.equals(item.getId()));
        if (selfReference) {
            if (item instanceof io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Context self) {
                return self;
            }
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "仅上下文条目可将指称/定义关联到其自身");
        }
        return loadContext(id);
    }

    /**
     * 解析指称/定义的语言。显式指定时直接加载；未指定时回退到注册表默认语言。
     * 若两者皆无，则抛出业务异常，符合 ISO/IEC 11179-3 的条件重数规则。
     */
    private LanguageIdentification resolveLanguage(UUID languageId) {
        if (languageId != null) {
            return loadLanguage(languageId);
        }
        return loadDefaultLanguage()
                .orElseThrow(() -> new BusinessException(ApiCodes.VALIDATION_FAILED,
                        "未配置注册表默认语言，无法省略指称/定义的语言标识"));
    }

    private List<LanguageIdentification> resolveLanguages(List<TextLine> lines) {
        return lines.stream()
                .map(line -> resolveLanguage(line.languageId()))
                .toList();
    }

    private java.util.Optional<LanguageIdentification> loadDefaultLanguage() {
        return registrySpecificationRepository.findAll().stream()
                .findFirst()
                .map(rs -> rs.getDefaultLanguage());
    }
}