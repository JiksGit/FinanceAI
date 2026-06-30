package com.finance.dashboard.security;

import com.finance.dashboard.config.ServiceAuthConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Lambda/EventBridge 등 서버 간 호출 전용 인증.
 * /api/internal/** 경로에 대해 X-Service-Token 헤더를 검증하고,
 * 일치하면 ROLE_SERVICE 권한으로 인증 컨텍스트를 채운다.
 */
@Component
@RequiredArgsConstructor
public class ServiceTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "X-Service-Token";
    private static final String INTERNAL_PATH_PREFIX = "/api/internal/";

    private final ServiceAuthConfig serviceAuthConfig;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (request.getRequestURI().startsWith(INTERNAL_PATH_PREFIX) && serviceAuthConfig.isEnabled()) {
            String token = request.getHeader(HEADER_NAME);
            if (serviceAuthConfig.token().equals(token)) {
                var authentication = new UsernamePasswordAuthenticationToken(
                        "internal-service", null, List.of(new SimpleGrantedAuthority("ROLE_SERVICE")));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }
}
