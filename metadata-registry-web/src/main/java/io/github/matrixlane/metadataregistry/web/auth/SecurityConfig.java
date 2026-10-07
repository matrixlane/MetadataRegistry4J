package io.github.matrixlane.metadataregistry.web.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.matrixlane.metadataregistry.web.common.ApiCodes;
import io.github.matrixlane.metadataregistry.web.common.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final PersonalEditionAuthenticationFilter personalEditionAuthenticationFilter;
    private final UrlBasedCorsConfigurationSource corsConfigurationSource;
    private final ObjectMapper objectMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(c -> c.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint((req, resp, ex) -> writeJson(resp,
                                HttpServletResponse.SC_UNAUTHORIZED,
                                ApiResponse.error(ApiCodes.UNAUTHORIZED, "未登录或登录已过期")))
                        .accessDeniedHandler((req, resp, ex) -> writeJson(resp,
                                HttpServletResponse.SC_FORBIDDEN,
                                ApiResponse.error(ApiCodes.FORBIDDEN, "无权访问此资源"))))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/auth/allow-registration",
                                "/api/v1/datasource/**",
                                "/api/v1/migration/**",
                                "/api/v1/startup/status",
                                "/api/v1/init-wizard/status", "/api/v1/init-wizard/wizard-state",
                                "/api/v1/init-wizard/setup", "/api/v1/init-wizard/commit",
                                "/api/v1/init-wizard/abort",
                                // 标识模板设计器的匿名只读端点（初始化向导在无账号状态下也需要）：
                                // 预设/对象缩写为静态资源，匿名预览不产生序号分配
                                "/api/v1/identifier-strategy/presets",
                                "/api/v1/identifier-strategy/object-abbreviations",
                                "/api/v1/identifier-strategy/preview",
                                "/", "/index.html", "/favicon.svg", "/favicon.ico",
                                "/apple-touch-icon.png", "/site.webmanifest", "/icons/**", "/assets/**").permitAll()
                        // 语言字典的匿名只读列表（初始化向导在无账号状态下需拉取语言选项以显示中文语言名）；
                        // 仅限 GET 精确路径，不开放 CRUD
                        .requestMatchers(HttpMethod.GET, "/api/v1/languages").permitAll()
                        .requestMatchers("/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/actuator/health",
                                "/actuator/info").permitAll()
                        .requestMatchers("/actuator/loggers/**").hasRole("SUPER_ADMIN")
                        .requestMatchers("/api/v1/system/**").hasRole("SUPER_ADMIN")
                        // AI 使用类端点整体限注册人员业务角色（REGISTRAR）；
                        // AI 辅助设置（候选数/快捷键等应用级偏好）属"应用设置"，
                        // 每一位登录用户均可读写（add-workspace-administrative-status：
                        // 从系统设置迁至右上角应用设置入口）；显式置于通用规则之前。
                        .requestMatchers(HttpMethod.GET, "/api/v1/assistant/settings").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/assistant/settings").authenticated()
                        // "我的"入口（联系人身份管理等）：任何登录用户可管理自己的资料，
                        // 越权由服务层按账号归属校验（add-submitter-contact-identity）
                        .requestMatchers("/api/v1/my/**").authenticated()
                        // 生命周期状态治理仍仅 Registrar/Admin（add-submitter-contact-identity D6），
                        // 显式前置收紧，不依赖服务层兜底
                        .requestMatchers(HttpMethod.POST, "/api/v1/administered-items/*/transitions/**")
                            .hasAnyRole("SUPER_ADMIN", "REGISTRAR")
                        // 注册流程画布布局：GET 登录可读（渲染恢复），保存仅 SUPER_ADMIN（显式前置）
                        .requestMatchers(HttpMethod.PUT, "/api/v1/registration-process/layout").hasRole("SUPER_ADMIN")
                        .requestMatchers("/api/v1/inference/**", "/api/v1/assistant/**").hasRole("REGISTRAR")
                        .requestMatchers("/api/v1/federation/inbound/**").permitAll()
                        .requestMatchers("/api/v1/federation/**").hasRole("SUPER_ADMIN")
                        // SPA 前端路由深链接（如 /login、/namespaces）：放行 GET，
                        // 由 SpaForwardController 转发到 index.html 交给客户端路由
                        .requestMatchers(RegexRequestMatcher.regexMatcher(HttpMethod.GET,
                                "^/(?!api(?:/.*)?$|v3(?:/.*)?$|swagger-ui|actuator(?:/.*)?$|assets(?:/.*)?$|icons(?:/.*)?$|favicon\\.svg$|favicon\\.ico$|apple-touch-icon\\.png$|site\\.webmanifest$|index\\.html$).*$"))
                        .permitAll()
                        // 工作空间：GET summary/items 登录即可；写操作需 SUBMITTER/REGISTRAR/ADMIN
                        .requestMatchers(HttpMethod.GET, "/api/v1/workspace/summary", "/api/v1/workspace/items")
                            .authenticated()
                        .requestMatchers("/api/v1/workspace/**").hasAnyRole("SUPER_ADMIN", "REGISTRAR", "SUBMITTER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/change-password").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/auth/me/individual").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/accounts/**").hasRole("SUPER_ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/accounts/**").hasRole("SUPER_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/accounts/**").hasRole("SUPER_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/**").hasAnyRole("SUPER_ADMIN", "REGISTRAR", "SUBMITTER")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/**").hasAnyRole("SUPER_ADMIN", "REGISTRAR", "SUBMITTER")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/**").hasAnyRole("SUPER_ADMIN", "REGISTRAR", "SUBMITTER")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/**").hasAnyRole("SUPER_ADMIN", "REGISTRAR", "SUBMITTER")
                        .anyRequest().authenticated())
                .addFilterBefore(personalEditionAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private void writeJson(HttpServletResponse resp, int status, ApiResponse<?> body) throws java.io.IOException {
        resp.setStatus(status);
        resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
        resp.setCharacterEncoding("UTF-8");
        resp.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
