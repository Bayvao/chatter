package com.chatter.chatter.user.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.chatter.chatter.user.model.Block;

@Repository
public interface BlockRepository extends JpaRepository<Block, Block.Key> {

    /**
     * Whether a block exists in <em>either</em> direction between two users.
     *
     * <p>The gate on every 1:1 message send, reached through
     * {@code RelationshipDirectory.isBlockedEitherWay}. Symmetric because a
     * block silences the conversation both ways: the blocked party must not be
     * able to keep talking, and the blocker should not be able to either while
     * the bar stands.
     *
     * <p>One query rather than two reads, and both directions are indexed — the
     * primary key covers {@code blocker_id}, {@code idx_blocks_blocked} the
     * other.
     */
    @Query("""
            select count(b) > 0 from Block b
            where (b.blockerId = :userA and b.blockedId = :userB)
               or (b.blockerId = :userB and b.blockedId = :userA)
            """)
    boolean existsEitherWay(@Param("userA") UUID userA, @Param("userB") UUID userB);

    /**
     * One specific block, for unblocking.
     *
     * <p>Directional on purpose: unblocking must lift only the caller's own
     * bar, never one held against them.
     */
    Optional<Block> findByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

    /**
     * Everyone this user has blocked.
     *
     * <p>Used to filter search results and the contact list.
     */
    @Query("select b.blockedId from Block b where b.blockerId = :userId")
    List<UUID> findBlockedIdsBy(@Param("userId") UUID userId);

    /**
     * Everyone involved in a block with this user, in either direction.
     *
     * <p>Search hides both: someone you blocked, and someone who blocked you.
     * Showing the latter would let a blocked user find their way back to a
     * profile and a request button.
     */
    @Query("""
            select case when b.blockerId = :userId then b.blockedId else b.blockerId end
            from Block b
            where b.blockerId = :userId or b.blockedId = :userId
            """)
    List<UUID> findAllInvolvedWith(@Param("userId") UUID userId);
}
