package com.chatter.chatter.user.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.chatter.chatter.user.repository.UserRepository;

/**
 * Used only by {@code AuthenticationManager} during login. Authenticated
 * requests resolve their principal from the JWT instead (see
 * {@link JwtAuthenticationFilter}), so this is not on the hot path.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Loads the credentials Spring Security compares a login attempt against.
     *
     * <p>Called by {@code AuthenticationManager} from {@code AuthController.login}
     * — the password hash it returns is what the supplied password is checked
     * against. Nothing else calls this.
     *
     * <p>A disabled account is returned as disabled rather than hidden, so
     * Spring reports it as a disabled account rather than a bad password.
     *
     * @throws UsernameNotFoundException if no such user exists
     */
    @Override
    public UserDetails loadUserByUsername(String username) {
        var user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown username: " + username));

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPassword())
                .disabled(!user.isEnabled())
                .authorities("ROLE_USER")
                .build();
    }
}
