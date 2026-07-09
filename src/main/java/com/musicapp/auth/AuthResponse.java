package com.musicapp.auth;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AuthResponse(
        Long listenerId,
        String username,
        String emailAddress,
        String countryName,
        String gender,
        LocalDate dateOfBirth,
        String planName,
        BigDecimal balance
) {
}
