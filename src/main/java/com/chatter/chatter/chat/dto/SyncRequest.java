package com.chatter.chatter.chat.dto;

import java.util.Map;
import java.util.UUID;

/**
 * Per-chat cursors: the highest {@code seq} the client already holds, keyed by
 * chat. Deliberately not a timestamp — see {@code SyncController}.
 */
public record SyncRequest(Map<UUID, Long> cursors) {
}
