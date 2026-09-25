package com.secureportal.course;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LessonProgressRepository extends JpaRepository<LessonProgress, Long> {

    Optional<LessonProgress> findByUserIdAndLessonId(Long userId, UUID lessonId);

    List<LessonProgress> findByUserIdAndCourseId(Long userId, UUID courseId);

    long countByUserIdAndCourseIdAndCompletedTrue(Long userId, UUID courseId);

    /** {course id, completed lessons} for one learner across all courses, for the catalog. */
    @Query("SELECT p.courseId, COUNT(p) FROM LessonProgress p WHERE p.userId = :userId AND p.completed = true GROUP BY p.courseId")
    List<Object[]> countCompletedPerCourse(@Param("userId") Long userId);
}
