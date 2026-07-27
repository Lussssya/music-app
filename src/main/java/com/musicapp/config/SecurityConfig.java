package com.musicapp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain (HttpSecurity http, SecurityContextRepository securityContextRepository) throws Exception {
        final HttpSessionCsrfTokenRepository csrfTokenRepository = new HttpSessionCsrfTokenRepository();

        return http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        // The React client receives the token as JSON and keeps it only in memory.
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                )
                .securityContext(securityContext -> securityContext.securityContextRepository(securityContextRepository))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/api/auth/register", "/api/auth/login", "/api/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/performers", "/api/performers/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/albums", "/api/albums/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/songs", "/api/songs/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/genres", "/api/genres/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/discovery/trending", "/api/discovery/suggestions").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/playlists/generated", "/api/playlists/generated/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/playlists", "/api/playlists/**").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
                        (request, response, exception) -> response.sendError(HttpStatus.UNAUTHORIZED.value())
                ))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .clearAuthentication(true)
                        .invalidateHttpSession(true)
                        .deleteCookies("MUSIC_APP_SESSION")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(HttpStatus.NO_CONTENT.value()))
                )
                .build();
    }

    @Bean
    SecurityContextRepository securityContextRepository () {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy () {
        return new ChangeSessionIdAuthenticationStrategy();
    }

    @Bean
    PasswordEncoder passwordEncoder () {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager (AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
