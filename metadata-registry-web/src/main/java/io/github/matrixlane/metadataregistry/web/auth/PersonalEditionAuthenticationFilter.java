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
import jakarta.servlet.http.HttpServletRequestWrapper;
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
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * 个人版免登录的隐式管理员自动认证（personal-no-login-registrar-default，方案 A）。
 *
 * <p>个人版仅本地应用可访问，"账号登录以识别用户"已无意义。当部署版本为
 * {@code PERSONAL} 且当前尚无已认证主体时，本过滤器自动装载系统唯一的 SUPER_ADMIN
 * 账号构建认证主体：
 * <ul>
 *   <li>authorities 按 AuthService 同款业务逻辑装配——SUPER_ADMIN 恒持有
 *       {@code ROLE_SUPER_ADMIN} 与 {@code ROLE_REGISTRAR}；账号绑定任一
 *       Contact（含 Registrar 行）时追加 {@code ROLE_SUBMITTER}；</li>
 *   <li>{@code forceChangePassword} 恒为 {@code false}——个人版无密码概念，
 *       密码相关拦截（498/强制改密）对个人版会话 MUST NOT 触发。</li>
 * </ul>
 *
 * <p>个人版对 {@code Authorization} 头一律免疫：向后续链传入屏蔽该头的请求包装，
 * 使 {@link JwtAuthenticationFilter} 完全看不到浏览器 localStorage 中可能残留的令牌。
 * 否则一个带 {@code forceChangePassword=true} 声明的旧令牌（向导会话令牌或强制改密
 * 期间签发的令牌）会覆盖隐式主体，把整个个人版会话推进 498 死锁——用户既无法使用
 * 系统，也无法通过任何合法操作把该标志清掉。
 *
 * <p>未初始化（无 SUPER_ADMIN 业务数据）时保持匿名——向导端点本就在白名单，
 * 其余端点由未初始化访问控制返回 499，行为不变。
 *
 * <p>依赖经 {@link ObjectProvider} 注入：完整应用上下文恒可解析；测试切片
 * （{@code @WebMvcTest}）缺失合作 bean 时空 provider 使过滤器安全降级为
 * no-op（与既有 JwtAuthenticationFilter 在切片中的处置一致）。
 */
@Slf4j
@Component
public class PersonalEditionAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";

    private final ObjectProvider<SystemSettingService> systemSettingService;
    private final ObjectProvider<AccountRepository> accountRepository;
    private final ObjectProvider<ContactAccountService> contactAccountService;

    /**
     * 初始化状态提供者（init-phase-database-free）：新架构开启时版本判定走
     * 本地状态记录，向导相因此零数据库查询。required=false 保持 @WebMvcTest
     * 切片可加载；缺失时回退 SystemSettingService 的数据库判定（既有行为）。
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private io.github.matrixlane.metadataregistry.web.bootstrap.InitializationStatusProvider statusProvider;

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
        Authentication existing = SecurityContextHolder.getContext().getAuthentication();
        boolean alreadyAuthenticatedUser = existing != null
                && existing.getPrincipal() instanceof AuthenticatedUser;
        if (!alreadyAuthenticatedUser && isPersonalEdition()) {
            accountRepository.getObject().findFirstBySysRole(SystemRole.SUPER_ADMIN)
                    .ifPresent(this::authenticateAsImplicitAdmin);
            filterChain.doFilter(new AuthorizationHiddenRequest(request), response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    /** 对后续过滤器链隐藏 Authorization 头，令 {@link JwtAuthenticationFilter} 无从读取令牌。 */
    private static final class AuthorizationHiddenRequest extends HttpServletRequestWrapper {

        private AuthorizationHiddenRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getHeader(String name) {
            return HEADER.equalsIgnoreCase(name) ? null : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return HEADER.equalsIgnoreCase(name) ? Collections.emptyEnumeration() : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = Collections.list(super.getHeaderNames());
            names.removeIf(HEADER::equalsIgnoreCase);
            return Collections.enumeration(names);
        }
    }

    private boolean isPersonalEdition() {
        // 新架构：版本判定走本地状态记录（向导相零数据库查询；与数据库判定在
        // 未初始化时同为 null → 非个人版，行为一致）
        io.github.matrixlane.metadataregistry.web.bootstrap.InitializationStatusProvider provider = statusProvider;
        if (provider != null) {
            return provider.getEdition() == DeploymentEdition.PERSONAL;
        }
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
