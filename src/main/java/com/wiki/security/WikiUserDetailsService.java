package com.wiki.security;

import com.wiki.model.User;
import com.wiki.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

/**
 * UserDetailsService implementation for Spring Security.
 * Loads user details from the database for authentication.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WikiUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.debug("Loading user details for: {}", username);

        User user = userRepository.findByUsernameWithRoles(username)
                .orElseThrow(() -> {
                    log.warn("User not found: {}", username);
                    return new UsernameNotFoundException("User not found: " + username);
                });

        if (!user.isEnabled()) {
            log.warn("User account is disabled: {}", username);
            throw new UsernameNotFoundException("User account is disabled: " + username);
        }

        if (!user.isAccountNonLocked()) {
            log.warn("User account is locked: {}", username);
            throw new UsernameNotFoundException("User account is locked: " + username);
        }

        var authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName()))
                .collect(Collectors.toList());

        log.debug("User {} has authorities: {}", username, authorities);

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                user.isEnabled(),
                true, // accountNonExpired
                true, // credentialsNonExpired
                user.isAccountNonLocked(),
                authorities
        );
    }
}