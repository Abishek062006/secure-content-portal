package com.secureportal.course;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface LessonRepository extends JpaRepository<Lesson, UUID> {

    List<Lesson> findByCourseId(UUID courseId);

    List<Lesson> findByModuleIdOrderByPositionAsc(UUID moduleId);

    long countByModuleId(UUID moduleId);

    long countByCourseId(UUID courseId);

    java.util.List<Lesson> findByHlsStatus(HlsStatus status);

    /** {course id, number of lessons} for every course, for the catalog. */
    @Query("SELECT l.courseId, COUNT(l) FROM Lesson l GROUP BY l.courseId")
    List<Object[]> countLessonsPerCourse();
}
