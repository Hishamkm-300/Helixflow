package com.helixflow.security;

public final class SecurityContext {

    private static final ThreadLocal<UserPrincipal> CURRENT_USER = new ThreadLocal<>();

    private SecurityContext() {
        // Prevent instantiation
    }

    public static void set(UserPrincipal principal) {
        CURRENT_USER.set(principal);
    }

    public static UserPrincipal get() {
        return CURRENT_USER.get();
    }

    public static Long getCurrentUserId() {
        UserPrincipal principal = get();
        return principal != null ? principal.getUserId() : null;
    }

    public static Role getCurrentUserRole() {
        UserPrincipal principal = get();
        return principal != null ? principal.getRole() : null;
    }

    public static void clear() {
        CURRENT_USER.remove();
    }
}
