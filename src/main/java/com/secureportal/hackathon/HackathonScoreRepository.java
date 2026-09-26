package com.secureportal.hackathon;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface HackathonScoreRepository extends JpaRepository<HackathonScore, Long> {

    Optional<HackathonScore> findBySubmissionIdAndJudgeId(Long submissionId, Long judgeId);

    List<HackathonScore> findBySubmissionIdIn(Collection<Long> submissionIds);

    @Query("SELECT s FROM HackathonScore s WHERE s.judgeId = :judgeId AND s.submissionId IN :submissionIds")
    List<HackathonScore> ofJudge(@Param("judgeId") Long judgeId, @Param("submissionIds") Collection<Long> submissionIds);
}
