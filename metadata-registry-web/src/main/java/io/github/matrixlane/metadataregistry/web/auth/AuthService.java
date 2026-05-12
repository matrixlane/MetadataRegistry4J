package io.github.matrixlane.metadataregistry.web.auth;

import io.github.matrixlane.metadataregistry.web.auth.dto.AppUserCreateRequest;
import io.github.matrixlane.metadataregistry.web.auth.dto.AppUserUpdateRequest;
import io.github.matrixlane.metadataregistry.web.auth.dto.ChangePasswordRequest;
import io.github.matrixlane.metadataregistry.web.auth.dto.LoginRequest;
import io.github.matrixlane.metadataregistry.web.auth.dto.LoginResponse;
import io.github.matrixlane.metadataregistry.web.auth.dto.UserProfileResponse;
import io.github.matrixlane.metadataregistry.web.common.BusinessException;
import io.github.matrixlane.metadataregistry.web.common.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public LoginResponse login(LoginRequest request) {
        AppUser user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new BadCredentialsException("用户名或密码错误"));
        if (!user.isEnabled()) {
            throw new BusinessException("账号已被禁用，请联系管理员");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("用户名或密码错误");
        }
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        String token = jwtService.generateToken(user);
        return LoginResponse.of(token, jwtService.expiresInSeconds(), toProfile(user));
    }

    @Transactional(readOnly = true)
    public UserProfileResponse profile(UUID userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("AppUser", userId));
        return toProfile(user);
    }

    public void changePassword(UUID userId, ChangePasswordRequest request) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("AppUser", userId));
        if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new BusinessException("原密码不正确");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public Page<UserProfileResponse> listUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(this::toProfile);
    }

    public UserProfileResponse createUser(AppUserCreateRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessException("用户名已存在：" + request.username());
        }
        AppUser user = new AppUser();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName());
        user.setEmail(request.email());
        user.setRole(request.role());
        user.setEnabled(request.enabled() == null || request.enabled());
        return toProfile(userRepository.save(user));
    }

    public UserProfileResponse updateUser(UUID id, AppUserUpdateRequest request) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AppUser", id));
        user.setDisplayName(request.displayName());
        user.setEmail(request.email());
        user.setRole(request.role());
        user.setEnabled(request.enabled());
        if (request.newPassword() != null && !request.newPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        }
        return toProfile(userRepository.save(user));
    }

    public void deleteUser(UUID id) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AppUser", id));
        if (user.getRole() == AppUserRole.ADMIN) {
            long adminCount = userRepository.findAll().stream()
                    .filter(u -> u.getRole() == AppUserRole.ADMIN && u.isEnabled())
                    .count();
            if (adminCount <= 1) {
                throw new BusinessException("至少需要保留一个启用的管理员账号");
            }
        }
        userRepository.delete(user);
    }

    private UserProfileResponse toProfile(AppUser user) {
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                user.getRole(),
                user.isEnabled(),
                user.getCreatedAt(),
                user.getLastLoginAt());
    }
}
