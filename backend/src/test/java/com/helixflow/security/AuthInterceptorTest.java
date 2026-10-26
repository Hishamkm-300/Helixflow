package com.helixflow.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.method.HandlerMethod;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthInterceptorTest {

    @Mock
    private TokenService tokenService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private AuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        interceptor = new AuthInterceptor(tokenService, mapper);
        SecurityContext.clear();
    }

    @Test
    void shouldBypassCorsOptionsRequest() throws Exception {
        when(request.getMethod()).thenReturn("OPTIONS");

        boolean result = interceptor.preHandle(request, response, new Object());

        assertTrue(result);
        verify(response).setStatus(HttpServletResponse.SC_OK);
        verifyNoInteractions(tokenService);
    }

    @Test
    void shouldRejectWhenMissingAuthorizationHeader() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/beds/admit");
        when(request.getHeader("Authorization")).thenReturn(null);

        HandlerMethod handlerMethod = mock(HandlerMethod.class);
        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        boolean result = interceptor.preHandle(request, response, handlerMethod);

        assertFalse(result);
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void shouldAuthenticateValidBearerToken() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/beds/admit");
        when(request.getHeader("Authorization")).thenReturn("Bearer valid-token-123");

        UserPrincipal principal = UserPrincipal.builder()
                .userId(1L)
                .username("dr_smith")
                .role(Role.DOCTOR)
                .build();
        when(tokenService.validateAndGetPrincipal("valid-token-123")).thenReturn(principal);

        HandlerMethod handlerMethod = mock(HandlerMethod.class);

        boolean result = interceptor.preHandle(request, response, handlerMethod);

        assertTrue(result);
        assertEquals(1L, SecurityContext.getCurrentUserId());
        assertEquals(Role.DOCTOR, SecurityContext.getCurrentUserRole());
    }

    @Test
    void shouldClearSecurityContextOnCompletion() {
        UserPrincipal principal = UserPrincipal.builder()
                .userId(1L)
                .username("dr_smith")
                .role(Role.DOCTOR)
                .build();
        SecurityContext.set(principal);
        assertNotNull(SecurityContext.get());

        interceptor.afterCompletion(request, response, new Object(), null);

        assertNull(SecurityContext.get(), "SecurityContext must be purged to prevent carrier leakage on Loom");
    }
}
