package com.secureportal.interview;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MockInterviewQuestionRepository extends JpaRepository<MockInterviewQuestion, Long> {

    List<MockInterviewQuestion> findBySessionIdOrderByQuestionIndexAsc(Long sessionId);

    /** Always scoped to the session, so a question id from another interview never resolves. */
    Optional<MockInterviewQuestion> findByIdAndSessionId(Long id, Long sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT q FROM MockInterviewQuestion q WHERE q.id = :id AND q.sessionId = :sessionId")
    Optional<MockInterviewQuestion> findForUpdate(@Param("id") Long id, @Param("sessionId") Long sessionId);

    long countBySessionIdAndParentQuestionIdIsNotNull(Long sessionId);

    boolean existsByParentQuestionId(Long parentQuestionId);

    /**
     * Moves every question after {@code index} down by {@code by} places. Used in two steps (out of the way, then back one further)
     * so a follow-up can slip in after its parent without ever breaking the unique (session, index) rule.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE MockInterviewQuestion q SET q.questionIndex = q.questionIndex + :by WHERE q.sessionId = :sessionId AND q.questionIndex > :index")
    int shiftAfter(@Param("sessionId") Long sessionId, @Param("index") int index, @Param("by") int by);
}
