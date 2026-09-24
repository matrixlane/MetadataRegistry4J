package io.github.matrixlane.metadataregistry.web.auth;

import io.github.matrixlane.metadataregistry.commonfacilities.basicandcore.basicclasses.SystemRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            String token = header.substring(PREFIX.length()).trim();
            try {
                Claims claims = jwtService.parseClaims(token);
                String userId = claims.getSubject();
                String role = claims.get("role", String.class);
                String username = claims.get("username", String.class);
                Boolean forceChangePassword = claims.get("forceChangePassword", Boolean.class);
                Boolean emergencyLogin = claims.get("emergencyLogin", Boolean.class);
                Boolean tempPasswordUsed = claims.get("tempPasswordUsed", Boolean.class);

                java.util.List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();

                if (role != null) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                }
                // 注册人员业务标志（isRegistrar）装配为 ROLE_REGISTRAR：
                // AI 使用类端点按业务角色放行，与 SUPER_ADMIN 系统角色解耦
                if (Boolean.TRUE.equals(claims.get("registrar", Boolean.class))) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_REGISTRAR"));
                }
                // Submitter 业务标志（已登记为 Contact）装配为 ROLE_SUBMITTER：
                // 写操作端点对已登记联系人放行（add-submitter-contact-identity）
                if (Boolean.TRUE.equals(claims.get("submitter", Boolean.class))) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_SUBMITTER"));
                }

                AuthenticatedUser principal = new AuthenticatedUser(
                        UUID.fromString(userId),
                        username,
                        role == null ? null : SystemRole.valueOf(role),
                        Boolean.TRUE.equals(forceChangePassword),
                        Boolean.TRUE.equals(emergencyLogin),
                        Boolean.TRUE.equals(tempPasswordUsed));
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException ex) {
                log.debug("Invalid JWT: {}", ex.getMessage());
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
