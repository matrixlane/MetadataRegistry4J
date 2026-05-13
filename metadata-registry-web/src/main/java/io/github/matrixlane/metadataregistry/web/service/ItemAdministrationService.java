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
import io.github.matrixlane.metadataregistry.commonfacilities.identification.IdentifiedItem;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.ScopedIdentifier;
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
import io.github.matrixlane.metadataregistry.web.repository.ContextRepository;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.repository.ReferenceDocumentRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import io.github.matrixlane.metadataregistry.web.service.namingconvention.NamingConventionEngine;
import io.github.matrixlane.metadataregistry.web.service.namingconvention.NamingConventionRuleResult;
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

    public void applyDesignations(Item item, List<TextLine> designations) {
        if (designations == null) return;

        validateNamingConventions(designations);
        validatePreferredUniqueness(designations);

        item.getDesignations().clear();
        for (TextLine line : designations) {
            Designation d = new Designation();
            d.setItem(item);
            d.setSign(Sign.of(line.value()));
            d.setLanguage(loadLanguage(line.languageId()));
            d.setSource(loadSourceOrNull(line.sourceId()));
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
        }
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

    private void validatePreferredUniqueness(List<TextLine> designations) {
        Map<UUID, Integer> preferredCountByLanguage = new HashMap<>();
        for (TextLine line : designations) {
            if (line.contexts() == null) continue;
            boolean hasPreferred = line.contexts().stream()
                    .anyMatch(c -> c.acceptability() == io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Acceptability.PREFERRED);
            if (hasPreferred) {
                preferredCountByLanguage.merge(line.languageId(), 1, Integer::sum);
            }
        }
        for (Map.Entry<UUID, Integer> entry : preferredCountByLanguage.entrySet()) {
            if (entry.getValue() > 1) {
                throw new BusinessException(ApiCodes.CONFLICT,
                        "同一语言下只能有一个首选名称 (PREFERRED)，languageId=" + entry.getKey());
            }
        }
    }

    public void applyDefinitions(Item item, List<TextLine> definitions) {
        if (definitions == null) return;

        List<DefinitionSnapshot> oldSnapshots = snapshotDefinitions(item.getDefinitions());

        item.getDefinitions().clear();
        for (TextLine line : definitions) {
            Definition d = new Definition();
            d.setItem(item);
            d.setText(Text.of(line.value()));
            d.setLanguage(loadLanguage(line.languageId()));
            d.setSource(loadSourceOrNull(line.sourceId()));
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
        if (req == null) {
            return null;
        }
        AdministeredItem ai = (item.getIdentifiedItem() instanceof AdministeredItem existing)
                ? existing
                : new AdministeredItem(item);

        Namespace ns = req.namespaceId() == null ? null
                : namespaceRepository.findById(req.namespaceId())
                .orElseThrow(() -> new ResourceNotFoundException("Namespace", req.namespaceId()));

        if (ai.getScopedIdentifiers() == null || ai.getScopedIdentifiers().isEmpty()) {
            ScopedIdentifier si = new ScopedIdentifier();
            si.setIdentifier(req.identifier());
            si.setVersion(req.version());
            si.setNamespace(ns);
            si.setIdentifiedItem(ai);
            ai.getScopedIdentifiers().add(si);
        } else {
            ScopedIdentifier si = ai.getScopedIdentifiers().get(0);
            si.setIdentifier(req.identifier());
            si.setVersion(req.version());
            si.setNamespace(ns);
        }

        if (req.registrationAuthorityId() != null) {
            RegistrationAuthority ra = registrationAuthorityRepository.findById(req.registrationAuthorityId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "RegistrationAuthority", req.registrationAuthorityId()));
            ai.setRegistrationAuthority(ra);
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

    public ItemMetaResponse buildItemMeta(Item item) {
        if (item == null) {
            return null;
        }
        IdentifiedItem ii = item.getIdentifiedItem();
        UUID administeredItemId = ii instanceof AdministeredItem ai ? ai.getId() : null;
        UUID nsId = null;
        String nsLabel = null;
        String identifier = null;
        String version = null;
        if (ii != null && ii.getScopedIdentifiers() != null && !ii.getScopedIdentifiers().isEmpty()) {
            ScopedIdentifier si = ii.getScopedIdentifiers().get(0);
            identifier = si.getIdentifier();
            version = si.getVersion();
            if (si.getNamespace() != null) {
                nsId = si.getNamespace().getId();
                nsLabel = ItemNamingHelper.preferredDesignation(si.getNamespace());
            }
        }
        return new ItemMetaResponse(
                item.getId(),
                ItemNamingHelper.preferredDesignation(item),
                ItemNamingHelper.preferredDefinition(item),
                ItemNamingHelper.firstScopedIdentifier(item),
                nsId,
                nsLabel,
                identifier,
                version,
                ItemNamingHelper.currentRegistrationStatus(item),
                administeredItemId);
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
                    mapDesignationContexts(d)));
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
                    mapDefinitionContexts(d)));
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
            throw new BusinessException("designation/definition 缺少 languageId");
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
}
