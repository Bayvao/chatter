package com.chatter.chatter.chat.projection;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.chatter.chatter.user.event.UserProfileChanged;

/**
 * Keeps {@code chat.messages}' denormalised sender snapshot current. The chat
 * module cannot join to {@code app_user.users}, so a rename would otherwise
 * leave every past message showing the old name forever.
 *
 * <p>The {@code sender_version <} guard is load-bearing: events are
 * at-least-once and can arrive out of order, and without it a rename followed
 * quickly by another can land backwards and stick.
 *
 * <p>Becomes a {@code @KafkaListener} in Phase 5 with the same body.
 */
@Component
public class SenderSnapshotProjection {

    private static final String UPDATE_SNAPSHOT = """
            UPDATE chat.messages
               SET sender_name = ?, sender_avatar_url = ?, sender_version = ?
             WHERE sender_id = ? AND sender_version < ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public SenderSnapshotProjection(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Rewrites the sender snapshot on every message this user has sent.
     *
     * <p>Invoked by Spring for each {@link UserProfileChanged} published by
     * {@code ProfileService}, once that transaction commits.
     *
     * <p>A single bulk {@code UPDATE} rather than loading entities: this can
     * touch every message a long-standing user ever sent, and doing that through
     * JPA would pull all of them into memory.
     *
     * <p>The {@code sender_version <} predicate in the statement is what makes
     * it safe to replay — an event delivered twice, or two renames landing
     * backwards, cannot overwrite newer data.
     *
     * <p>REQUIRES_NEW because AFTER_COMMIT runs once the publishing transaction
     * has already committed; joining it is not possible.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProfileChanged(UserProfileChanged event) {
        jdbcTemplate.update(UPDATE_SNAPSHOT,
                event.displayName(), event.avatarUrl(), event.version(), event.userId(), event.version());
    }
}
