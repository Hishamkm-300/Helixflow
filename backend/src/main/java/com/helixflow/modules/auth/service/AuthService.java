package com.helixflow.modules.auth.service;

import com.helixflow.common.exception.UnauthorizedException;
import com.helixflow.modules.auth.dto.LoginRequest;
import com.helixflow.modules.auth.dto.LoginResponse;
import com.helixflow.modules.auth.dto.UserDto;
import com.helixflow.modules.auth.entity.User;
import com.helixflow.modules.auth.repository.UserRepository;
import com.helixflow.security.PasswordService;
import com.helixflow.security.Role;
import com.helixflow.security.SecurityContext;
import com.helixflow.security.TokenService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final TokenService tokenService;

    @PostConstruct
    @Transactional
    public void bootstrapDefaultUsers() {
        if (userRepository.count() == 0) {
            log.info("Bootstrapping default HelixFlow users...");
            createUserIfNotExists("admin", "admin@helixflow.org", "AdminPass123!", "System Administrator", Role.ADMIN);
            createUserIfNotExists("dr_smith", "smith@helixflow.org", "DoctorPass123!", "Dr. Robert Smith, MD", Role.DOCTOR);
            createUserIfNotExists("nurse_joy", "joy@helixflow.org", "NursePass123!", "Nurse Joy Patel, RN", Role.NURSE);
            createUserIfNotExists("pharm_alice", "alice@helixflow.org", "PharmPass123!", "Alice Chang, PharmD", Role.PHARMACIST);
            createUserIfNotExists("recept_bob", "bob@helixflow.org", "ReceptPass123!", "Bob Davis, Front Desk", Role.RECEPTIONIST);
            log.info("Default users successfully created.");
        }
    }

    private void createUserIfNotExists(String username, String email, String password, String fullName, Role role) {
        if (!userRepository.existsByUsername(username)) {
            User user = User.builder()
                    .username(username)
                    .email(email)
                    .passwordHash(passwordService.hash(password))
                    .fullName(fullName)
                    .role(role)
                    .active(true)
                    .build();
            userRepository.save(user);
        }
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new UnauthorizedException("Invalid username or password"));

        if (!user.isActive()) {
            throw new UnauthorizedException("Account is deactivated");
        }

        if (!passwordService.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid username or password");
        }

        String token = tokenService.createSession(user);

        return LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .userId(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .role(user.getRole())
                .build();
    }

    @Transactional
    public void logout(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            tokenService.invalidate(token);
        }
    }

    @Transactional(readOnly = true)
    public UserDto getCurrentUser() {
        Long userId = SecurityContext.getCurrentUserId();
        if (userId == null) {
            throw new UnauthorizedException("No active authenticated session");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        return UserDto.fromEntity(user);
    }
}
