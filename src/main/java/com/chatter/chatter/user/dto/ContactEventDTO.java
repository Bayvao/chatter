package com.chatter.chatter.user.dto;

/**
 * What a client receives on {@code /user/queue/contacts}.
 *
 * <p>{@code type} is a {@code ContactChanged.Type} name, and {@code user} is
 * always the other party from the recipient's point of view, so the client can
 * update its lists without resolving ids.
 */
public record ContactEventDTO(String type, UserDTO user) {
}
