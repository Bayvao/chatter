package com.chatter.chatter.chat.model;

/** Ordinals are persisted and compared by rank; append only, never reorder. */
public enum MessageStatus {
    SENT,
    DELIVERED,
    READ
}
