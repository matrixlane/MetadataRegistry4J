package io.github.matrixlane.metadataregistry.web.auth;

import io.github.matrixlane.metadataregistry.web.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final AppProperties props;
    private SecretKey signingKey;

    @PostConstruct
    void init() {
        String secret = props.getJwt().getSecret();
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("app.jwt.secret 必须配置且不少于 32 字节，请设置环境变量 JWT_SECRET 或修改 application.yml。");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(AppUser user) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(props.getJwt().getExpiresMinutes() * 60L);
        return Jwts.builder()
                .subject(user.getId().toString())
                .issuer(props.getJwt().getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .claim("username", user.getUsername())
                .claim("role", user.getRole().name())
                .claim("displayName", user.getDisplayName())
                .signWith(signingKey)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long expiresInSeconds() {
        return props.getJwt().getExpiresMinutes() * 60L;
    }
}
