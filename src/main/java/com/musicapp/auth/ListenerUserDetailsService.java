package com.musicapp.auth;

import com.musicapp.listener.Listener;
import com.musicapp.listener.ListenerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListenerUserDetailsService implements UserDetailsService {
    private final ListenerRepository listenerRepository;

    @Override
    public UserDetails loadUserByUsername (String username) {
        final Listener listener = listenerRepository.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException("Listener not found: " + username));
        return new User(listener.getUsername(), listener.getPasswordHash(), List.of(new SimpleGrantedAuthority("ROLE_LISTENER")));
    }
}
