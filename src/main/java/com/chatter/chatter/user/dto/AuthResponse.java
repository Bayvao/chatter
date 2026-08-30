package com.chatter.chatter.user.dto;

public record AuthResponse(String token, UserDTO user) {
}
