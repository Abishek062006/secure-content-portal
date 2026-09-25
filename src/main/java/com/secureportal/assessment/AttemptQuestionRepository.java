package com.secureportal.assessment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AttemptQuestionRepository extends JpaRepository<AttemptQuestion, Long> {

    List<AttemptQuestion> findByAttemptIdOrderByPositionAsc(UUID attemptId);

    List<AttemptQuestion> findByAttemptIdIn(Collection<UUID> attemptIds);
}
