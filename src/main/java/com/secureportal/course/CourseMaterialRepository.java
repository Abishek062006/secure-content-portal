package com.secureportal.course;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CourseMaterialRepository extends JpaRepository<CourseMaterial, UUID> {

    List<CourseMaterial> findByCourseIdOrderByCreatedAtAsc(UUID courseId);

    List<CourseMaterial> findByModuleIdOrderByCreatedAtAsc(UUID moduleId);
}
