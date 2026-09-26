package com.secureportal.interview;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MockInterviewQuestionRepository extends JpaRepository<MockInterviewQuestion, Long> {
    List<MockInterviewQuestion> findBySessionIdOrderByQuestionIndexAsc(Long sessionId);
}
