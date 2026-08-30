package com.chatter.chatter.chat.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.chatter.chatter.common.Ids;

@Entity
@Table(name = "chats", schema = "chat")
@Getter
@Setter
@NoArgsConstructor
public class Chat {

    @Id
    private UUID id;

    @Column(name = "is_group", nullable = false)
    private boolean group;

    @Column(length = 200)
    private String title;

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    /** Plain value — crosses the user boundary, so no association. */
    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    public static Chat directChat(UUID createdBy) {
        Chat chat = new Chat();
        chat.id = Ids.newId();
        chat.group = false;
        chat.createdBy = createdBy;
        return chat;
    }
}
