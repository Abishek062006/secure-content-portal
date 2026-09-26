package com.secureportal.jobs;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface GenerationJobRepository extends JpaRepository<GenerationJob, UUID> {

    List<GenerationJob> findTop10ByCourseIdOrderByCreatedAtDesc(UUID courseId);

    List<GenerationJob> findByStatusIn(Collection<JobStatus> statuses);

    boolean existsByLessonIdAndStatusIn(UUID lessonId, Collection<JobStatus> statuses);
}
