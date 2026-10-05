package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.web.edition.DeploymentEdition;
import io.github.matrixlane.metadataregistry.web.entity.SystemSetting;
import io.github.matrixlane.metadataregistry.web.repository.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class SystemSettingService {

    private final SystemSettingRepository repository;

    private static final String KEY_INITIALIZED = "system.initialized";
    private static final String KEY_EDITION = "system.edition";
    private static final String KEY_NEW_USER_REQUIRE_AUDIT = "system.new_user_require_audit";
    private static final String KEY_ALLOW_USER_REGISTRATION = "system.allow_user_registration";

    @Transactional(readOnly = true)
    public boolean isInitialized() {
        return repository.findById(KEY_INITIALIZED)
                .map(setting -> "true".equalsIgnoreCase(setting.getValue()))
                .orElse(false);
    }

    public void setInitialized(boolean initialized) {
        SystemSetting setting = new SystemSetting(KEY_INITIALIZED, String.valueOf(initialized));
        repository.save(setting);
    }

    /**
     * 当前部署版本；未初始化（或旧部署未写入）时返回 null。
     */
    @Transactional(readOnly = true)
    public DeploymentEdition getEdition() {
        return repository.findById(KEY_EDITION)
                .map(setting -> DeploymentEdition.valueOf(setting.getValue()))
                .orElse(null);
    }

    /**
     * 写入部署版本（初始化向导 setup 阶段的草稿语义；commit 后随系统一起生效）。
     */
    public void setEdition(DeploymentEdition edition) {
        SystemSetting setting = new SystemSetting(KEY_EDITION, edition.name());
        repository.save(setting);
    }

    /**
     * 清除部署版本草稿（初始化向导 abort 时调用）。
     */
    public void clearEdition() {
        repository.deleteById(KEY_EDITION);
    }

    @Transactional(readOnly = true)
    public boolean isNewUserRequireAudit() {
        return repository.findById(KEY_NEW_USER_REQUIRE_AUDIT)
                .map(setting -> "true".equalsIgnoreCase(setting.getValue()))
                .orElse(false);
    }

    public void setNewUserRequireAudit(boolean requireAudit) {
        SystemSetting setting = new SystemSetting(KEY_NEW_USER_REQUIRE_AUDIT, String.valueOf(requireAudit));
        repository.save(setting);
    }

    @Transactional(readOnly = true)
    public boolean isAllowUserRegistration() {
        return repository.findById(KEY_ALLOW_USER_REGISTRATION)
                .map(setting -> "true".equalsIgnoreCase(setting.getValue()))
                .orElse(true);
    }

    public void setAllowUserRegistration(boolean allowRegistration) {
        SystemSetting setting = new SystemSetting(KEY_ALLOW_USER_REGISTRATION, String.valueOf(allowRegistration));
        repository.save(setting);
    }
}
