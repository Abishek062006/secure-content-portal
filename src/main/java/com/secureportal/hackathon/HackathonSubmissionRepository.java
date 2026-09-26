package com.secureportal.hackathon;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HackathonSubmissionRepository extends JpaRepository<HackathonSubmission, Long> {

    Optional<HackathonSubmission> findByTeamId(Long teamId);

    Optional<HackathonSubmission> findByIdAndHackathonId(Long id, Long hackathonId);

    List<HackathonSubmission> findByHackathonIdOrderByIdAsc(Long hackathonId);
}
