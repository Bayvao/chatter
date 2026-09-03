package com.chatter.chatter.user.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One user barring another from contacting them.
 *
 * <p>Deliberately not a flag on {@link Contact}: a block must outlive the
 * contact row, or removing someone would clear the block that keeps them away.
 * It also means a stranger can be blocked, which a flag on a row that does not
 * exist cannot express.
 *
 * <p>One-directional by design. Alice blocking Bob is not Bob blocking Alice,
 * and unblocking must only lift the blocker's own bar.
 */
@Entity
@Table(name = "blocks", schema = "app_user")
@IdClass(Block.Key.class)
@Getter
@NoArgsConstructor
public class Block {

    @Id
    @Column(name = "blocker_id")
    private UUID blockerId;

    @Id
    @Column(name = "blocked_id")
    private UUID blockedId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    /**
     * Records a block.
     *
     * <p>Called from {@code ContactService.setBlocked}. No contact row is
     * required — blocking someone who only ever messaged you is the case that
     * matters most.
     */
    public Block(UUID blockerId, UUID blockedId) {
        this.blockerId = blockerId;
        this.blockedId = blockedId;
    }

    /**
     * The composite key: one row per ordered pair, so blocking twice is a no-op
     * rather than a duplicate.
     */
    public record Key(UUID blockerId, UUID blockedId) implements Serializable {
    }
}
