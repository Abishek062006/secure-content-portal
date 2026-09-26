package com.secureportal.hackathon;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HackathonTeamRepository extends JpaRepository<HackathonTeam, Long> {

    Optional<HackathonTeam> findByInviteCode(String inviteCode);

    /** Locks the team for the rest of the transaction, so two people joining the last place can't both get it. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM HackathonTeam t WHERE t.id = :id")
    Optional<HackathonTeam> findForUpdate(@Param("id") Long id);

    @Query("SELECT t FROM HackathonTeam t WHERE t.id = (SELECT m.teamId FROM HackathonTeamMember m WHERE m.hackathonId = :hackathonId AND m.userId = :userId)")
    Optional<HackathonTeam> findOfMember(@Param("hackathonId") Long hackathonId, @Param("userId") Long userId);

    List<HackathonTeam> findByHackathonId(Long hackathonId);
}
