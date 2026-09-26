package com.secureportal.gamification;

import com.secureportal.user.Role;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserGamificationRepository extends JpaRepository<UserGamification, Long> {

    /**
     * Creates the learner's row if they don't have one yet; harmless when they do. It is an upsert, not INSERT IGNORE: an ignored
     * duplicate only takes a shared lock, and several requests holding shared locks that then all ask for the exclusive one
     * deadlock. This takes the exclusive lock at once, so requests for one learner simply queue.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "INSERT INTO user_gamification (user_id) VALUES (:userId) ON DUPLICATE KEY UPDATE user_id = user_id", nativeQuery = true)
    void ensureRow(@Param("userId") Long userId);

    /** The one place points change: atomic in the database, never below zero. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE user_gamification SET total_points = GREATEST(0, total_points + :amount), updated_at = UTC_TIMESTAMP(6) "
            + "WHERE user_id = :userId", nativeQuery = true)
    int addPoints(@Param("userId") Long userId, @Param("amount") int amount);

    /** Locks the row for the rest of the transaction, so two check-ins from one learner run one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM UserGamification g WHERE g.userId = :userId")
    Optional<UserGamification> findForUpdate(@Param("userId") Long userId);

    @Query("SELECT g FROM UserGamification g, User u WHERE u.id = g.userId AND u.role <> :admin "
            + "ORDER BY g.totalPoints DESC, g.userId ASC")
    List<UserGamification> topLearners(@Param("admin") Role admin, Pageable page);

    @Query("SELECT COUNT(g) FROM UserGamification g, User u WHERE u.id = g.userId AND u.role <> :admin")
    long countLearners(@Param("admin") Role admin);

    @Query("SELECT COALESCE(SUM(g.totalPoints), 0) FROM UserGamification g, User u WHERE u.id = g.userId AND u.role <> :admin")
    long sumPoints(@Param("admin") Role admin);

    @Query("SELECT COUNT(g) FROM UserGamification g, User u WHERE u.id = g.userId AND u.role <> :admin AND g.currentStreak > 0")
    long countWithStreak(@Param("admin") Role admin);

    /** 1 + the number of learners with strictly more points. Admins never take part. */
    @Query("SELECT COUNT(g) + 1 FROM UserGamification g, User u WHERE u.id = g.userId AND u.role <> :admin AND g.totalPoints > :points")
    long rankOf(@Param("points") int points, @Param("admin") Role admin);
}
