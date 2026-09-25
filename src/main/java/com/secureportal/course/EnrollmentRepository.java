package com.secureportal.course;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    Optional<Enrollment> findByUserIdAndCourseId(Long userId, UUID courseId);

    List<Enrollment> findByUserId(Long userId);
}
