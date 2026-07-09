package com.musicapp.listener;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ListenerRepository extends JpaRepository<Listener, Long> {
    Optional<Listener> findByUsername (String username);

    boolean existsByUsername (String username);

    boolean existsByEmailAddress (String emailAddress);
}
