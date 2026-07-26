package com.musicapp.auth;

import com.musicapp.common.BadRequestException;
import com.musicapp.listener.Listener;
import com.musicapp.listener.ListenerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final ListenerRepository listenerRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public AuthResponse register (RegisterRequest request) {
        final String username = request.username().trim();
        final String emailAddress = request.emailAddress().trim().toLowerCase();
        final String countryName = request.countryName().trim();

        if (listenerRepository.existsByUsername(username)) {
            throw new BadRequestException("Username is already taken.");
        }
        if (listenerRepository.existsByEmailAddress(emailAddress)) {
            throw new BadRequestException("Email address is already registered.");
        }
        if (!countryExists(countryName)) {
            throw new BadRequestException("Country is not supported yet: " + countryName);
        }

        final Listener listener = Listener.register(
                username,
                emailAddress,
                passwordEncoder.encode(request.password()),
                request.gender().trim(),
                request.dateOfBirth(),
                countryName
        );

        try {
            return toResponse(listenerRepository.save(listener));
        } catch (DataIntegrityViolationException ex) {
            throw new BadRequestException("Registration data conflicts with existing data.");
        }
    }

    @Transactional(readOnly = true)
    public AuthenticatedListener login (LoginRequest request) {
        final String username = request.username().trim();
        final Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(username, request.password())
            );
        } catch (BadCredentialsException ex) {
            throw new BadRequestException("Invalid username or password.");
        }

        final Listener listener = listenerRepository.findByUsername(username).orElseThrow(() -> new BadRequestException("Invalid username or password."));
        return new AuthenticatedListener(toResponse(listener), authentication);
    }

    @Transactional(readOnly = true)
    public AuthResponse getCurrentUser (String username) {
        return listenerRepository.findByUsername(username)
                .map(this::toResponse)
                .orElseThrow(() -> new BadRequestException("The signed-in listener no longer exists."));
    }

    private boolean countryExists (String countryName) {
        final Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM country WHERE country_name = ?",
                Integer.class,
                countryName
        );
        return count != null && count > 0;
    }

    private AuthResponse toResponse (Listener listener) {
        return new AuthResponse(
                listener.getId(),
                listener.getUsername(),
                listener.getEmailAddress(),
                listener.getCountryName(),
                listener.getGender(),
                listener.getDateOfBirth(),
                listener.getPlanName(),
                listener.getBalance()
        );
    }
}
