package com.finance.dashboard.security;

import com.finance.dashboard.config.JwtConfig;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private static final String CLAIM_TOKEN_TYPE = "tokenType";
    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";

    private final Key key;
    private final JwtConfig jwtConfig;

    public JwtTokenProvider(JwtConfig jwtConfig) {
        this.jwtConfig = jwtConfig;
        this.key = Keys.hmacShaKeyFor(jwtConfig.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(String email) {
        return createToken(email, TOKEN_TYPE_ACCESS, jwtConfig.accessTokenExpiration());
    }

    public String createRefreshToken(String email) {
        return createToken(email, TOKEN_TYPE_REFRESH, jwtConfig.refreshTokenExpiration());
    }

    private String createToken(String email, String tokenType, long expiration) {
        Date now = new Date();
        return Jwts.builder()
                .subject(email)
                .claim(CLAIM_TOKEN_TYPE, tokenType)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration))
                .signWith(key)
                .compact();
    }

    /**
     * @throws ExpiredJwtException 토큰 만료 시
     * @throws JwtException 서명 불일치 등 그 외 토큰이 유효하지 않은 경우
     */
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith((javax.crypto.SecretKey) key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String getEmail(Claims claims) {
        return claims.getSubject();
    }

    public boolean isRefreshToken(Claims claims) {
        return TOKEN_TYPE_REFRESH.equals(claims.get(CLAIM_TOKEN_TYPE, String.class));
    }
}
