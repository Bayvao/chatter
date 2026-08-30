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

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

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
