package com.secureportal.interview;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MockInterviewSessionRepository extends JpaRepository<MockInterviewSession, Long> {
    List<MockInterviewSession> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<MockInterviewSession> findByStatusOrderByCreatedAtDesc(String status);
    long countByStatus(String status);
}
