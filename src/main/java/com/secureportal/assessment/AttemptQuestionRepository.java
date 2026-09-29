package com.secureportal.assessment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AttemptQuestionRepository extends JpaRepository<AttemptQuestion, Long> {

    List<AttemptQuestion> findByAttemptIdOrderByPositionAsc(UUID attemptId);

    List<AttemptQuestion> findByAttemptIdIn(Collection<UUID> attemptIds);

    /** {difficulty, correct} for every answered question across every submitted attempt this learner has
     *  made — the raw material for "how do you do on hard questions specifically". */
    @Query("SELECT q.difficulty, aq.correct FROM AttemptQuestion aq, Attempt a, Question q "
            + "WHERE aq.attemptId = a.id AND aq.questionId = q.id AND a.userId = :userId AND a.status = :status "
            + "AND aq.correct IS NOT NULL")
    List<Object[]> difficultyResultsForUser(@Param("userId") Long userId, @Param("status") AttemptStatus status);
}
