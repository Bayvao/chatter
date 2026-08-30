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

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = userService.register(request);
        String token = tokenProvider.generateToken(user.getId(), user.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(new AuthResponse(token, UserDTO.from(user)));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        User user = userService.getByUsername(request.username());
        return new AuthResponse(tokenProvider.generateToken(user.getId(), user.getUsername()), UserDTO.from(user));
    }

    @GetMapping("/me")
    public UserDTO currentUser(@AuthenticationPrincipal AuthenticatedUser principal) {
        return UserDTO.from(userService.getById(principal.id()));
    }
}
