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
import io.github.matrixlane.metadataregistry.commonfacilities.identification.IdentifierCreationMode;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Versions;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.AdministeredItem;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationState;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationStatus;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.common.ItemMetaResponse;
import io.github.matrixlane.metadataregistry.web.dto.common.ItemRegistrationRequest;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLine;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLineContextResponse;
import io.github.matrixlane.metadataregistry.web.dto.common.TextLineResponse;
import io.github.matrixlane.metadataregistry.web.repository.AdministeredItemRepository;
import io.github.matrixlane.metadataregistry.web.repository.ScopedIdentifierRepository;
import io.github.matrixlane.metadataregistry.web.repository.ContextRepository;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamingConventionRepository;
import io.github.matrixlane.metadataregistry.web.repository.ReferenceDocumentRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrySpecificationRepository;
import io.github.matrixlane.metadataregistry.web.federation.GlobalNamespaceService;
import io.github.matrixlane.metadataregistry.web.seed.SystemDefaultNamespaceInitializer;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ItemAdministrationService {

    private final LanguageIdentificationRepository languageRepository;
    private final ReferenceDocumentRepository referenceDocumentRepository;
    private final NamespaceRepository namespaceRepository;
    private final RegistrationAuthorityRepository registrationAuthorityRepository;
    private final ContextRepository contextRepository;
    private final NamingConventionEngine namingConventionEngine;
    private final DefinitionAuditLogService definitionAuditLogService;
    private final ScopedIdentifierRepository scopedIdentifierRepository;
    private final AdministeredItemRepository administeredItemRepository;
    private final IdentifierInputPolicyService identifierInputPolicyService;
    private final NamingConventionRepository namingConventionRepository;
    private final NamespaceConstraintValidator namespaceConstraintValidator;
    private final ActiveRegistrarService activeRegistrarService;
    private final RegistrySpecificationRepository registrySpecificationRepository;

    public void applyDesignations(Item item, List<TextLine> designations) {
        applyDesignations(item, designations, null);
    }

    /**
     * Apply designations to an item and validate them against the given namespace's
     * naming convention constraints when the namespace enforces mandatory naming conventions.
     */
    public void applyDesignations(Item item, List<TextLine> designations, UUID namespaceId) {
        if (designations == null) return;

        validateIsolatedLines(designations, "指称");
        validateNamingConventions(designations);
        validatePreferredUniqueness(designations, resolveLanguages(designations), "指称");

        item.getDesignations().clear();
        for (int i = 0; i < designations.size(); i++) {
            TextLine line = designations.get(i);
            Designation d = new Designation();
            d.setItem(item);
            d.setSign(Sign.of(line.value()));
            d.setLanguage(resolveLanguage(line.languageId()));
            d.setSource(loadSourceOrNull(line.sourceId()));
            applyNamingConventions(d, line.namingConventionIds());
            d.setAcceptability(line.acceptability());
            if (line.contexts() != null) {
                for (var ce : line.contexts()) {
                    DesignationContext dc = new DesignationContext();
                    dc.setDesignation(d);
                    dc.setContext(loadContext(ce.contextId()));
                    dc.setAcceptability(ce.acceptability());
                    d.getDesignationContexts().add(dc);
                }
            }
            item.getDesignations().add(d);
            if (namespaceId != null) {
                namespaceConstraintValidator.validateDesignation(namespaceId, d, null);
            }
        }
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

    public void applyDefinitions(Item item, List<TextLine> definitions) {
        if (definitions == null) return;

        validateIsolatedLines(definitions, "定义");
        validatePreferredUniqueness(definitions, resolveLanguages(definitions), "定义");

        List<DefinitionSnapshot> oldSnapshots = snapshotDefinitions(item.getDefinitions());

        item.getDefinitions().clear();
        for (int i = 0; i < definitions.size(); i++) {
            TextLine line = definitions.get(i);
            Definition d = new Definition();
            d.setItem(item);
            d.setText(Text.of(line.value()));
            d.setLanguage(resolveLanguage(line.languageId()));
            d.setSource(loadSourceOrNull(line.sourceId()));
            d.setAcceptability(line.acceptability());
            if (line.contexts() != null) {
                for (var ce : line.contexts()) {
                    DefinitionContext dc = new DefinitionContext();
                    dc.setDefinition(d);
                    dc.setContext(loadContext(ce.contextId()));
                    dc.setAcceptability(ce.acceptability());
                    d.getDefinitionContexts().add(dc);
                }
            }
            item.getDefinitions().add(d);
        }

        List<DefinitionSnapshot> newSnapshots = snapshotDefinitions(item.getDefinitions());
        logDefinitionChanges(item.getId(), oldSnapshots, newSnapshots);
    }

    private List<DefinitionSnapshot> snapshotDefinitions(List<Definition> definitions) {
        if (definitions == null) return Collections.emptyList();
        return definitions.stream()
                .map(d -> new DefinitionSnapshot(
                        d.getId(),
                        d.getText() == null ? null : d.getText().getValue(),
                        d.getLanguage() == null ? null : d.getLanguage().getLanguageIdentifier()))
                .toList();
    }

    private void logDefinitionChanges(UUID itemId, List<DefinitionSnapshot> oldList, List<DefinitionSnapshot> newList) {
        if (itemId == null) return;
        String operator = currentUser();
        Map<String, DefinitionSnapshot> oldMap = new HashMap<>();
        for (DefinitionSnapshot s : oldList) {
            if (s.language != null) oldMap.put(s.language, s);
        }
        Map<String, DefinitionSnapshot> newMap = new HashMap<>();
        for (DefinitionSnapshot s : newList) {
            if (s.language != null) newMap.put(s.language, s);
        }

        for (DefinitionSnapshot oldSnap : oldList) {
            DefinitionSnapshot newSnap = newMap.get(oldSnap.language);
            if (newSnap == null) {
                definitionAuditLogService.save(io.github.matrixlane.metadataregistry.web.entity.DefinitionAuditLog.ofDelete(
                        oldSnap.id, itemId, oldSnap.text, oldSnap.language, operator));
            } else if (!oldSnap.text.equals(newSnap.text)) {
                definitionAuditLogService.save(io.github.matrixlane.metadataregistry.web.entity.DefinitionAuditLog.ofUpdate(
                        oldSnap.id, itemId, oldSnap.text, newSnap.text, oldSnap.language, operator));
            }
        }
        for (DefinitionSnapshot newSnap : newList) {
            if (!oldMap.containsKey(newSnap.language)) {
                // CREATE: id may be null before persist; logged with null definitionId,
                // itemId and languageIdentifier allow tracing.
                definitionAuditLogService.save(io.github.matrixlane.metadataregistry.web.entity.DefinitionAuditLog.ofCreate(
                        newSnap.id, itemId, newSnap.text, newSnap.language, operator));
            }
        }
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

    private record DefinitionSnapshot(UUID id, String text, String language) {
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

        enforceRaNamespacePrecondition(ns);

        if (ai.getScopedIdentifiers() == null || ai.getScopedIdentifiers().isEmpty()) {
            ScopedIdentifier si = new ScopedIdentifier();
            String identifier;
            try {
                identifier = identifierInputPolicyService.resolveIdentifier(ns, req.identifier(),
                        org.hibernate.Hibernate.getClass(item).getSimpleName(), req.variables());
            } catch (io.github.matrixlane.metadataregistry.commonfacilities.identification.segment.SegmentTemplateException e) {
                throw new BusinessException("标识生成失败：" + e.getMessage());
            }
            si.setIdentifier(identifier);
            si.setVersion(identifierInputPolicyService.resolveVersion(ns, req.version()));
            // 持久化创建时用户选择的 AUTO/MANUAL：请求未携带 mode 时按 identifier / version
            // 是否有显式值反推（空 → AUTO，非空 → MANUAL）。mode 创建即固化，后续编辑不变更。
            si.setIdentifierCreationMode(req.identifierMode() != null
                    ? req.identifierMode()
                    : ItemRegistrationRequest.resolveMode(req.identifier()));
            si.setVersionCreationMode(req.versionMode() != null
                    ? req.versionMode()
                    : ItemRegistrationRequest.resolveMode(req.version()));
            si.setNamespace(ns);
            si.setIdentifiedItem(ai);
            ai.getScopedIdentifiers().add(si);
        } else {
            ScopedIdentifier si = selectEditableIdentifier(ai, ns);

            // 标识符：AUTO 时 MUST 拒绝任何手动修改；MANUAL / NULL 时沿用单 SI 可改逻辑
            String requestedIdentifier = req.identifier();
            if (requestedIdentifier != null && !requestedIdentifier.isBlank()
                    && !Objects.equals(requestedIdentifier, si.getIdentifier())) {
                if (si.getIdentifierCreationMode() == IdentifierCreationMode.AUTO) {
                    throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                            "该标识符创建时选择自动生成，不允许手动修改");
                }
                if (ai.getScopedIdentifiers().size() == 1) {
                    si.setIdentifier(requestedIdentifier);
                }
            }

            // 版本：AUTO 时 MUST 忽略请求体显式值并按命名空间版本策略递增；MANUAL / NULL 时
            // 接受显式值并按策略校验，空则递增（registry-merge-strategy D4/D5）。
            // 联邦合并路径不经由此处，版本由合并执行器显式维护。
            String requestedVersion = req.version();
            if (si.getVersionCreationMode() == IdentifierCreationMode.AUTO) {
                si.setVersion(Versions.bump(si.getVersion(), ns != null ? ns.getVersioningPolicy() : null));
            } else if (requestedVersion != null && !requestedVersion.isBlank()
                    && !requestedVersion.equals(si.getVersion())) {
                identifierInputPolicyService.validateManualVersion(ns, requestedVersion);
                si.setVersion(requestedVersion);
            } else {
                si.setVersion(Versions.bump(si.getVersion(), ns != null ? ns.getVersioningPolicy() : null));
            }
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

        if (ai.getRegistrationState() == null) {
            RegistrationState state = new RegistrationState();
            state.setRegistrationStatus(RegistrationStatus.RECORDED);
            state.setEffectiveDate(LocalDateTime.now());
            ai.setRegistrationState(state);
        }
        if (ai.getCreationDateTime() == null) {
            ai.setCreationDateTime(LocalDateTime.now());
        }
        ai.setLastChangeDateTime(LocalDateTime.now());

        item.setIdentifiedItem(ai);
        return ai;
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
     * 新建条目的归属推导：当前账号必须持有活动 Registrar 身份（非 Registrar
     * 没有权限注册 Administered_Item）；注册机构自有命名空间所属机构必须与
     * 活动身份所在机构一致（防止跨机构写入）。无认证（种子/系统路径）时返回
     * null，维持既有行为。
     */
    private RegistrationAuthority resolveOwnershipForCreate(Namespace ns) {
        if (activeRegistrarService.currentAccountIdOrNull() == null) {
            return null;
        }
        var active = activeRegistrarService.resolveForCurrentUser()
                .orElseThrow(() -> new BusinessException(ApiCodes.VALIDATION_FAILED,
                        "当前账号没有 Registrar 身份，无法注册 Administered_Item；请先被指派为 Registrar"));
        RegistrationAuthority activeRa = active.getRegistrationAuthority();
        if (ns != null && ns.getRegistrationAuthority() != null
                && !ns.getRegistrationAuthority().getId().equals(activeRa.getId())) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "目标命名空间属于其他注册机构，请先将活动 registrar身份切换为该机构的身份");
        }
        return activeRa;
    }

    /**
     * 自有数据标准的命名空间前置条件：系统中必须存在至少一个与注册机构关联的
     * 命名空间，否则阻止创建自有数据标准并引导先补建命名空间。
     *
     * <p>外部命名空间（无关联注册机构，且非 system-default）用于导入/编目外部
     * 组织的数据标准，不受此限制；system-default 仅供系统内部与试用，不计入该
     * 前置条件。
     */
    private void enforceRaNamespacePrecondition(Namespace ns) {
        boolean externalNamespace = ns != null
                && ns.getRegistrationAuthority() == null
                && !SystemDefaultNamespaceInitializer.SYSTEM_DEFAULT_SCHEME.equals(ns.getSchemeReference());
        if (externalNamespace) {
            return;
        }
        if (!namespaceRepository.existsByRegistrationAuthorityIsNotNull()) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "创建自有数据标准前，请先创建基于注册机构的命名空间");
        }
    }

    public ItemMetaResponse buildItemMeta(Item item) {
        if (item == null) {
            return null;
        }

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

        // Try to query AdministeredItem by itemId
        List<Object[]> metaList = administeredItemRepository.findMetaByItemId(item.getId());
        if (metaList != null && !metaList.isEmpty()) {
            Object[] metaRow = metaList.get(0);
            administeredItemId = (UUID) metaRow[0];
            registrationStatus = (RegistrationStatus) metaRow[1];
        }
        var administeredItem = administeredItemRepository.findByItem_Id(item.getId()).orElse(null);
        if (administeredItem != null && administeredItem.getRegistrationAuthority() != null) {
            registrationAuthorityId = administeredItem.getRegistrationAuthority().getId();
            var raNameList = administeredItem.getRegistrationAuthority().getName();
            registrationAuthorityName = raNameList == null ? null
                    : raNameList.stream().findFirst().map(n -> n.getValue()).orElse(null);
        }

        // Try to query ScopedIdentifier by itemId
        List<ScopedIdentifier> siList = scopedIdentifierRepository.findByItemId(item.getId());
        if (siList != null && !siList.isEmpty()) {
            ScopedIdentifier si = preferPrimaryIdentifier(siList);
            identifier = si.getIdentifier();
            version = si.getVersion();
            identifierCreationMode = si.getIdentifierCreationMode();
            versionCreationMode = si.getVersionCreationMode();
            if (si.getNamespace() != null) {
                nsId = si.getNamespace().getId();
                nsLabel = ItemNamingHelper.preferredDesignation(si.getNamespace());
            }
            
            // Build scopedIdentifierStr: namespace:identifier@version
            StringBuilder sb = new StringBuilder();
            if (nsLabel != null) sb.append(nsLabel).append(':');
            sb.append(identifier == null ? "" : identifier);
            if (version != null && !version.isBlank()) {
                sb.append('@').append(version);
            }
            scopedIdentifierStr = sb.toString();
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
                versionCreationMode);
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
                    d.getAcceptability()));
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
                    d.getAcceptability()));
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

    private io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.core.Context loadContext(UUID id) {
        return contextRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Context", id));
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