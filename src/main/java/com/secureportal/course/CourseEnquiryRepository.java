package com.secureportal.course;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseEnquiryRepository extends JpaRepository<CourseEnquiry, Long> {

    List<CourseEnquiry> findByStatusOrderByCreatedAtDesc(EnquiryStatus status);

    List<CourseEnquiry> findAllByOrderByCreatedAtDesc();
}
