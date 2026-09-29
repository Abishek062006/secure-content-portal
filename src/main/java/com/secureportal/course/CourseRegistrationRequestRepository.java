package com.secureportal.course;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseRegistrationRequestRepository extends JpaRepository<CourseRegistrationRequest, Long> {

    Optional<CourseRegistrationRequest> findByCourseIdAndUserId(UUID courseId, Long userId);

    List<CourseRegistrationRequest> findByCourseIdOrderByRequestedAtDesc(UUID courseId);

    List<CourseRegistrationRequest> findByStatusOrderByRequestedAtDesc(RegistrationStatus status);

    List<CourseRegistrationRequest> findAllByOrderByRequestedAtDesc();
}
