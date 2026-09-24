package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.ext.identification.IdentifierStrategyConfig;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrySpecification;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import io.github.matrixlane.metadataregistry.web.auth.AccountRepository;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.RegistrationAuthorityIdentifier;
import io.github.matrixlane.metadataregistry.web.config.IdentifierStrategyLoader;
import io.github.matrixlane.metadataregistry.web.dto.initwizard.InitWizardCommitRequest;
import io.github.matrixlane.metadataregistry.web.dto.initwizard.InitWizardSetupRequest;
import io.github.matrixlane.metadataregistry.web.dto.initwizard.InitWizardSpecAttributes;
import io.github.matrixlane.metadataregistry.web.dto.initwizard.InitWizardStatusResponse;
import io.github.matrixlane.metadataregistry.web.edition.DeploymentEdition;
import io.github.matrixlane.metadataregistry.web.repository.IdentifierStrategyConfigRepository;
import io.github.matrixlane.metadataregistry.web.repository.NamespaceRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrationAuthorityRepository;
import io.github.matrixlane.metadataregistry.web.seed.LanguageInitializer;
import io.github.matrixlane.metadataregistry.web.seed.SystemDefaultNamespaceInitializer;
import io.github.matrixlane.metadataregistry.web.service.database.DatabaseCleaner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 5-step transactional init-wizard service.
 *
 * Step 1-4 (running mode → registration authority → admin & namespace → registry
 * spec) is the "setup" phase. The wizard writes business data during setup
 * but does NOT flip {@code system.initialized} — the system is still
 * considered un-initialised until the admin completes step 5 (set admin
 * password) which calls {@link #commit}. Any interruption (page reload,
 * tab switch, manual abort) must be safe to recover from, so the
 * {@link #abort} operation removes all business data written by setup and
 * resets the admin password to {@code NO_PASSWORD}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InitWizardService {

    private final SystemSettingService systemSettingService;
    private final RegistrarService registrarService;
    private final AccountRepository accountRepository;
    private final RegistrationAuthorityRepository registrationAuthorityRepository;
    private final NamespaceRepository namespaceRepository;
    private final IdentifierStrategyConfigRepository identifierStrategyConfigRepository;
    private final IdentifierStrategyLoader identifierStrategyLoader;
    private final RegistrySpecificationService registrySpecificationService;
    private final LanguageInitializer languageInitializer;
    private final Optional<SystemDefaultNamespaceInitializer> systemDefaultNamespaceInitializer;
    private final PasswordEncoder passwordEncoder;
    private final DatabaseCleaner databaseCleaner;
    private final NamespaceIdentifierSettingsService namespaceIdentifierSettingsService;
    private final RegistryContextBootstrapService registryContextBootstrapService;

    @Transactional(readOnly = true)
    public InitWizardStatusResponse getStatus() {
        DeploymentEdition edition = systemSettingService.getEdition();
        return new InitWizardStatusResponse(
                systemSettingService.isInitialized(),
                systemSettingService.getRunningMode(),
                edition == null ? null : edition.name()
        );
    }

    /**
     * Discovers the current state of the wizard. Used by the frontend to
     * decide whether to resume a previous attempt, restart, or skip the
     * wizard entirely.
     *
     *   NOT_STARTED — system is fully initialised
     *   IN_PROGRESS — system is un-initialised and business data exists
     *   READY       — system is un-initialised, no business data yet
     */
    @Transactional(readOnly = true)
    public String getWizardState() {
        if (systemSettingService.isInitialized()) {
            return "NOT_STARTED";
        }
        // We treat "in progress" as: no business RA / namespace / spec yet,
        // but the admin account exists (which it always does after the first
        // DataInitializer run, so this heuristic is "is there any RA row?")
        long raCount = registrationAuthorityRepository.count();
        long nsCount = namespaceRepository.count();
        boolean specExists = registrySpecificationService.exists();
        if (raCount > 0 || nsCount > 0 || specExists) {
            return "IN_PROGRESS";
        }
        return "READY";
    }

    /**
     * Step 1-4: write the wizard's business data. Does NOT flip
     * {@code system.initialized} — that only happens in {@link #commit}.
     *
     * If any prior wizard attempt left dirty data, this method first cleans
     * it up (delegating to {@link #abort}) so that the wizard is idempotent
     * against repeated submissions.
     */
    @Transactional
    public void setup(InitWizardSetupRequest request, UUID appUserId) {
        if (systemSettingService.isInitialized()) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "系统已经完成初始化");
        }

        // 部署版本解析与校验（CLOUD/未知值在此被拒绝）
        DeploymentEdition edition = DeploymentEdition.parse(request.edition());

        // Clear any leftover dirty data from a previous aborted attempt.
        clearBusinessDataOnly();

        // Guarantee commit() preconditions no matter how the system arrived at
        // the uninitialized state. E.g. a factory reset re-creates the admin
        // with a real password hash, which would otherwise make commit()
        // reject every attempt with no way to recover through the wizard.
        resetSuperAdminToNoPassword();

        // 持久化版本草稿；commit 时随 initialized 一起生效，abort 时清除。
        systemSettingService.setEdition(edition);

        Account account = accountRepository.findById(appUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", appUserId));

        // 版本驱动的有效配置：PERSONAL 名称类字段缺省时取默认值，治理布尔开关
        // 缺省（null）按 true 处理以保持向后兼容。命名空间为必选（D7：初始化
        // 必须创建注册机构的自有命名空间），显式 false 返回业务校验错误；
        // 注册规范同为必选。TEAM 使用表单值并校验必填。
        String raName = request.registrationAuthorityName();
        boolean selfAsRegistrar = Boolean.TRUE.equals(request.selfAsRegistrar());
        String registryName = request.registryName();
        // createNamespace 语义改为强制 true：缺省与显式 true 正常执行，显式 false 拒绝
        if (Boolean.FALSE.equals(request.createNamespace())) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "初始化必须创建注册机构的自有命名空间，不能跳过命名空间步骤");
        }
        boolean createNamespace = true;
        // 注册管理员三要素（individualName/roleName/organizationId）的版本差异化默认值
        String adminIndividualName = request.adminIndividualName();
        String adminRoleName = request.adminRoleName();
        java.util.UUID adminOrganizationId = request.adminOrganizationId();
        if (edition == DeploymentEdition.PERSONAL) {
            if (raName == null || raName.isBlank()) {
                // 注册机构(Registration_Authority)是组织机构层级；个人版下默认名为
                // "默认注册机构"。Registrar 是 admin 兼任的注册管理员，名称由绑定流程另赋，
                // 不应与组织机构同名。
                raName = "默认注册机构";
            }
            selfAsRegistrar = true;
            if (registryName == null || registryName.isBlank()) {
                // 注册规范名称：与注册机构名称解耦，使用稳定的默认字面值，避免
                // 跟着 RA 名称拼接成不可控的长串。
                registryName = "默认注册规范";
            }
            // PERSONAL 版三要素默认值：姓名"注册管理员"+角色"注册管理员"+组织 = 新建 RA。
            // 即使用户在前端清空输入，setup 后端 MUST 兜底为"注册管理员"——admin-registrar
            // 行的列表显示由该兜底值决定（spec registrar-identity-elements）。
            if (adminIndividualName == null || adminIndividualName.isBlank()) {
                adminIndividualName = "注册管理员";
            }
            if (adminRoleName == null || adminRoleName.isBlank()) {
                adminRoleName = "注册管理员";
            }
            // adminOrganizationId 在 RA 创建之后回填
        } else if (raName == null || raName.isBlank()) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "注册机构名称不能为空");
        }
        // TEAM 版三要素必填校验（角色+组织必填，姓名允许为空）
        if (edition == DeploymentEdition.TEAM) {
            if (adminRoleName == null || adminRoleName.isBlank()) {
                throw new BusinessException(ApiCodes.VALIDATION_FAILED, "注册管理员角色不能为空");
            }
            if (adminOrganizationId == null) {
                throw new BusinessException(ApiCodes.VALIDATION_FAILED, "注册管理员所属组织不能为空");
            }
        }
        // 注册规范为数据治理先决条件，两个版本均必选：向导不再设独立配置步骤，
        // 由系统在 setup 时自动创建；显式 false 视为契约违规，仍被拒绝。
        if (Boolean.FALSE.equals(request.createRegistrySpecification())) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "注册规范为必选项，不能跳过创建");
        }

        // 1. Create RegistrationAuthority
        RegistrationAuthority ra = new RegistrationAuthority();
        ra.getName().add(Sign.of(raName));
        if (request.registrationAuthorityEnglishName() != null && !request.registrationAuthorityEnglishName().isBlank()) {
            ra.getName().add(Sign.of(request.registrationAuthorityEnglishName()));
        }
        if (request.registrationAuthorityEmail() != null && !request.registrationAuthorityEmail().isBlank()) {
            ra.getEmailAddress().add(request.registrationAuthorityEmail());
        }

        RegistrationAuthorityIdentifier rai = new RegistrationAuthorityIdentifier();
        // 表 8:international_code_designator 必备。占位值 "0000"(ISO/IEC 6523 未分配段),
        // 部署方应在注册机构设置中改为本机构真实 ICD。
        rai.setInternationalCodeDesignator("0000");
        rai.setOrganizationIdentifier("RA");
        ra.setRegistrationAuthorityIdentifier(rai);

        ra = registrationAuthorityRepository.save(ra);

        // 2. Assign self as registrar if checked
        if (selfAsRegistrar) {
            // PERSONAL 版下 adminOrganizationId 缺省回退到新建 RA（其 id 已写入 ra）
            java.util.UUID resolvedAdminOrgId = adminOrganizationId != null ? adminOrganizationId : ra.getId();
            registrarService.createRegistrarForUser(
                    account.getId(),
                    account.getUsername(),
                    adminIndividualName,
                    adminRoleName,
                    resolvedAdminOrgId,
                    ra
            );
        }

        // 3.5 Initialize languages before creating registry specification,
        // because the default specification needs a valid languageId.
        languageInitializer.initializeLanguages();

        // 3. Create default namespace if checked
        if (createNamespace) {
            Namespace ns = new Namespace();

            String namespaceName = request.namespaceName() != null && !request.namespaceName().isBlank()
                    ? request.namespaceName() : "默认命名空间";
            io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Designation d = new io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Designation();
            d.setItem(ns);
            d.setSign(Sign.of(namespaceName));
            ns.getDesignations().add(d);

            ns.setRegistrationAuthority(ra);
            ns.setNamingAuthority(ra);
            String versioningPolicy = request.versionPolicy() != null && !request.versionPolicy().isBlank()
                    ? request.versionPolicy() : "SEMVER";
            String identifierInputMode = normalizeInputMode(request.identifierInputMode());
            String versionInputMode = normalizeInputMode(request.versionInputMode());

            Namespace nsSaved = namespaceRepository.save(ns);
            // 标识符治理扩展属性写入卫星实体(标准实体 Namespace 不再持有)
            namespaceIdentifierSettingsService.mutate(nsSaved.getId(), settings -> {
                settings.setVersioningPolicy(versioningPolicy);
                settings.setIdentifierInputMode(identifierInputMode);
                settings.setVersionInputMode(versionInputMode);
            });

            // MANUAL_ONLY 命名空间禁用自动生成，不创建策略配置
            if (!"MANUAL_ONLY".equals(identifierInputMode)) {
                createNamespaceStrategyConfig(nsSaved, request);
            }

            ra.setNamespace(nsSaved);
            registrationAuthorityRepository.save(ra);
        }

        // 3.5 注册规范（必选，注册表固有对象）：向导步骤可携带表 28 全量标量属性，
        // 缺省时由 createDefault 按系统默认值兜底（字符集 UTF-8、标准
        // ISO/IEC 11179-3:2023、时区 +0800、默认语言 zh-CN）。
        InitWizardSpecAttributes specAttrs = request.registrySpecificationAttributes();
        registrySpecificationService.createDefault(
                registryName,
                request.defaultLanguage() != null ? request.defaultLanguage() : "zh-CN",
                request.registryDescription(),
                specAttrs != null ? specAttrs.webAddress() : null,
                specAttrs != null ? specAttrs.characterRepertoire() : null,
                specAttrs != null ? specAttrs.standard() : null,
                specAttrs != null ? specAttrs.degreeOfConformance() : null,
                specAttrs != null ? specAttrs.conformanceLevel() : null,
                specAttrs != null ? specAttrs.conformanceLabel() : null,
                specAttrs != null ? specAttrs.includedFeatures() : null,
                specAttrs != null ? specAttrs.excludedFeatures() : null,
                specAttrs != null ? specAttrs.defaultTimeZone() : null,
                specAttrs != null ? specAttrs.alternativeLanguages() : null,
                specAttrs != null ? specAttrs.referenceDocumentIdentifierKinds() : null,
                specAttrs != null ? specAttrs.representationClassSchemeId() : null);

        // 3.6 Initialize system default namespace
        systemDefaultNamespaceInitializer.ifPresent(SystemDefaultNamespaceInitializer::initializeSystemDefaultNamespace);

        // NOTE: system.initialized stays FALSE; admin password is NOT reset
        // here. Those happen in commit().
    }

    /**
    * 为初始化创建的默认命名空间生成命名空间级标识生成策略配置
     * （PUBLISHED、version=1、即生效），并刷新策略注册表使其立即可用。
     */
    private void createNamespaceStrategyConfig(Namespace ns, InitWizardSetupRequest request) {
        String strategy = request.identifierStrategy() == null || request.identifierStrategy().isBlank()
                ? "SEGMENT_TEMPLATE" : request.identifierStrategy().trim().toUpperCase();
        // 标识生成策略收敛为段模板唯一选择；UUID/顺序号/旧模板等旧值一律拒绝
        if (!"SEGMENT_TEMPLATE".equals(strategy)) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "标识生成策略仅支持段模板（SEGMENT_TEMPLATE），不支持的策略值: " + strategy);
        }
        String raw = request.identifierTemplate();
        String configJson = raw == null || raw.isBlank()
                ? defaultSegmentTemplateJson() : raw.trim();
        validateSegmentTemplate(configJson);
        IdentifierStrategyConfig config = new IdentifierStrategyConfig(strategy);
        config.setNamespace(ns);
        config.setConfigJson(configJson);
        config.setVersion(1);
        config.setIsGlobalDefault(false);
        config.setCreatedAt(java.time.LocalDateTime.now());
        config.setCreatedBy("init-wizard");
        config.publish();
        identifierStrategyConfigRepository.save(config);
        identifierStrategyLoader.refreshRegistry();
    }

    /**
     * 向导默认段模板：对象缩写 + 分隔符 + 顺序号（示例 DE.00001）；
     * 顺序号作用域自动按对象类型区分。
     */
    private static String defaultSegmentTemplateJson() {
        return """
                {"segments":[
                  {"type":"OBJECT_ABBREVIATION","name":"objectAbbr","params":{}},
                  {"type":"SEPARATOR","params":{"char":"."}},
                  {"type":"SEQUENCE","name":"seq","params":{"start":1,"increment":1,"pad":5,"overflow":"REJECT"}}
                ]}""";
    }

    private static void validateSegmentTemplate(String templateJson) {
        try {
            io.github.matrixlane.metadataregistry.ext.identification.segment.SegmentTemplate template =
                    io.github.matrixlane.metadataregistry.ext.identification.segment.SegmentTemplate.fromJson(templateJson);
            java.util.List<String> errors =
                    new io.github.matrixlane.metadataregistry.ext.identification.segment.SegmentTemplateEngine()
                            .validate(template);
            if (!errors.isEmpty()) {
                throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                        "段模板校验失败：" + String.join("；", errors));
            }
        } catch (io.github.matrixlane.metadataregistry.ext.identification.segment.SegmentTemplateException e) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "段模板解析失败：" + e.getMessage());
        }
    }

    /**
     * 归一化输入模式值：空白或非法值回退 AUTO_WITH_OVERRIDE（兼容缺省调用方）。
     */
    private static String normalizeInputMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return "AUTO_WITH_OVERRIDE";
        }
        String normalized = raw.trim().toUpperCase();
        return java.util.Set.of("AUTO_ONLY", "AUTO_WITH_OVERRIDE", "MANUAL_ONLY").contains(normalized)
                ? normalized : "AUTO_WITH_OVERRIDE";
    }

    /**
     * Step 5: the final commit. Sets {@code system.initialized=true} and
     * persists the admin's real password. This is the only operation that
     * can take the system out of "un-initialised" state.
     */
    @Transactional
    public void commit(InitWizardCommitRequest request) {
        if (systemSettingService.isInitialized()) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "系统已经完成初始化");
        }
        DeploymentEdition edition = systemSettingService.getEdition();
        if (edition == null) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "请先完成初始化向导配置（setup）");
        }
        if (request == null || request.newPassword() == null || request.newPassword().isBlank()) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "新密码不能为空");
        }
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "两次输入的密码不一致");
        }
        if (request.newPassword().length() < 8 || request.newPassword().length() > 64) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "密码长度需在 8-64 个字符之间");
        }

        // The admin must currently be in the "NO_PASSWORD" state, since
        // setup() did not reset it. This guards against a stray commit call
        // after a normal admin session.
        UUID adminId = accountRepository.findAll().stream()
                .filter(a -> a.getSysRole() == io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole.SUPER_ADMIN)
                .findFirst()
                .orElseThrow(() -> new BusinessException("找不到超级管理员账户"))
                .getId();
        Account admin = accountRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", adminId));
        if (!"NO_PASSWORD".equals(admin.getPasswordHash())) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED,
                    "管理员当前不在 NO_PASSWORD 状态，请先通过 setup 重新发起向导");
        }

        // Persist the real password and flip the system flag. This is the
        // point of no return — after this, abort() is no longer valid.
        admin.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        admin.setForcePasswordReset(false);
        admin.setTempPasswordUsed(false);
        admin.setLastLoginAt(null); // fresh start
        accountRepository.save(admin);

        // PERSONAL 强制 SINGLE 治理策略；TEAM 默认 SINGLE（可在系统设置中切换 MULTI）。
        systemSettingService.setRunningMode("SINGLE");
        systemSettingService.setInitialized(true);
    }

    /**
     * 初始化向导完成后创建/回填 Registry Context（注册系统默认上下文，语境自指）。
     *
     * <p>必须在 {@link #commit} 事务提交之后、无外层事务时调用（Controller 组合调用）：
     * Registry Context 的创建需要独立事务——SQLite 连接池为单连接，嵌套事务必然
     * 死锁；且在向导事务内创建既对独立事务不可见（setup 数据未提交），失败又会
     * 把外层事务标记为 rollback-only。失败仅 log warn，不阻断初始化；重启后由
     * 启动补建路径兜底。
     */
    public void createRegistryContextAfterInitialization() {
        try {
            registrationAuthorityRepository.findAll().stream()
                    .findFirst()
                    .ifPresentOrElse(
                            registryContextBootstrapService::ensureRegistryContext,
                            () -> log.warn("Registry Context 创建跳过：系统中不存在注册机构"));
        } catch (Exception e) {
            log.warn("Registry Context 创建失败，可在重启后自动补建: {}", e.getMessage(), e);
        }
    }

    /**
     * Abort the wizard. Removes all business data written by setup() but
     * preserves the admin account (reset to NO_PASSWORD). Safe to call
     * multiple times.
     */
    @Transactional
    public void abort() {
        if (systemSettingService.isInitialized()) {
            // Already initialised — nothing to abort.
            return;
        }
        clearBusinessDataOnly();
        // 清除版本草稿，回到完全未初始化状态。
        systemSettingService.clearEdition();
        // Reset admin back to NO_PASSWORD.
        resetSuperAdminToNoPassword();
    }

    /**
     * Reset the SUPER_ADMIN account back to the NO_PASSWORD bootstrap state.
     * Used by both {@link #setup} (to guarantee commit preconditions) and
     * {@link #abort} (the user-driven interrupt path).
     */
    private void resetSuperAdminToNoPassword() {
        accountRepository.findAll().stream()
                .filter(a -> a.getSysRole() == io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole.SUPER_ADMIN)
                .forEach(admin -> {
                    admin.setPasswordHash("NO_PASSWORD");
                    admin.setForcePasswordReset(false);
                    admin.setTempPasswordUsed(false);
                    admin.setLastLoginAt(null);
                    accountRepository.save(admin);
                });
    }

    /**
     * Remove business data created by setup() while preserving the admin
     * account and the Flyway schema history. Used by both {@link #setup}
     * (to clear dirty state from previous aborted attempts) and
     * {@link #abort} (the user-driven interrupt path).
     */
    private void clearBusinessDataOnly() {
        // Dialect-portable cleanup: table discovery via JDBC metadata and
        // FK handling via the active vendor's strategy (see DatabaseCleaner).
        databaseCleaner.clearTables(Set.of("account", "system_setting", "flyway_schema_history"));
    }
}
