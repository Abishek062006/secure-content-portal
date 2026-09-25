package com.secureportal.course;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface CourseModuleRepository extends JpaRepository<CourseModule, UUID> {

    List<CourseModule> findByCourseIdOrderByPositionAsc(UUID courseId);

    long countByCourseId(UUID courseId);

    /** {course id, number of modules} for every course, for the catalog. */
    @Query("SELECT m.courseId, COUNT(m) FROM CourseModule m GROUP BY m.courseId")
    List<Object[]> countModulesPerCourse();
}
