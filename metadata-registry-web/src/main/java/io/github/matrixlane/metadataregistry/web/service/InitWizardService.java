package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.EmailAddress;
import io.github.matrixlane.metadataregistry.commonfacilities.identification.Namespace;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrationAuthority;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.RegistrySpecification;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import io.github.matrixlane.metadataregistry.web.auth.AccountRepository;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.RegistrationAuthorityIdentifier;
import io.github.matrixlane.metadataregistry.web.dto.initwizard.InitWizardCommitRequest;
import io.github.matrixlane.metadataregistry.web.dto.initwizard.InitWizardSetupRequest;
import io.github.matrixlane.metadataregistry.web.dto.initwizard.InitWizardStatusResponse;
import io.github.matrixlane.metadataregistry.web.edition.DeploymentEdition;
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
    private final RegistrySpecificationService registrySpecificationService;
    private final LanguageInitializer languageInitializer;
    private final Optional<SystemDefaultNamespaceInitializer> systemDefaultNamespaceInitializer;
    private final PasswordEncoder passwordEncoder;
    private final DatabaseCleaner databaseCleaner;

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

        // 版本驱动的有效配置：PERSONAL 精简为默认值；TEAM 使用表单值并校验必填。
        String raName = request.registrationAuthorityName();
        boolean selfAsRegistrar = Boolean.TRUE.equals(request.selfAsRegistrar());
        boolean createNamespace = Boolean.TRUE.equals(request.createNamespace());
        boolean createSpec = Boolean.TRUE.equals(request.createRegistrySpecification());
        String registryName = request.registryName();
        if (edition == DeploymentEdition.PERSONAL) {
            if (raName == null || raName.isBlank()) {
                raName = "个人注册机构";
            }
            selfAsRegistrar = true;
            createNamespace = true;
            createSpec = true;
            if (registryName == null || registryName.isBlank()) {
                registryName = "个人元数据注册表规范";
            }
        } else if (raName == null || raName.isBlank()) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "注册机构名称不能为空");
        }

        // 1. Create RegistrationAuthority
        RegistrationAuthority ra = new RegistrationAuthority();
        ra.getName().add(Sign.of(raName));
        if (request.registrationAuthorityEnglishName() != null && !request.registrationAuthorityEnglishName().isBlank()) {
            ra.getName().add(Sign.of(request.registrationAuthorityEnglishName()));
        }
        if (request.registrationAuthorityEmail() != null && !request.registrationAuthorityEmail().isBlank()) {
            ra.getEmailAddress().add(EmailAddress.of(request.registrationAuthorityEmail()));
        }

        RegistrationAuthorityIdentifier rai = new RegistrationAuthorityIdentifier();
        rai.setOrganizationIdentifier(request.identifierPrefix() != null && !request.identifierPrefix().isBlank()
                ? request.identifierPrefix()
                : "RA");
        ra.setRegistrationAuthorityIdentifier(rai);

        ra = registrationAuthorityRepository.save(ra);

        // 2. Assign self as registrar if checked
        if (selfAsRegistrar) {
            registrarService.createRegistrarForUser(
                    account.getId(),
                    account.getUsername(),
                    null,
                    null,
                    ra
            );
        }

        // 3. Create default namespace if checked
        if (createNamespace) {
            Namespace ns = new Namespace();

            io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Designation d = new io.github.matrixlane.metadataregistry.commonfacilities.designationanddefinition.Designation();
            d.setItem(ns);
            d.setSign(Sign.of("默认命名空间"));
            ns.getDesignations().add(d);

            ns.setRegistrationAuthority(ra);
            ns.setNamingAuthority(ra);

            Namespace nsSaved = namespaceRepository.save(ns);

            ra.setNamespace(nsSaved);
            registrationAuthorityRepository.save(ra);
        }

        // 3.5 Create registry specification if checked
        if (createSpec) {
            registrySpecificationService.createDefault(
                    registryName,
                    request.identifierPrefix(),
                    request.autoGenerateIdentifier(),
                    request.identifierPattern(),
                    request.versioningPolicy(),
                    request.mandatoryClassification(),
                    request.defaultLanguage(),
                    request.registryDescription());
        }

        // 3.6 Initialize languages and system default namespace
        languageInitializer.initializeLanguages();
        systemDefaultNamespaceInitializer.ifPresent(SystemDefaultNamespaceInitializer::initializeSystemDefaultNamespace);

        // NOTE: system.initialized stays FALSE; admin password is NOT reset
        // here. Those happen in commit().
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
        if (request.newPassword().length() < 6 || request.newPassword().length() > 20) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "密码长度需在 6 到 20 个字符之间");
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
