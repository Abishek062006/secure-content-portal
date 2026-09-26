package com.secureportal.course;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LessonNoteRepository extends JpaRepository<LessonNote, Long> {

    List<LessonNote> findByUserIdAndLessonIdOrderByVideoSecondsAscIdAsc(Long userId, UUID lessonId);

    Optional<LessonNote> findByIdAndUserIdAndLessonId(Long id, Long userId, UUID lessonId);
}
