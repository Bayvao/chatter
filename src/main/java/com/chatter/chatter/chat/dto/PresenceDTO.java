package com.chatter.chatter.chat.dto;

import java.time.Instant;
import java.util.UUID;

public record PresenceDTO(UUID userId, boolean online, Instant lastSeen) {
}
