package com.example.execrise2.ai_orchestrator.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * [Phase 2.1] AdminApiKeyFilter — Bảo vệ endpoint admin bằng X-Admin-Token header.
 *
 * Lỗ hổng cũ: GET /api/ai/pool-reset ai cũng gọi được →
 *   Kẻ tấn công liên tục reset pool → bypass quota protection.
 *
 * Giải pháp: OncePerRequestFilter chặn các endpoint admin.
 *   - Header: X-Admin-Token: <secret>
 *   - Thiếu/sai token → HTTP 403 (không leak chi tiết lỗi)
 *   - Chỉ áp dụng cho PROTECTED_PATHS, các endpoint khác không bị ảnh hưởng.
 *
 * Cấu hình:
 *   admin.api-token=CHANGE_ME_TO_RANDOM_SECRET   (application.properties)
 */
@Component
@Slf4j
public class AdminApiKeyFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "X-Admin-Token";

    /** Các path cần bảo vệ */
    private static final Set<String> PROTECTED_PATHS = Set.of(
        "/api/ai/pool-reset",
        "/api/ai/pool-stats",
        "/api/ai/metrics"
    );

    @Value("${admin.api-token:CHANGE_ME}")
    private String adminToken;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Chỉ kiểm tra protected paths
        if (!PROTECTED_PATHS.contains(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Kiểm tra token
        String providedToken = request.getHeader(HEADER_NAME);

        if (providedToken == null || !providedToken.equals(adminToken)) {
            log.warn("🔐 [AdminFilter] Unauthorized access to {} from IP={}",
                    path, request.getRemoteAddr());

            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            // Không leak lý do chi tiết (security best practice)
            response.getWriter().write("""
                    {"error":"Forbidden","message":"Access denied. Missing or invalid admin token."}
                    """);
            return;
        }

        log.debug("✅ [AdminFilter] Admin access granted to {} from IP={}", path, request.getRemoteAddr());
        filterChain.doFilter(request, response);
    }
}
