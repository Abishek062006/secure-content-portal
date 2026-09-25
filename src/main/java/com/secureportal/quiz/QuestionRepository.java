package com.secureportal.quiz;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QuestionRepository extends JpaRepository<Question, UUID> {

    List<Question> findByCourseIdOrderByCreatedAtAsc(UUID courseId);

    List<Question> findByLessonIdOrderByCreatedAtAsc(UUID lessonId);
}
