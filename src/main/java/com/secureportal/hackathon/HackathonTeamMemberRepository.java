package com.secureportal.hackathon;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface HackathonTeamMemberRepository extends JpaRepository<HackathonTeamMember, Long> {

    List<HackathonTeamMember> findByTeamIdOrderByJoinedAtAscIdAsc(Long teamId);

    long countByTeamId(Long teamId);

    boolean existsByHackathonIdAndUserId(Long hackathonId, Long userId);

    List<HackathonTeamMember> findByTeamIdIn(Collection<Long> teamIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM HackathonTeamMember m WHERE m.teamId = :teamId AND m.userId = :userId")
    int remove(@Param("teamId") Long teamId, @Param("userId") Long userId);
}
