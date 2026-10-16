package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Text;
import io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.NamingConvention;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.namingconvention.NamingConventionRequest;
import io.github.matrixlane.metadataregistry.web.dto.namingconvention.NamingConventionResponse;
import io.github.matrixlane.metadataregistry.web.repository.NamingConventionRepository;
import io.github.matrixlane.metadataregistry.web.util.ItemNamingHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class NamingConventionService {

    private final NamingConventionRepository repository;
    private final NamingConventionRulesService rulesService;
    private final ItemAdministrationService itemAdministrationService;

    @Transactional(readOnly = true)
    public Page<NamingConventionResponse> list(String keyword, Pageable pageable) {
        Specification<NamingConvention> spec = byKeyword(keyword);
        return ItemListSorts.findAll(repository, spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public NamingConventionResponse get(UUID id) {
        return toResponse(loadOrThrow(id));
    }

    public NamingConventionResponse create(NamingConventionRequest request) {
        NamingConvention entity = new NamingConvention();
        applyRequest(entity, request);
        NamingConvention saved = repository.save(entity);
        rulesService.applyRules(saved.getId(), request.rulesJson());
        return toResponse(saved);
    }

    public NamingConventionResponse update(UUID id, NamingConventionRequest request) {
        NamingConvention entity = loadOrThrow(id);
        applyRequest(entity, request);
        NamingConvention saved = repository.save(entity);
        rulesService.applyRules(saved.getId(), request.rulesJson());
        return toResponse(saved);
    }

    public void delete(UUID id) {
        NamingConvention entity = loadOrThrow(id);
        // Recorded 及之后的注册状态已纳入版本控制，只能走 Retired，不允许直接删除
        itemAdministrationService.assertDeletable(entity);
        // 解除提交记录连接；关联条目删光后空的 SubmissionRecord 一并删除
        itemAdministrationService.removeFromSubmissionRecords(entity);
        repository.deleteById(id);
    }

    private void applyRequest(NamingConvention entity, NamingConventionRequest request) {
        entity.setScopeRule(Text.of(request.scopeRule()));
        entity.setAuthorityRule(toTextOrNull(request.authorityRule()));
        entity.setSemanticRule(toTextOrNull(request.semanticRule()));
        entity.setSyntacticRule(toTextOrNull(request.syntacticRule()));
        entity.setLexicalRule(toTextOrNull(request.lexicalRule()));
        itemAdministrationService.applyDesignations(entity, request.designations());
        itemAdministrationService.applyDefinitions(entity, request.definitions());
        if (request.registration() != null) {
            itemAdministrationService.ensureAdministeredItem(entity, request.registration());
        }
    }

    private static Text toTextOrNull(String value) {
        return StringUtils.hasText(value) ? Text.of(value) : null;
    }

    private NamingConvention loadOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("NamingConvention", id));
    }

    /** 委托共享实现：名称（指称）或标识符命中即返回，见 {@link ObjectClassService#byKeyword}。 */
    private static Specification<NamingConvention> byKeyword(String keyword) {
        return ObjectClassService.byKeyword(keyword);
    }

    private NamingConventionResponse toResponse(NamingConvention entity) {
        return new NamingConventionResponse(
                entity.getId(),
                ItemNamingHelper.preferredDesignation(entity),
                textValue(entity.getScopeRule()),
                textValue(entity.getAuthorityRule()),
                textValue(entity.getSemanticRule()),
                textValue(entity.getSyntacticRule()),
                textValue(entity.getLexicalRule()),
                rulesService.getOrDefault(entity.getId()).getRulesJson(),
                itemAdministrationService.mapDesignations(entity),
                itemAdministrationService.mapDefinitions(entity),
                itemAdministrationService.buildItemMeta(entity));
    }

    private static String textValue(Text text) {
        return text == null ? null : text.getValue();
    }
}