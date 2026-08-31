package com.chatter.chatter.chat.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chatter.chatter.chat.dto.PresenceDTO;
import com.chatter.chatter.chat.port.PresenceStore;

/**
 * Presence on demand, for the initial render. Live changes arrive over
 * {@code /topic/presence} instead of by polling this.
 */
@RestController
@RequestMapping("/api/presence")
public class PresenceController {

    private final PresenceStore presenceStore;

    public PresenceController(PresenceStore presenceStore) {
        this.presenceStore = presenceStore;
    }

    @GetMapping("/{userId}")
    public PresenceDTO presenceOf(@PathVariable UUID userId) {
        return toDto(userId);
    }

    @GetMapping
    public List<PresenceDTO> presenceOf(@RequestParam("userIds") List<UUID> userIds) {
        return userIds.stream().map(this::toDto).toList();
    }

    private PresenceDTO toDto(UUID userId) {
        return new PresenceDTO(userId, presenceStore.isOnline(userId),
                presenceStore.lastSeen(userId).orElse(null));
    }
}
