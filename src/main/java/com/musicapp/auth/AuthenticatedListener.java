package com.musicapp.auth;

import org.springframework.security.core.Authentication;

/**
 * The successful authentication result is kept server-side and is never sent
 * to the browser. The controller uses it to establish the HTTP session.
 */
public record AuthenticatedListener(AuthResponse user, Authentication authentication) {
}
