package com.secureportal.hackathon;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface HackathonJudgeRepository extends JpaRepository<HackathonJudge, Long> {

    boolean existsByHackathonIdAndUserId(Long hackathonId, Long userId);

    List<HackathonJudge> findByHackathonIdOrderByIdAsc(Long hackathonId);

    List<HackathonJudge> findByUserId(Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM HackathonJudge j WHERE j.hackathonId = :hackathonId AND j.userId = :userId")
    int remove(@Param("hackathonId") Long hackathonId, @Param("userId") Long userId);
}
