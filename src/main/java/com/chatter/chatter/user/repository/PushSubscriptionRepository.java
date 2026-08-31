package com.chatter.chatter.user.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.chatter.chatter.user.model.PushSubscription;

@Repository
public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, UUID> {

    /**
     * Every browser a user has registered for push.
     *
     * <p>Read by {@code WebPushSender} when notifying someone offline — a list
     * because one person may have several browsers or devices, and all of them
     * should light up.
     */
    List<PushSubscription> findByUserId(UUID userId);

    /**
     * Finds a registration by its endpoint URL.
     *
     * <p>What makes {@code subscribe} idempotent and lets an endpoint be
     * re-pointed at its current owner. The endpoint is uniquely indexed, so this
     * returns at most one row — deliberately, since a push service reassigning
     * an endpoint could otherwise leave two users sharing a browser.
     */
    Optional<PushSubscription> findByEndpoint(String endpoint);

    /**
     * Removes a registration by endpoint.
     *
     * <p>Unused today: {@code unsubscribe} deletes the entity it has already
     * loaded and ownership-checked, and {@code WebPushSender} does the same when
     * a push service reports the endpoint gone. Kept as the direct route for a
     * cleanup job that has only the endpoint.
     */
    void deleteByEndpoint(String endpoint);
}
