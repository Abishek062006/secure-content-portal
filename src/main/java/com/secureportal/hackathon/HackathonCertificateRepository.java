package com.secureportal.hackathon;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HackathonCertificateRepository extends JpaRepository<HackathonCertificate, Long> {

    Optional<HackathonCertificate> findByHackathonIdAndUserId(Long hackathonId, Long userId);

    Optional<HackathonCertificate> findByCode(String code);

    List<HackathonCertificate> findByHackathonId(Long hackathonId);

    List<HackathonCertificate> findByUserIdOrderByIssuedAtDesc(Long userId);

    boolean existsByHackathonIdAndUserId(Long hackathonId, Long userId);

    boolean existsByCode(String code);
}
