package io.github.matrixlane.metadataregistry.web.auth;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.Account;
import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole;
import io.github.matrixlane.metadataregistry.web.edition.DeploymentEdition;
import io.github.matrixlane.metadataregistry.web.service.ContactAccountService;
import io.github.matrixlane.metadataregistry.web.service.SystemSettingService;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 个人版免登录的隐式管理员自动认证（personal-no-login-registrar-default，方案 A）。
 *
 * <p>个人版仅本地应用可访问，"账号登录以识别用户"已无意义。当部署版本为
 * {@code PERSONAL}、请求未携带 {@code Authorization} 头、且当前尚无已认证主体时，
 * 本过滤器自动装载系统唯一的 SUPER_ADMIN 账号构建认证主体：
 * <ul>
 *   <li>authorities 按 AuthService 同款业务逻辑装配——SUPER_ADMIN 恒持有
 *       {@code ROLE_SUPER_ADMIN} 与 {@code ROLE_REGISTRAR}；账号绑定任一
 *       Contact（含 Registrar 行）时追加 {@code ROLE_SUBMITTER}；</li>
 *   <li>{@code forceChangePassword} 恒为 {@code false}——个人版无密码概念，
 *       密码相关拦截（498/强制改密）对个人版会话 MUST NOT 触发。</li>
 * </ul>
 *
 * <p>携带 Bearer 头的请求一律交由 {@link JwtAuthenticationFilter} 处理（团队版路径与
 * 个人版残留令牌均不受影响）；未初始化（无 SUPER_ADMIN 业务数据）时保持匿名——
 * 向导端点本就在白名单，其余端点由未初始化访问控制返回 499，行为不变。
 *
 * <p>依赖经 {@link ObjectProvider} 注入：完整应用上下文恒可解析；测试切片
 * （{@code @WebMvcTest}）缺失合作 bean 时空 provider 使过滤器安全降级为
 * no-op（与既有 JwtAuthenticationFilter 在切片中的处置一致）。
 */
@Slf4j
@Component
public class PersonalEditionAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final ObjectProvider<SystemSettingService> systemSettingService;
    private final ObjectProvider<AccountRepository> accountRepository;
    private final ObjectProvider<ContactAccountService> contactAccountService;

    public PersonalEditionAuthenticationFilter(ObjectProvider<SystemSettingService> systemSettingService,
                                               ObjectProvider<AccountRepository> accountRepository,
                                               ObjectProvider<ContactAccountService> contactAccountService) {
        this.systemSettingService = systemSettingService;
        this.accountRepository = accountRepository;
        this.contactAccountService = contactAccountService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        Authentication existing = SecurityContextHolder.getContext().getAuthentication();
        boolean alreadyAuthenticatedUser = existing != null
                && existing.getPrincipal() instanceof AuthenticatedUser;
        if (!alreadyAuthenticatedUser
                && (header == null || !header.startsWith(PREFIX))
                && isPersonalEdition()) {
            accountRepository.getObject().findFirstBySysRole(SystemRole.SUPER_ADMIN)
                    .ifPresent(this::authenticateAsImplicitAdmin);
        }
        filterChain.doFilter(request, response);
    }

    private boolean isPersonalEdition() {
        SystemSettingService settings = systemSettingService.getIfAvailable();
        if (settings == null) {
            log.warn("个人版自动认证过滤器缺少 SystemSettingService，隐式管理员认证降级为 no-op");
            return false;
        }
        return settings.getEdition() == DeploymentEdition.PERSONAL;
    }

    private void authenticateAsImplicitAdmin(Account admin) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
        // 同 AuthService.isRegistrar：SUPER_ADMIN 恒为注册人员业务角色
        authorities.add(new SimpleGrantedAuthority("ROLE_REGISTRAR"));
        // Submitter 通道：绑定任一 Contact（含 Registrar 行）即装配
        ContactAccountService bindings = contactAccountService.getIfAvailable();
        if (bindings != null && bindings.hasAnyContact(admin.getId())) {
            authorities.add(new SimpleGrantedAuthority("ROLE_SUBMITTER"));
        }
        AuthenticatedUser principal = new AuthenticatedUser(
                admin.getId(),
                admin.getUsername(),
                SystemRole.SUPER_ADMIN,
                false, // forceChangePassword：个人版无密码概念，恒 false
                false,
                false);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
