package com.chatter.chatter.user.security;

import java.security.Principal;
import java.util.UUID;

/**
 * The authentication principal, built straight from validated JWT claims —
 * no database lookup per request.
 *
 * <p>Implements {@link Principal} so the same object doubles as the STOMP
 * session user, with {@code getName()} returning the username that
 * {@code /user/{name}/...} destinations resolve against.
 */
public record AuthenticatedUser(UUID id, String username) implements Principal {

    @Override
    public String getName() {
        return username;
    }
}
