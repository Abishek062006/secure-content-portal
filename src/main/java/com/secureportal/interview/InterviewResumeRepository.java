package com.secureportal.interview;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface InterviewResumeRepository extends JpaRepository<InterviewResume, Long> {

    Optional<InterviewResume> findByUserId(Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM InterviewResume r WHERE r.userId = :userId")
    int deleteForUser(@Param("userId") Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM InterviewResume r WHERE r.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
