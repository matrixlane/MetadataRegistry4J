package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.LanguageIdentification;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.RegistrationAuthorityIdentifier;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.PhoneNumber;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.PostalAddress;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.Registrar;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.web.auth.AccountRepository;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.registrationauthority.RegistrationAuthorityRequest;
import io.github.matrixlane.metadataregistry.web.dto.registrationauthority.RegistrationAuthorityResponse;
import io.github.matrixlane.metadataregistry.web.edition.DeploymentEdition;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import io.github.matrixlane.metadataregistry.web.util.ItemNamingHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class RegistrationAuthorityService {

    private final RegistrationAuthorityRepository repository;
    private final NamespaceRepository namespaceRepository;
    private final LanguageIdentificationRepository languageRepository;
    private final SystemSettingService systemSettingService;
    private final RegistrationProcessService registrationProcessService;
    private final RegistrarService registrarService;
    private final ContactAccountBindingService contactAccountBindingService;
    private final AccountRepository accountRepository;
    private final RegistrationAuthorityDeletionService deletionService;

    @Transactional(readOnly = true)
    public Page<RegistrationAuthorityResponse> list(String keyword, Pageable pageable) {
        Specification<RegistrationAuthority> spec = Specification.where(null);
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> {
                if (q != null) q.distinct(true);
                var nameJoin = root.join("name", jakarta.persistence.criteria.JoinType.LEFT);
                return cb.like(cb.lower(nameJoin.get("value")), like);
            });
        }
        return repository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public RegistrationAuthorityResponse get(UUID id) {
        return toResponse(loadOrThrow(id));
    }

    /**
     * 新增注册机构（版本化治理）。
     *
     * <p>PERSONAL 个人版：不设数量限制，调用者必须为 SUPER_ADMIN；创建成功后自动为
     * 当前账号建立该机构的 Registrar（注册管理员），若为该账号首个身份则标为默认。
     * TEAM 团队版（或版本未设置，保守按 TEAM）：沿用 runningMode 规则——SINGLE 且
     * 已存在注册机构时拒绝；不自动建立注册人员。</p>
     */
    public RegistrationAuthorityResponse create(RegistrationAuthorityRequest request,
                                                UUID accountId, SystemRole role) {
        DeploymentEdition edition = systemSettingService.getEdition();
        boolean personal = edition == DeploymentEdition.PERSONAL;
        if (personal) {
            if (role != SystemRole.SUPER_ADMIN) {
                throw new BusinessException(ApiCodes.VALIDATION_FAILED, "个人版仅允许超级管理员创建注册机构");
            }
        } else if ("SINGLE".equalsIgnoreCase(systemSettingService.getRunningMode()) && repository.count() >= 1) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "单注册表模式下只能创建一个注册机构");
        }
        RegistrationAuthority entity = new RegistrationAuthority();
        applyRequest(entity, request);
        RegistrationAuthority saved = repository.save(entity);
        // registration-process-designer：新注册机构创建即幂等播种标准注册流程规则
        registrationProcessService.ensureSeed(saved.getId());
        if (personal) {
            autoCreateRegistrar(saved, accountId);
        }
        return toResponse(saved);
    }

    /** 个人版：为当前账号自动建立该机构的 Registrar，并确保首个身份为默认身份。 */
    private void autoCreateRegistrar(RegistrationAuthority ra, UUID accountId) {
        boolean first = !contactAccountBindingService.hasAnyContact(accountId);
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        Registrar registrar = registrarService.createRegistrarForUser(
                accountId, account.getUsername(), null, "注册管理员", ra.getId(), ra);
        if (first) {
            contactAccountBindingService.markDefault(accountId, registrar.getId());
        }
    }

    public RegistrationAuthorityResponse update(UUID id, RegistrationAuthorityRequest request) {
        RegistrationAuthority entity = loadOrThrow(id);
        applyRequest(entity, request);
        return toResponse(repository.save(entity));
    }

    /**
     * 删除注册机构（版本化治理 + 级联删除）。
     *
     * <p>PERSONAL 个人版：主要注册机构（isPrimary）不允许删除，其余经
     * {@link RegistrationAuthorityDeletionService} 级联删除。TEAM 团队版
     * （或版本未设置，保守按 TEAM）：SINGLE 模式下不允许删除。</p>
     */
    public void delete(UUID id) {
        RegistrationAuthority entity = loadOrThrow(id);
        DeploymentEdition edition = systemSettingService.getEdition();
        if (edition == DeploymentEdition.PERSONAL) {
            if (entity.isPrimary()) {
                throw new BusinessException(ApiCodes.VALIDATION_FAILED, "主要注册机构不允许删除");
            }
        } else if ("SINGLE".equalsIgnoreCase(systemSettingService.getRunningMode())) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "单注册表模式下不允许删除注册机构");
        }
        deletionService.delete(id);
    }

    private RegistrationAuthority loadOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RegistrationAuthority", id));
    }

    private void applyRequest(RegistrationAuthority entity, RegistrationAuthorityRequest request) {
        entity.getName().clear();
        entity.getName().add(Sign.of(request.name()));
        if (request.englishName() != null && !request.englishName().isBlank()) {
            entity.getName().add(Sign.of(request.englishName()));
        }

        entity.setMailAddress(request.mailAddress() == null
                ? new PostalAddress()
                : PostalAddress.of(request.mailAddress()));
        entity.getEmailAddress().clear();
        if (request.emailAddress() != null) {
            for (String e : request.emailAddress()) {
                entity.getEmailAddress().add(e);
            }
        }
        entity.getPhoneNumber().clear();
        if (request.phoneNumber() != null) {
            for (String p : request.phoneNumber()) {
                entity.getPhoneNumber().add(PhoneNumber.of(p));
            }
        }
        entity.setWebAddress(request.webAddress());

        RegistrationAuthorityIdentifier rai = entity.getRegistrationAuthorityIdentifier();
        if (rai == null) rai = new RegistrationAuthorityIdentifier();
        rai.setInternationalCodeDesignator(request.internationalCodeDesignator());
        rai.setOrganizationIdentifier(request.organizationIdentifier());
        rai.setOrganizationPartIdentifier(request.organizationPartIdentifier());
        rai.setOpiSource(request.opiSource());
        entity.setRegistrationAuthorityIdentifier(rai);

        Namespace ns = request.namespaceId() == null ? null
                : namespaceRepository.findById(request.namespaceId())
                .orElseThrow(() -> new ResourceNotFoundException("Namespace", request.namespaceId()));
        entity.setNamespace(ns);

        entity.getDocumentationLanguageIdentifier().clear();
        if (request.documentationLanguageIds() != null) {
            for (UUID langId : request.documentationLanguageIds()) {
                LanguageIdentification lang = languageRepository.findById(langId)
                        .orElseThrow(() -> new ResourceNotFoundException("LanguageIdentification", langId));
                entity.getDocumentationLanguageIdentifier().add(lang);
            }
        }
    }

    private RegistrationAuthorityResponse toResponse(RegistrationAuthority entity) {
        RegistrationAuthorityIdentifier rai = entity.getRegistrationAuthorityIdentifier();
        Namespace ns = entity.getNamespace();
        List<UUID> langIds = new ArrayList<>();
        List<String> langCodes = new ArrayList<>();
        for (LanguageIdentification lang : entity.getDocumentationLanguageIdentifier()) {
            langIds.add(lang.getId());
            langCodes.add(lang.getLanguageIdentifier());
        }
        List<String> names = entity.getName().stream().map(Sign::getValue).toList();
        String name = names.isEmpty() ? null : names.get(0);
        String englishName = names.size() > 1 ? names.get(1) : null;
        return new RegistrationAuthorityResponse(
                entity.getId(),
                name,
                englishName,
                entity.getMailAddress() == null ? null : entity.getMailAddress().getValue(),
                entity.getEmailAddress().stream().toList(),
                entity.getPhoneNumber().stream().map(PhoneNumber::getValue).toList(),
                entity.getWebAddress(),
                rai == null ? null : rai.getInternationalCodeDesignator(),
                rai == null ? null : rai.getOrganizationIdentifier(),
                rai == null ? null : rai.getOrganizationPartIdentifier(),
                rai == null ? null : rai.getOpiSource(),
                ns == null ? null : ns.getId(),
                ns == null ? null : ItemNamingHelper.preferredDesignation(ns),
                langIds,
                langCodes);
    }
}
