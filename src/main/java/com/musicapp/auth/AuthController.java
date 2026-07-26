package com.musicapp.auth;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register (@Valid @RequestBody RegisterRequest request, HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        authService.register(request);
        final AuthenticatedListener authenticatedListener = authService.login(new LoginRequest(request.username(), request.password()));
        establishSession(authenticatedListener.authentication(), servletRequest, servletResponse);
        return authenticatedListener.user();
    }

    @PostMapping("/login")
    public AuthResponse login (@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        final AuthenticatedListener authenticatedListener = authService.login(request);
        establishSession(authenticatedListener.authentication(), servletRequest, servletResponse);
        return authenticatedListener.user();
    }

    @GetMapping("/me")
    public AuthResponse getCurrentUser (Authentication authentication) {
        return authService.getCurrentUser(authentication.getName());
    }

    @GetMapping("/csrf")
    public CsrfTokenResponse getCsrfToken (CsrfToken csrfToken) {
        return new CsrfTokenResponse(csrfToken.getHeaderName(), csrfToken.getToken());
    }

    private void establishSession (Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
        final SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
