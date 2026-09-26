package com.secureportal.gamification;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {

    /**
     * Records an award unless one with the same dedupe key already exists for this learner. Returns 1 for the request that
     * created it and 0 for every repeat, so a lesson, quiz, course, day, hackathon or interview pays out exactly once, even
     * when two requests arrive together.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "INSERT IGNORE INTO point_transactions "
            + "(user_id, amount, action_type, description, stream, source_type, source_id, dedupe_key, course_id, created_at) "
            + "VALUES (:userId, :amount, :actionType, :description, :stream, :sourceType, :sourceId, :dedupeKey, :courseId, UTC_TIMESTAMP(6))",
            nativeQuery = true)
    int insertOnce(@Param("userId") Long userId, @Param("amount") int amount, @Param("actionType") String actionType,
                   @Param("description") String description, @Param("stream") String stream,
                   @Param("sourceType") String sourceType, @Param("sourceId") String sourceId,
                   @Param("dedupeKey") String dedupeKey, @Param("courseId") String courseId);

    /** How many things a learner finished (lessons, quizzes, courses) since a moment: what fills the daily ring. */
    @Query("SELECT COUNT(pt) FROM PointTransaction pt WHERE pt.userId = :userId AND pt.createdAt >= :since "
            + "AND pt.actionType IN ('LESSON_COMPLETED', 'QUIZ_PASSED', 'COURSE_COMPLETED')")
    long countActivitiesSince(@Param("userId") Long userId, @Param("since") Instant since);

    long countByUserIdAndActionType(Long userId, String actionType);

    long countByUserIdAndActionTypeAndStream(Long userId, String actionType, String stream);

    long countByUserIdAndActionTypeAndCreatedAtAfter(Long userId, String actionType, Instant after);

    /** {learner id, action count} for just these learners. */
    @Query("SELECT pt.userId, COUNT(pt) FROM PointTransaction pt WHERE pt.userId IN :userIds AND pt.actionType = :actionType GROUP BY pt.userId")
    List<Object[]> countByUsersAndAction(@Param("userIds") Collection<Long> userIds, @Param("actionType") String actionType);

    @Query("SELECT COALESCE(SUM(pt.amount), 0) FROM PointTransaction pt")
    long totalAwarded();

    /** A learner's own history, newest first. */
    List<PointTransaction> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable page);

    @Query("SELECT pt FROM PointTransaction pt WHERE (:userId IS NULL OR pt.userId = :userId) "
            + "AND (:actionType IS NULL OR pt.actionType = :actionType) AND (:stream IS NULL OR pt.stream = :stream) "
            + "ORDER BY pt.createdAt DESC, pt.id DESC")
    List<PointTransaction> filter(@Param("userId") Long userId, @Param("actionType") String actionType,
                                  @Param("stream") String stream, Pageable page);

    // ---- Leaderboards for a period and/or stream, summed from the ledger. Admins never appear. ----

    @Query(value = "SELECT pt.user_id, SUM(pt.amount) FROM point_transactions pt JOIN users u ON u.id = pt.user_id "
            + "WHERE u.role <> 'ADMIN' AND u.leaderboard_hidden = 0 AND pt.created_at >= :since AND (:stream IS NULL OR pt.stream = :stream) AND (:courseId IS NULL OR pt.course_id = :courseId) "
            + "GROUP BY pt.user_id ORDER BY SUM(pt.amount) DESC, pt.user_id ASC LIMIT :limit", nativeQuery = true)
    List<Object[]> topSince(@Param("since") Instant since, @Param("stream") String stream, @Param("courseId") String courseId, @Param("limit") int limit);

    @Query(value = "SELECT COUNT(*) FROM (SELECT pt.user_id FROM point_transactions pt JOIN users u ON u.id = pt.user_id "
            + "WHERE u.role <> 'ADMIN' AND u.leaderboard_hidden = 0 AND pt.created_at >= :since AND (:stream IS NULL OR pt.stream = :stream) AND (:courseId IS NULL OR pt.course_id = :courseId) "
            + "GROUP BY pt.user_id) ranked", nativeQuery = true)
    long countParticipantsSince(@Param("since") Instant since, @Param("stream") String stream, @Param("courseId") String courseId);

    @Query(value = "SELECT COALESCE(SUM(pt.amount), 0) FROM point_transactions pt "
            + "WHERE pt.user_id = :userId AND pt.created_at >= :since AND (:stream IS NULL OR pt.stream = :stream) AND (:courseId IS NULL OR pt.course_id = :courseId)", nativeQuery = true)
    long pointsSince(@Param("userId") Long userId, @Param("since") Instant since, @Param("stream") String stream, @Param("courseId") String courseId);

    @Query(value = "SELECT COUNT(*) + 1 FROM (SELECT pt.user_id FROM point_transactions pt JOIN users u ON u.id = pt.user_id "
            + "WHERE u.role <> 'ADMIN' AND u.leaderboard_hidden = 0 AND pt.created_at >= :since AND (:stream IS NULL OR pt.stream = :stream) AND (:courseId IS NULL OR pt.course_id = :courseId) "
            + "GROUP BY pt.user_id HAVING SUM(pt.amount) > :points) ahead", nativeQuery = true)
    long rankSince(@Param("since") Instant since, @Param("stream") String stream, @Param("courseId") String courseId, @Param("points") long points);
}
