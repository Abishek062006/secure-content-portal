package com.secureportal.interview;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MockInterviewSessionRepository extends JpaRepository<MockInterviewSession, Long> {

    Optional<MockInterviewSession> findByIdAndUserId(Long id, Long userId);

    List<MockInterviewSession> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable page);

    long countByUserIdAndCreatedAtAfter(Long userId, Instant after);

    long countByStatus(String status);

    /** A learner has one interview open at a time; starting another closes the earlier one. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE MockInterviewSession s SET s.status = 'ABANDONED' WHERE s.userId = :userId AND s.status = 'IN_PROGRESS'")
    int abandonOpen(@Param("userId") Long userId);

    @Query("SELECT s.track, COUNT(s) FROM MockInterviewSession s GROUP BY s.track")
    List<Object[]> countByTrack();

    @Query("SELECT s.difficulty, COUNT(s) FROM MockInterviewSession s GROUP BY s.difficulty")
    List<Object[]> countByDifficulty();

    @Query("SELECT s.stream, AVG(s.overallScore) FROM MockInterviewSession s WHERE s.status = 'COMPLETED' GROUP BY s.stream")
    List<Object[]> averageScoreByStream();

    @Query("SELECT COALESCE(AVG(s.overallScore), 0) FROM MockInterviewSession s WHERE s.status = 'COMPLETED'")
    double averageScore();

    /** The newest sessions with who sat them: {session, user}. */
    @Query("SELECT s, u FROM MockInterviewSession s, User u WHERE u.id = s.userId ORDER BY s.createdAt DESC, s.id DESC")
    List<Object[]> recentWithUsers(Pageable page);
}
