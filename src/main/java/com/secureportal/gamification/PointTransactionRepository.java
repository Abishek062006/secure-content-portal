package com.secureportal.gamification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {

    List<PointTransaction> findByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByUserIdAndActionTypeAndSourceId(Long userId, String actionType, String sourceId);

    @Query("SELECT pt.userId AS userId, SUM(pt.amount) AS totalPoints " +
           "FROM PointTransaction pt " +
           "WHERE pt.createdAt >= :startDate " +
           "AND (:stream IS NULL OR pt.stream = :stream) " +
           "GROUP BY pt.userId " +
           "ORDER BY SUM(pt.amount) DESC")
    List<Object[]> findLeaderboardByDateRangeAndStream(
            @Param("startDate") Instant startDate,
            @Param("stream") String stream
    );

    @Query("SELECT pt.userId AS userId, SUM(pt.amount) AS totalPoints " +
           "FROM PointTransaction pt " +
           "WHERE (:stream IS NULL OR pt.stream = :stream) " +
           "GROUP BY pt.userId " +
           "ORDER BY SUM(pt.amount) DESC")
    List<Object[]> findLeaderboardAllTimeAndStream(@Param("stream") String stream);

    long countByUserIdAndActionTypeAndCreatedAtAfter(Long userId, String actionType, Instant after);

    long countByUserIdAndActionType(Long userId, String actionType);

    long countByUserIdAndActionTypeAndStream(Long userId, String actionType, String stream);

    @Query("SELECT pt FROM PointTransaction pt " +
           "WHERE (:userId IS NULL OR pt.userId = :userId) " +
           "AND (:actionType IS NULL OR pt.actionType = :actionType) " +
           "AND (:stream IS NULL OR pt.stream = :stream) " +
           "ORDER BY pt.createdAt DESC")
    List<PointTransaction> filterTransactions(
            @Param("userId") Long userId,
            @Param("actionType") String actionType,
            @Param("stream") String stream
    );

    List<PointTransaction> findAllByOrderByCreatedAtDesc();
}

