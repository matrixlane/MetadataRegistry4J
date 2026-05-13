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
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
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
                        .requestMatchers("/api/v1/auth/login").permitAll()
                        .requestMatchers("/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/actuator/health",
                                "/actuator/info").permitAll()
                        .requestMatchers("/actuator/loggers/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/change-password").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/app-users/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/app-users/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/app-users/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/**").hasAnyRole("ADMIN", "REGISTRAR")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/**").hasAnyRole("ADMIN", "REGISTRAR")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/**").hasAnyRole("ADMIN", "REGISTRAR")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/**").hasAnyRole("ADMIN", "REGISTRAR")
                        .anyRequest().authenticated())
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
