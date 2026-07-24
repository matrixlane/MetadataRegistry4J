package io.github.matrixlane.metadataregistry.web.service;

import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
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
    private static final String KEY_RUNNING_MODE = "system.running_mode";
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

    @Transactional(readOnly = true)
    public String getRunningMode() {
        return repository.findById(KEY_RUNNING_MODE)
                .map(SystemSetting::getValue)
                .orElse("SINGLE");
    }

    public void setRunningMode(String mode) {
        if (mode == null) {
            repository.deleteById(KEY_RUNNING_MODE);
        } else {
            SystemSetting setting = new SystemSetting(KEY_RUNNING_MODE, mode);
            repository.save(setting);
        }
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

    /**
     * 切换注册表治理策略（SINGLE/MULTI）。仅团队版允许；个人版强制 SINGLE。
     */
    public void setRegistryPolicy(String mode) {
        if (getEdition() == DeploymentEdition.PERSONAL) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "个人版不支持修改注册表治理策略");
        }
        if (!"SINGLE".equalsIgnoreCase(mode) && !"MULTI".equalsIgnoreCase(mode)) {
            throw new BusinessException(ApiCodes.VALIDATION_FAILED, "无效的治理策略: " + mode + "（仅支持 SINGLE/MULTI）");
        }
        setRunningMode(mode.toUpperCase());
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
