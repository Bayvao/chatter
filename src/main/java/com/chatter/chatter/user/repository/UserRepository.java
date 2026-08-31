package com.chatter.chatter.user.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.chatter.chatter.user.model.User;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Looks a user up by their unique username.
     *
     * <p>Used on the login path — by {@code AppUserDetailsService} for the
     * password hash and by {@code UserService.getByUsername} afterwards.
     */
    Optional<User> findByUsername(String username);

    /**
     * Looks a user up by e-mail.
     *
     * <p>Present for password-reset and e-mail-login flows that do not exist
     * yet; nothing calls it today. Note e-mail is optional, so this can never be
     * the only way to find an account.
     */
    Optional<User> findByEmail(String email);

    /**
     * Whether a username is taken.
     *
     * <p>The pre-check in {@code UserService.register}. It exists for the error
     * message, not for correctness — a unique constraint backs the column, since
     * two simultaneous registrations could both pass this check.
     */
    boolean existsByUsername(String username);

    /**
     * Whether an e-mail is taken.
     *
     * <p>Checked at registration only when an e-mail was actually supplied —
     * blank ones are stored as NULL, and any number of accounts may have none.
     */
    boolean existsByEmail(String email);

    /**
     * Finds users whose username or name contains the search term.
     *
     * <p>Backs the search box, through {@code UserService.search}.
     * {@code excludeUserId} keeps the searcher out of their own results.
     *
     * <p>A leading-wildcard {@code LIKE}, which no B-tree index can serve: it
     * scans. Fine at this size, and the point at which it stops being fine is
     * the point to move search to Postgres full-text or Elasticsearch.
     */
    @Query("""
            select u from User u
            where u.id <> :excludeUserId
              and (lower(u.username) like lower(concat('%', :term, '%'))
                   or lower(u.firstName) like lower(concat('%', :term, '%'))
                   or lower(u.lastName) like lower(concat('%', :term, '%')))
            order by u.username
            """)
    List<User> search(@Param("term") String term, @Param("excludeUserId") UUID excludeUserId);
}
