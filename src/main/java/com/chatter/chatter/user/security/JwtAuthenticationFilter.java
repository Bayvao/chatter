package com.chatter.chatter.user.security;

import java.io.IOException;
import java.util.List;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Turns a {@code Authorization: Bearer ...} header into an authenticated
 * security context, once per HTTP request.
 *
 * <p>Registered into the filter chain by {@code SecurityConfig}. Covers REST
 * only — the WebSocket handshake carries no header, so STOMP is authenticated
 * separately on its CONNECT frame by {@code StompAuthChannelInterceptor}.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    /**
     * Authenticates the request if it carries a valid bearer token, then hands
     * on down the chain.
     *
     * <p>Called by the servlet container for every request. Deliberately never
     * rejects: a missing or bad token simply leaves the context empty, and
     * Spring Security's authorization rules decide whether that is allowed.
     * This is what lets {@code /api/auth/**} stay public while everything else
     * requires a token.
     *
     * <p>An existing authentication is left alone, so the filter cannot clobber
     * a principal established earlier in the chain.
     */
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            AuthenticatedUser principal = tokenProvider.parse(header.substring(BEARER_PREFIX.length()));
            if (principal != null) {
                var authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }
}
