package com.bridgework.auth.security;

import com.bridgework.auth.config.BridgeWorkAuthProperties;
import com.bridgework.common.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RefreshCookieOriginFilter extends OncePerRequestFilter {

    private static final Set<String> PROTECTED_PATHS = Set.of(
            "/api/v1/auth/token/refresh",
            "/api/v1/auth/logout"
    );

    private final Set<String> allowedOrigins;
    private final ObjectMapper objectMapper;

    public RefreshCookieOriginFilter(BridgeWorkAuthProperties authProperties, ObjectMapper objectMapper) {
        this.allowedOrigins = authProperties.getAllowedOrigins().stream()
                .map(this::normalizeOrigin)
                .filter(StringUtils::hasText)
                .collect(Collectors.toUnmodifiableSet());
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !PROTECTED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (!StringUtils.hasText(origin)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!allowedOrigins.contains(normalizeOrigin(origin))) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getWriter(), ApiResponse.error(
                    "UNTRUSTED_ORIGIN",
                    "허용되지 않은 출처의 인증 요청입니다."
            ));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String normalizeOrigin(String value) {
        try {
            URI uri = URI.create(value.trim());
            if (!StringUtils.hasText(uri.getScheme()) || !StringUtils.hasText(uri.getHost())) {
                return "";
            }
            int port = uri.getPort();
            return uri.getScheme().toLowerCase() + "://" + uri.getHost().toLowerCase()
                    + (port < 0 ? "" : ":" + port);
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }
}
