package com.musicapp.listener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "listener")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Listener {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "listener_id")
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(name = "email_address", nullable = false, unique = true, length = 128)
    private String emailAddress;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 32)
    private String gender;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(name = "country_name", nullable = false, length = 64)
    private String countryName;

    @Column(nullable = false)
    private BigDecimal balance;

    @Column(name = "plan_name", nullable = false, length = 64)
    private String planName;

    public static Listener register (
            String username,
            String emailAddress,
            String passwordHash,
            String gender,
            LocalDate dateOfBirth,
            String countryName
    ) {
        Listener listener = new Listener();
        listener.username = username;
        listener.emailAddress = emailAddress;
        listener.passwordHash = passwordHash;
        listener.gender = gender;
        listener.dateOfBirth = dateOfBirth;
        listener.countryName = countryName;
        listener.balance = BigDecimal.ZERO;
        listener.planName = "free";
        return listener;
    }
}
