package com.chatter.chatter.user.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import com.chatter.chatter.user.dto.AuthResponse;
import com.chatter.chatter.user.dto.LoginRequest;
import com.chatter.chatter.user.dto.RegisterRequest;
import com.chatter.chatter.user.dto.UserDTO;
import com.chatter.chatter.user.model.User;
import com.chatter.chatter.user.security.AuthenticatedUser;
import com.chatter.chatter.user.security.JwtTokenProvider;
import com.chatter.chatter.user.service.UserService;

/**
 * Sign-up, sign-in, and "who am I".
 *
 * <p>The register and login routes are the only unauthenticated endpoints in
 * the application (see {@code SecurityConfig}), and the only place a JWT is
 * issued.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;

    public AuthController(UserService userService, AuthenticationManager authenticationManager,
                           JwtTokenProvider tokenProvider) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
    }

    /**
     * Creates an account and signs the new user straight in.
     *
     * <p>Used by the Register screen. Returning a token here rather than
     * redirecting to login spares the client a second round trip and a second
     * password submission.
     *
     * @return 201 with the token and the new user
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = userService.register(request);
        String token = tokenProvider.generateToken(user.getId(), user.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(new AuthResponse(token, UserDTO.from(user)));
    }

    /**
     * Exchanges a username and password for a token.
     *
     * <p>Used by the Login screen. The password check is delegated to Spring's
     * {@code AuthenticationManager} — which reaches
     * {@code AppUserDetailsService} for the stored hash — so bad credentials
     * surface as a 401 from the security layer rather than as a branch here.
     */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        User user = userService.getByUsername(request.username());
        return new AuthResponse(tokenProvider.generateToken(user.getId(), user.getUsername()), UserDTO.from(user));
    }

    /**
     * The signed-in user, re-read from the database.
     *
     * <p>Used by the frontend on page load to restore a session from a stored
     * token: it both validates the token still works and returns fresh user
     * details. The lookup matters — the token's claims are a snapshot from
     * issue time, so a display name changed since would otherwise be stale.
     */
    @GetMapping("/me")
    public UserDTO currentUser(@AuthenticationPrincipal AuthenticatedUser principal) {
        return UserDTO.from(userService.getById(principal.id()));
    }
}
