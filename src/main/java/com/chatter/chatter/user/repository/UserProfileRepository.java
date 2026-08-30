package com.chatter.chatter.user.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.chatter.chatter.user.model.UserProfile;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {
}
