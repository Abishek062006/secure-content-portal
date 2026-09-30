package com.secureportal.hackathon;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HackathonProblemStatementRepository extends JpaRepository<HackathonProblemStatement, Long> {

    List<HackathonProblemStatement> findByHackathonIdOrderByIdAsc(Long hackathonId);

    Optional<HackathonProblemStatement> findByIdAndHackathonId(Long id, Long hackathonId);

    long countByHackathonId(Long hackathonId);
}
