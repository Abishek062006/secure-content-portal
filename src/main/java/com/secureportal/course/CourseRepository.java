package com.secureportal.course;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CourseRepository extends JpaRepository<Course, UUID> {

    List<Course> findByStatusOrderByCreatedAtDesc(CourseStatus status);

    @Query("SELECT DISTINCT c.category FROM Course c WHERE c.category IS NOT NULL AND c.category != ''")
    List<String> findDistinctCategories();

    @Modifying
    @Query("UPDATE Course c SET c.viewCount = c.viewCount + 1, c.lastViewedAt = :now WHERE c.id = :id")
    void recordView(@Param("id") UUID id, @Param("now") Instant now);
}
