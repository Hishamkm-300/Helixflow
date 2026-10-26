package com.helixflow.security;

import com.helixflow.modules.auth.entity.User;
import com.helixflow.modules.auth.entity.UserSession;
import com.helixflow.modules.auth.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private final UserSessionRepository sessionRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    private static final Duration SESSION_DURATION = Duration.ofHours(8);

    // High-performance in-memory cache to minimize DB round-trips for token validation
    private final Map<String, UserPrincipal> tokenCache = new ConcurrentHashMap<>();

    @Transactional
    public String createSession(User user) {
        byte[] randomBytes = new byte[48];
        secureRandom.nextBytes(randomBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        Instant expiresAt = Instant.now().plus(SESSION_DURATION);

        UserSession session = UserSession.builder()
                .user(user)
                .token(token)
                .expiresAt(expiresAt)
                .build();
        sessionRepository.save(session);

        UserPrincipal principal = UserPrincipal.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .role(user.getRole())
                .build();

        tokenCache.put(token, principal);
        return token;
    }

    @Transactional(readOnly = true)
    public UserPrincipal validateAndGetPrincipal(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        // Check in-memory cache first
        UserPrincipal cached = tokenCache.get(token);
        if (cached != null) {
            return cached;
        }

        // Fallback to database
        Optional<UserSession> sessionOpt = sessionRepository.findByToken(token);
        if (sessionOpt.isPresent()) {
            UserSession session = sessionOpt.get();
            if (!session.isExpired() && session.getUser().isActive()) {
                User user = session.getUser();
                UserPrincipal principal = UserPrincipal.builder()
                        .userId(user.getId())
                        .username(user.getUsername())
                        .fullName(user.getFullName())
                        .role(user.getRole())
                        .build();
                tokenCache.put(token, principal);
                return principal;
            } else if (session.isExpired()) {
                sessionRepository.deleteByToken(token);
            }
        }
        return null;
    }

    @Transactional
    public void invalidate(String token) {
        if (token != null) {
            tokenCache.remove(token);
            sessionRepository.deleteByToken(token);
        }
    }

    @Scheduled(fixedRate = 3600000) // Purge expired sessions every hour
    @Transactional
    public void purgeExpiredSessions() {
        log.debug("Purging expired user sessions from database and memory cache...");
        sessionRepository.deleteExpiredSessions(Instant.now());
    }
}
