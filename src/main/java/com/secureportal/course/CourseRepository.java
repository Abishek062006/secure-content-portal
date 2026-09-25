package com.secureportal.course;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface CourseRepository extends JpaRepository<Course, UUID> {

    @Modifying
    @Query("UPDATE Course c SET c.viewCount = c.viewCount + 1, c.lastViewedAt = :now WHERE c.id = :id")
    void recordView(@Param("id") UUID id, @Param("now") Instant now);
}
