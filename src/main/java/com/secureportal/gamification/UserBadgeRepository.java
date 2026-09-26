package com.secureportal.gamification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface UserBadgeRepository extends JpaRepository<UserBadge, Long> {

    List<UserBadge> findByUserId(Long userId);

    long countByUserId(Long userId);

    /** Gives the badge unless the learner already has it; returns 1 only for the request that actually unlocked it. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "INSERT IGNORE INTO user_badges (user_id, badge_id, unlocked_at) VALUES (:userId, :badgeId, UTC_TIMESTAMP(6))",
            nativeQuery = true)
    int unlockOnce(@Param("userId") Long userId, @Param("badgeId") String badgeId);

    /** {learner id, badges held} for just these learners. */
    @Query("SELECT ub.userId, COUNT(ub) FROM UserBadge ub WHERE ub.userId IN :userIds GROUP BY ub.userId")
    List<Object[]> countByUsers(@Param("userIds") Collection<Long> userIds);

    @Query("SELECT COUNT(ub) FROM UserBadge ub")
    long countAll();
}
