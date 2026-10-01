package io.github.matrixlane.metadataregistry.web.auth;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Contact;
import io.github.matrixlane.metadataregistry.web.auth.dto.AccountCreateRequest;
import io.github.matrixlane.metadataregistry.web.auth.dto.AccountUpdateRequest;
import io.github.matrixlane.metadataregistry.web.auth.dto.ChangePasswordRequest;
import io.github.matrixlane.metadataregistry.web.auth.dto.LoginRequest;
import io.github.matrixlane.metadataregistry.web.auth.dto.LoginResponse;
import io.github.matrixlane.metadataregistry.web.auth.dto.AccountProfileResponse;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import io.github.matrixlane.metadataregistry.commonfacilities.registration.Registrar;
import io.github.matrixlane.metadataregistry.web.repository.ContactRepository;
import io.github.matrixlane.metadataregistry.web.repository.RegistrarRepository;
import io.github.matrixlane.metadataregistry.web.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import io.github.matrixlane.metadataregistry.web.dto.individual.IndividualRequest;
import io.github.matrixlane.metadataregistry.web.dto.individual.IndividualResponse;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Individual;
import io.github.matrixlane.metadataregistry.web.repository.IndividualRepository;

@Slf4j
@Service("authService")
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final AccountRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final IndividualRepository individualRepository;
    private final ContactRepository contactRepository;
    private final RegistrarRepository registrarRepository;
    private final io.github.matrixlane.metadataregistry.web.service.ContactAccountBindingService contactAccountBindingService;
    private final SystemSettingService systemSettingService;

    private static final String EMERGENCY_CODE_FILE = "emergency-code.txt";

    @PostConstruct
    public void initEmergencyCode() {
        File file = new File(EMERGENCY_CODE_FILE);
        if (!file.exists()) {
            generateAndSaveEmergencyCode();
        }
    }

    private synchronized String generateAndSaveEmergencyCode() {
        String code = generateRandomCode(10);
        try {
            Files.writeString(Paths.get(EMERGENCY_CODE_FILE), code);
            log.info("已生成超级管理员应急登录码，并写入本地文件 '{}'。请妥善保存该码以备不时之需。", EMERGENCY_CODE_FILE);
        } catch (IOException e) {
            log.error("生成或写入应急登录码文件失败", e);
        }
        return code;
    }

    private String generateRandomCode(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private String readEmergencyCode() {
        try {
            File file = new File(EMERGENCY_CODE_FILE);
            if (file.exists()) {
                return Files.readString(Paths.get(EMERGENCY_CODE_FILE)).trim();
            }
        } catch (IOException e) {
            log.error("读取应急登录码文件失败", e);
        }
        return null;
    }

    public LoginResponse login(LoginRequest request) {
        Account user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new BadCredentialsException("用户名或密码错误"));
        if (!user.isEnabled()) {
            throw new BusinessException("账号已被禁用，请联系管理员");
        }

        boolean forceChangePassword = false;
        boolean emergencyLogin = false;

        // Check if admin is logging in for the first time (passwordless)
        boolean isFirstLoginAdmin = "admin".equals(user.getUsername()) && "NO_PASSWORD".equals(user.getPasswordHash());

        // Check if admin is logging in using emergency code
        boolean isEmergencyLogin = false;
        if ("admin".equals(user.getUsername())) {
            String emergencyCode = readEmergencyCode();
            if (emergencyCode != null && emergencyCode.equals(request.password())) {
                isEmergencyLogin = true;
            }
        }

        if (isFirstLoginAdmin) {
            forceChangePassword = true;
            user.setForcePasswordReset(true);
        } else if (isEmergencyLogin) {
            forceChangePassword = true;
            emergencyLogin = true;
            user.setForcePasswordReset(true);
            generateAndSaveEmergencyCode();
        } else {
            String dbHash = user.getPasswordHash();
            if (dbHash.startsWith("USED:")) {
                throw new BadCredentialsException("临时密码已失效，请联系管理员重新重置");
            }
            
            String realHash = dbHash;
            boolean isTemp = dbHash.startsWith("TEMP:");
            if (isTemp) {
                realHash = dbHash.substring(5);
            }

            if (!passwordEncoder.matches(request.password(), realHash)) {
                throw new BadCredentialsException("用户名或密码错误");
            }

            if (isTemp) {
                // Mark as USED so they cannot login again with it
                user.setPasswordHash("USED:" + realHash);
                user.setForcePasswordReset(true);
                user.setTempPasswordUsed(true);
                userRepository.save(user);
            }
            
            if (user.isForcePasswordReset()) {
                forceChangePassword = true;
            }
        }

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        
        String token = jwtService.generateToken(user, forceChangePassword, emergencyLogin,
                user.isTempPasswordUsed(), isRegistrar(user), isSubmitter(user));
        return LoginResponse.of(token, jwtService.expiresInSeconds(), toProfile(user, forceChangePassword, emergencyLogin, user.isTempPasswordUsed()));
    }

    @Transactional(readOnly = true)
    public AccountProfileResponse profile(UUID userId) {
        Account user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", userId));
        return toProfile(user);
    }

    /**
     * Look up the unique SUPER_ADMIN account. The system has exactly one
     * (created by {@code DataInitializer} on first run and re-seeded by
     * {@code FactoryResetService}), so this is unambiguous.
     *
     * Used by {@code InitWizardController.setup} — which must be callable
     * without an authenticated user (the wizard is public) — to find the
     * admin to attach the wizard's setup work to.
     */
    @Transactional(readOnly = true)
    public UUID findAdminId() {
        return userRepository.findAll().stream()
                .filter(a -> a.getSysRole() == SystemRole.SUPER_ADMIN)
                .findFirst()
                .orElseThrow(() -> new BusinessException("找不到超级管理员账户，请先执行系统初始化或联系运维"))
                .getId();
    }

    /**
     * Build a fully-authenticated session for the user whose account is being
     * used to drive the first-time initialization. The token carries
     * `forceChangePassword=true` so the frontend's AppLayout will pop up the
     * mandatory change-password dialog immediately, locking the user out of
     * any data endpoints until a real password is set.
     *
     * The associated {@code init-wizard/setup} call must have just reset the
     * user's {@code passwordHash} to {@code "NO_PASSWORD"} and
     * {@code forcePasswordReset=false} on the entity — this method flips
     * {@code forcePasswordReset=true} so that the in-memory session and the
     * persistent state stay consistent.
     */
    public LoginResponse establishSessionAfterInitialization(UUID userId) {
        Account user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", userId));
        user.setForcePasswordReset(true);
        // Clear lastLoginAt so the frontend's `isFirstLoginAdmin` check
        // (which gates the "old password required" field) recognises this
        // session as a true first login. Without this, even a brand-new
        // admin who has just reset their password would still be prompted
        // for a non-existent previous password.
        user.setLastLoginAt(null);
        userRepository.save(user);
        String token = jwtService.generateToken(user, true, false, false, isRegistrar(user), isSubmitter(user));
        return LoginResponse.of(token, jwtService.expiresInSeconds(),
                toProfile(user, true, false, false));
    }

    public void changePassword(UUID userId, ChangePasswordRequest request) {
        changePassword(userId, request, false);
    }

    public void changePassword(UUID userId, ChangePasswordRequest request, boolean isEmergencyLogin) {
        Account user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", userId));
        
        if (request.newPassword().equals(request.oldPassword())) {
            throw new BusinessException("新密码不能与原密码相同");
        }

        if (isEmergencyLogin || user.isTempPasswordUsed()) {
            String dbHash = user.getPasswordHash();
            if (dbHash.startsWith("TEMP:") || dbHash.startsWith("USED:")) {
                dbHash = dbHash.substring(5);
            }
            if (passwordEncoder.matches(request.newPassword(), dbHash)) {
                throw new BusinessException("新密码不能与原密码相同");
            }
        } else {
            boolean isFirstLoginAdmin = "admin".equals(user.getUsername()) && "NO_PASSWORD".equals(user.getPasswordHash());
            
            if (!isFirstLoginAdmin) {
                String dbHash = user.getPasswordHash();
                String realHash = dbHash;
                if (dbHash.startsWith("TEMP:")) {
                    realHash = dbHash.substring(5);
                } else if (dbHash.startsWith("USED:")) {
                    realHash = dbHash.substring(5);
                }
                
                if (!passwordEncoder.matches(request.oldPassword(), realHash)) {
                    throw new BusinessException("原密码不正确");
                }
            }
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setForcePasswordReset(false);
        user.setTempPasswordUsed(false);
        userRepository.save(user);
    }

    public String resetPassword(UUID userId) {
        Account user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", userId));
        if (user.getSysRole() == SystemRole.SUPER_ADMIN) {
            throw new BusinessException("不能重置超级管理员的密码");
        }
        String tempPassword = generateRandomCode(10);
        user.setPasswordHash("TEMP:" + passwordEncoder.encode(tempPassword));
        user.setForcePasswordReset(true);
        user.setTempPasswordUsed(true);
        userRepository.save(user);
        return tempPassword;
    }

    @Transactional(readOnly = true)
    public Page<AccountProfileResponse> listUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(this::toProfile);
    }

    public AccountProfileResponse register(AccountCreateRequest request) {
        if (!systemSettingService.isAllowUserRegistration()) {
            throw new BusinessException("系统已关闭自主注册功能，请联系管理员");
        }
        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessException("用户名已存在：" + request.username());
        }
        Account user = new Account();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setSysRole(null);
        boolean requireAudit = systemSettingService.isNewUserRequireAudit();
        user.setEnabled(!requireAudit);

        return toProfile(userRepository.save(user));
    }

    public AccountProfileResponse createUser(AccountCreateRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessException("用户名已存在：" + request.username());
        }
        Account user = new Account();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setSysRole(request.sysRole());
        user.setEnabled(request.enabled() == null || request.enabled());

        return toProfile(userRepository.save(user));
    }

    public AccountProfileResponse updateUser(UUID id, AccountUpdateRequest request) {
        Account user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account", id));
        user.setSysRole(request.sysRole());
        user.setEnabled(request.enabled());
        if (request.newPassword() != null && !request.newPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        }

        return toProfile(userRepository.save(user));
    }

    public void deleteUser(UUID id) {
        Account user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account", id));
        if (user.getSysRole() == SystemRole.SUPER_ADMIN) {
            long adminCount = userRepository.findAll().stream()
                    .filter(u -> u.getSysRole() == SystemRole.SUPER_ADMIN && u.isEnabled())
                    .count();
            if (adminCount <= 1) {
                throw new BusinessException("至少需要保留一个启用的管理员账号");
            }
        }
        userRepository.delete(user);
    }

    @Transactional(readOnly = true)
    public IndividualResponse getMyIndividual(UUID userId) {
        Account user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", userId));
        if (user.getIndividual() == null) {
            return null;
        }
        return toIndividualResponse(user.getIndividual());
    }

    public IndividualResponse updateMyIndividual(UUID userId, IndividualRequest request) {
        Account user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", userId));
        Individual entity = user.getIndividual();
        boolean isFirstTime = (entity == null);
        if (entity == null) {
            // 优先复用该账号已绑定 Contact/Registrar 上已有的 Individual（如初始化向导
            // 按管理员姓名创建并关联到 Registrar 的那个）——同一人全程只应有一个
            // Individual，同时代表账户本人与注册人员；无任何可复用对象时才新建。
            entity = contactAccountBindingService.contactsOfAccount(user).stream()
                    .map(Contact::getIndividual)
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            if (entity == null) {
                entity = individualRepository.save(new Individual());
            }
            user.setIndividual(entity);
            userRepository.save(user);
        }

        applyIndividualRequest(entity, request);
        entity = individualRepository.save(entity);

        if (isFirstTime) {
            // Task 4.3: Find contacts associated with this Account and update their Individual
            List<Contact> contacts = contactAccountBindingService.contactsOfAccount(user);
            for (Contact contact : contacts) {
                if (contact.getIndividual() == null) {
                    contact.setIndividual(entity);
                    contactRepository.save(contact);
                }
            }
        }

        // 账号绑定的 Registrar 未关联 Individual 时回填(如 createRegistrarForUser 创建的
        // registrar 初始 individual 为空),使注册人员列表能展示姓名。
        for (Registrar registrar : contactAccountBindingService.registrarsOfAccount(user.getId())) {
            if (registrar.getIndividual() == null) {
                registrar.setIndividual(entity);
                registrarRepository.save(registrar);
            }
        }

        return toIndividualResponse(entity);
    }

    private void applyIndividualRequest(Individual entity, IndividualRequest request) {
        entity.setName(io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.Sign.of(request.name()));
        entity.setTitle(request.title());
        entity.setMailAddress(request.mailAddress() == null
                ? new io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.PostalAddress()
                : io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.PostalAddress.of(request.mailAddress()));

        entity.getEmailAddress().clear();
        if (request.emailAddress() != null) {
            for (String e : request.emailAddress()) {
                entity.getEmailAddress().add(e);
            }
        }
        entity.getPhoneNumber().clear();
        if (request.phoneNumber() != null) {
            for (String p : request.phoneNumber()) {
                entity.getPhoneNumber().add(io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.PhoneNumber.of(p));
            }
        }
    }

    private IndividualResponse toIndividualResponse(Individual entity) {
        return new IndividualResponse(
                entity.getId(),
                entity.getName() == null ? null : entity.getName().getValue(),
                entity.getTitle(),
                entity.getMailAddress() == null ? null : entity.getMailAddress().getValue(),
                entity.getEmailAddress().stream().toList(),
                entity.getPhoneNumber().stream().map(io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.predefinedtypes.PhoneNumber::getValue).toList());
    }

    @Transactional(readOnly = true)
    public String getMyAvatar(UUID userId) {
        Account user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", userId));
        return user.getAvatar();
    }

    public void updateMyAvatar(UUID userId, String dataUrl) {
        validateAvatar(dataUrl);
        Account user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", userId));
        user.setAvatar(dataUrl);
        userRepository.save(user);
    }

    public void deleteMyAvatar(UUID userId) {
        Account user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", userId));
        if (user.getAvatar() == null) {
            return;
        }
        user.setAvatar(null);
        userRepository.save(user);
    }

    private static final List<String> AVATAR_PREFIXES = List.of(
            "data:image/png;base64,",
            "data:image/jpeg;base64,",
            "data:image/webp;base64,");

    private static final long AVATAR_MAX_BYTES = 2L * 1024 * 1024;

    private void validateAvatar(String dataUrl) {
        if (dataUrl == null) {
            throw new BusinessException("头像数据不能为空");
        }
        String prefix = AVATAR_PREFIXES.stream().filter(dataUrl::startsWith).findFirst()
                .orElseThrow(() -> new BusinessException("头像仅支持 PNG、JPEG 或 WebP 格式"));
        byte[] bytes;
        try {
            bytes = java.util.Base64.getMimeDecoder()
                    .decode(dataUrl.substring(prefix.length()).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        } catch (IllegalArgumentException e) {
            throw new BusinessException("头像数据不是合法的 base64 编码");
        }
        if (bytes.length > AVATAR_MAX_BYTES) {
            throw new BusinessException("头像大小不能超过 2MB");
        }
    }

    public AccountProfileResponse auditAccount(UUID id, boolean enabled) {
        Account user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account", id));
        user.setEnabled(enabled);
        return toProfile(userRepository.save(user));
    }

    private AccountProfileResponse toProfile(Account user, boolean forceChangePassword, boolean emergencyLogin, boolean tempPasswordUsed) {
        boolean registrar = isRegistrar(user);
        boolean hasContact = isSubmitter(user);
        boolean profilePrompt = user.getSysRole() != SystemRole.SUPER_ADMIN && !registrar && !hasContact;
        return new AccountProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getSysRole(),
                user.isEnabled(),
                user.getCreatedAt(),
                user.getLastLoginAt(),
                user.getIndividual() != null ? user.getIndividual().getId() : null,
                registrar,
                hasContact,
                profilePrompt,
                forceChangePassword,
                emergencyLogin,
                tempPasswordUsed);
    }

    /**
     * Submitter 业务标志：账号绑定了任一 Contact（含 Registrar 行）即视为
     * Submitter（add-submitter-contact-identity），装配为 ROLE_SUBMITTER。
     */
    private boolean isSubmitter(Account user) {
        return user.getId() != null && contactAccountBindingService.hasAnyContact(user.getId());
    }

    /**
     * 注册人员业务标志：SUPER_ADMIN 恒为注册人员；其余账户当其绑定的 Contact 中存在
     * Registrar 子类实例（ISO/IEC 11179-3：Registrar 是 Contact 的特化，身份由类型
     * 确定，与 Role 无关——Role 仅为描述性元数据）时为注册人员。
     * 该标志随令牌 claim 下发并装配为 ROLE_REGISTRAR。
     */
    private boolean isRegistrar(Account user) {
        if (user.getSysRole() == SystemRole.SUPER_ADMIN) {
            return true;
        }
        return user.getId() != null && !contactAccountBindingService.registrarsOfAccount(user.getId()).isEmpty();
    }

    private AccountProfileResponse toProfile(Account user) {
        return toProfile(user, user.isForcePasswordReset(), false, user.isTempPasswordUsed());
    }
}
