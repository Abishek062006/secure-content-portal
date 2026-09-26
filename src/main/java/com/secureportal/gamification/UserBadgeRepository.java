package com.secureportal.gamification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserBadgeRepository extends JpaRepository<UserBadge, Long> {
    List<UserBadge> findByUserId(Long userId);
    Optional<UserBadge> findByUserIdAndBadgeId(Long userId, String badgeId);
    boolean existsByUserIdAndBadgeId(Long userId, String badgeId);
    long countByUserId(Long userId);
}
