package com.secureportal.assessment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssessmentRepository extends JpaRepository<Assessment, UUID> {

    List<Assessment> findByCourseId(UUID courseId);

    Optional<Assessment> findByModuleId(UUID moduleId);

    Optional<Assessment> findByCourseIdAndModuleIdIsNull(UUID courseId);
}
