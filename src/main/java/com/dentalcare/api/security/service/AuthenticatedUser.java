package com.dentalcare.api.security.service;

import java.util.Collection;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;

public record AuthenticatedUser(UUID userId, Collection<? extends GrantedAuthority> authorities) {
}
