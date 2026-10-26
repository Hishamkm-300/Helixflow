package com.helixflow.security;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserPrincipal {
    private final Long userId;
    private final String username;
    private final String fullName;
    private final Role role;
}
