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
import io.github.matrixlane.metadataregistry.ext.registration.RegistrationAuthorityExtension;
import io.github.matrixlane.metadataregistry.web.auth.AccountRepository;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.web.dto.registrationauthority.RegistrationAuthorityRequest;
import io.github.matrixlane.metadataregistry.web.dto.registrationauthority.RegistrationAuthorityResponse;
import io.github.matrixlane.metadataregistry.web.edition.DeploymentEdition;
import io.github.matrixlane.metadataregistry.web.repository.LanguageIdentificationRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityExtensionRepository;
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
    private final ContactAccountService contactAccountService;
    private final AccountRepository accountRepository;
    private final RegistrationAuthorityDeletionService deletionService;
    private final NamespaceProvisioningService namespaceProvisioningService;
    private final RegistrationAuthorityExtensionRepository registrationAuthorityExtensionRepository;

    /**
     * 注册机构列表，关键词按名称检索。
     *
     * <p>维持仅名称匹配：RegistrationAuthority 继承 Organization，而 Organization 是
     * 独立根实体（{@code Organization extends Entity}），不在受管链上，
     * 不存在 Scoped_Identifier 行。其 RegistrationAuthorityIdentifier 是 ISO/IEC 11179-3
     * 的机构编码分段（ICD/OI/OP），不是受管链上的业务标识符，故不参与关键词匹配。</p>
     */
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
     * 新增注册机构（版本化治理 + 角色门槛）。
     *
     * <p>角色门槛（M4）：创建注册机构要求调用者为 SUPER_ADMIN（不分版本）；
     * SUBMITTER/REGISTRAR 业务角色一律拒绝。</p>
     *
     * <p>注册人员：PERSONAL 个人版创建成功后自动为当前账号建立该机构的
     * Registrar（忽略请求中的 selfAsRegistrar 值）；TEAM 团队版（或版本未设置，
     * 保守按 TEAM）仅当 {@code selfAsRegistrar=true} 时为当前账号建立 Registrar，
     * false/缺省不建立。两种路径均为当前账号首个身份时标为默认。</p>
     *
     * <p>自有命名空间（ra-creation-wizard）：{@code createNamespace=true} 时在同一
     * 事务内调用 {@link NamespaceProvisioningService#provisionOwnNamespace} 开通命名
     * 空间并回挂 {@code ra.namespace}。语义与初始化向导一致：名称缺省"默认命名空间"、
     * 输入模式缺省 AUTO_WITH_OVERRIDE、版本策略缺省 SEMVER、MANUAL_ONLY 不建策略配置。</p>
     *
     * <p>系统不存在"单注册表/多注册表"运行模式概念，任何版本均无数量上限。</p>
     */
    public RegistrationAuthorityResponse create(RegistrationAuthorityRequest request,
                                                UUID accountId, SystemRole role) {
        if (role != SystemRole.SUPER_ADMIN) {
            throw new BusinessException(ApiCodes.FORBIDDEN, "仅超级管理员可创建注册机构");
        }
        DeploymentEdition edition = systemSettingService.getEdition();
        boolean personal = edition == DeploymentEdition.PERSONAL;
        RegistrationAuthority entity = new RegistrationAuthority();
        applyRequest(entity, request);
        RegistrationAuthority saved = repository.save(entity);
        // registration-process-designer：新注册机构创建即幂等播种标准注册流程规则
        registrationProcessService.ensureSeed(saved.getId());
        if (personal) {
            autoCreateRegistrar(saved, accountId);
        } else if (Boolean.TRUE.equals(request.selfAsRegistrar())) {
            // TEAM 团队版（含版本未设置，保守按 TEAM）：仅显式 selfAsRegistrar=true 时为
            // 当前账号建立该机构的 Registrar；false/缺省不建立。
            autoCreateRegistrar(saved, accountId);
        }
        if (Boolean.TRUE.equals(request.createNamespace())) {
            // ra-creation-wizard：同事务开通自有命名空间并回挂 ra.namespace
            Namespace ns = namespaceProvisioningService.provisionOwnNamespace(
                    saved,
                    request.namespaceName(),
                    request.versionPolicy(),
                    NamespaceProvisioningService.normalizeInputMode(request.identifierInputMode()),
                    NamespaceProvisioningService.normalizeInputMode(request.versionInputMode()),
                    request.identifierTemplate());
            saved.setNamespace(ns);
            repository.save(saved);
        }
        return toResponse(saved);
    }

    /**
     * 为当前账号建立该机构的 Registrar（roleName 固定"注册管理员"）；账号尚无默认
     * 注册人员时，该 Registrar 自动成为默认（首个身份自动默认语义）。
     * PERSONAL 个人版创建后无条件调用；TEAM 团队版（含版本未设置）仅 selfAsRegistrar=true 时调用。
     */
    private void autoCreateRegistrar(RegistrationAuthority ra, UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        Registrar registrar = registrarService.createRegistrarForUser(
                accountId, account.getUsername(), null, "注册管理员", ra.getId(), ra);
        contactAccountService.ensureDefaultRegistrarWhenFirst(accountId, registrar.getId());
    }

    /**
     * 更新注册机构（M4 角色门槛：仅 SUPER_ADMIN）。
     */
    public RegistrationAuthorityResponse update(UUID id, RegistrationAuthorityRequest request, SystemRole role) {
        if (role != SystemRole.SUPER_ADMIN) {
            throw new BusinessException(ApiCodes.FORBIDDEN, "仅超级管理员可更新注册机构");
        }
        RegistrationAuthority entity = loadOrThrow(id);
        applyRequest(entity, request);
        return toResponse(repository.save(entity));
    }

    /**
     * 删除注册机构（版本化治理 + 级联删除）。
     *
     * <p>删除保护仅按版本判定（M1）：PERSONAL 个人版主要注册机构（Primary，
     * 存储于 ext 卫星表）不允许删除，其余一律放行（系统不存在运行模式概念，
     * 删除无任何模式门槛）；TEAM 团队版（或版本未设置，保守按 TEAM）无 Primary
     * 保护，任何机构可删。级联删除经 {@link RegistrationAuthorityDeletionService} 完成。</p>
     *
     * <p>角色门槛（M4）：仅 SUPER_ADMIN 可删除；SUBMITTER/REGISTRAR 业务角色拒绝。</p>
     */
    public void delete(UUID id, SystemRole role) {
        if (role != SystemRole.SUPER_ADMIN) {
            throw new BusinessException(ApiCodes.FORBIDDEN, "仅超级管理员可删除注册机构");
        }
        RegistrationAuthority entity = loadOrThrow(id);
        DeploymentEdition edition = systemSettingService.getEdition();
        if (edition == DeploymentEdition.PERSONAL && isPrimary(id)) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "主要注册机构不允许删除");
        }
        deletionService.delete(id);
    }

    /**
     * Primary 隐式标志现存储于 ext 卫星表（{@code ext.registration_authority}，
     * 与 mdr.registration_authority 按 id 一一对应）；无卫星行按 false 处理。
     */
    private boolean isPrimary(UUID id) {
        return registrationAuthorityExtensionRepository.findById(id)
                .map(RegistrationAuthorityExtension::isPrimary)
                .orElse(false);
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
        // m6：deletable 间接信号——个人版 Primary 机构不可删（Primary 存于 ext 卫星表，
        // 不进 DTO）；版本未知按个人版保守。团队版无 Primary 删除保护。
        DeploymentEdition edition = systemSettingService.getEdition();
        boolean personal = edition != DeploymentEdition.TEAM;
        boolean deletable = !(personal && isPrimary(entity.getId()));
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
                langCodes,
                deletable);
    }
}
