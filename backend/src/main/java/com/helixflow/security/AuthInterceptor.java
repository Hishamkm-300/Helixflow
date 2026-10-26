package com.helixflow.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helixflow.common.dto.ApiError;
import com.helixflow.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final TokenService tokenService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. Explicitly bypass CORS pre-flight OPTIONS requests to prevent frontend Axios/Fetch network blocks
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return true;
        }

        // If not a handler method (e.g. resource handler, static assets), proceed
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        String path = request.getRequestURI();

        // 2. Allow public endpoints (login, register, health checks, swagger/h2)
        if (path.startsWith("/api/v1/auth/login") ||
            path.startsWith("/api/v1/auth/register") ||
            path.startsWith("/h2-console") ||
            path.startsWith("/actuator") ||
            path.startsWith("/error")) {
            return true;
        }

        // 3. Extract Authorization header
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "Missing or invalid Authorization header");
            return false;
        }

        String token = authHeader.substring(7).trim();
        UserPrincipal principal = tokenService.validateAndGetPrincipal(token);
        if (principal == null) {
            writeErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "Session token is invalid or expired");
            return false;
        }

        // 4. Role-based authorization check via @RequireRole annotation
        RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (requireRole == null && handlerMethod.getBeanType() != null) {
            requireRole = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }

        if (requireRole != null) {
            boolean hasPermission = false;
            for (Role permittedRole : requireRole.value()) {
                // ADMIN has root access across all modules
                if (permittedRole == principal.getRole() || principal.getRole() == Role.ADMIN) {
                    hasPermission = true;
                    break;
                }
            }
            if (!hasPermission) {
                writeErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN",
                        "Role '" + principal.getRole() + "' is not authorized for this resource");
                return false;
            }
        }

        // 5. Store principal in thread-safe context for downstream controllers & services
        SecurityContext.set(principal);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // Strict finally cleanup prevents virtual thread carrier contamination on Loom
        SecurityContext.clear();
    }

    private void writeErrorResponse(HttpServletResponse response, int statusCode, String code, String message) throws IOException {
        response.setStatus(statusCode);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError error = ApiError.builder()
                .code(code)
                .message(message)
                .build();
        ApiResponse<Void> apiResponse = ApiResponse.failed(error);
        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
    }
}
