package com.secureportal.hackathon;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HackathonRegistrationRepository extends JpaRepository<HackathonRegistration, Long> {
    boolean existsByHackathonIdAndUserId(Long hackathonId, Long userId);
    Optional<HackathonRegistration> findByHackathonIdAndUserId(Long hackathonId, Long userId);
    List<HackathonRegistration> findByUserId(Long userId);
    long countByHackathonId(Long hackathonId);
}
