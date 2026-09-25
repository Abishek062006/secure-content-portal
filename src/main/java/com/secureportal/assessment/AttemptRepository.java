package com.secureportal.assessment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface AttemptRepository extends JpaRepository<Attempt, UUID> {

    List<Attempt> findByUserIdAndAssessmentIdOrderByStartedAtDesc(Long userId, UUID assessmentId);

    List<Attempt> findByUserIdAndAssessmentIdIn(Long userId, Collection<UUID> assessmentIds);

    Optional<Attempt> findFirstByUserIdAndAssessmentIdAndStatus(Long userId, UUID assessmentId, AttemptStatus status);

    long countByUserIdAndAssessmentId(Long userId, UUID assessmentId);

    List<Attempt> findByAssessmentIdInAndStatus(Collection<UUID> assessmentIds, AttemptStatus status);

    @Query("SELECT DISTINCT a.assessmentId FROM Attempt a WHERE a.userId = :userId AND a.passed = true")
    Set<UUID> passedAssessmentIds(@Param("userId") Long userId);
}
