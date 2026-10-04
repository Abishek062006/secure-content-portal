package com.secureportal.hackathon;

import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface HackathonRepository extends JpaRepository<Hackathon, Long> {

    @Query("SELECT h FROM Hackathon h WHERE (:stream IS NULL OR LOWER(h.stream) = LOWER(:stream)) AND (:mode IS NULL OR h.mode = :mode) "
            + "ORDER BY h.featured DESC, CASE WHEN h.eventStartDate IS NULL THEN 1 ELSE 0 END, h.eventStartDate ASC, h.id DESC")
    List<Hackathon> search(@Param("stream") String stream, @Param("mode") String mode, Pageable page);

    @Query("SELECT h FROM Hackathon h WHERE h.id IN (SELECT s.hackathonId FROM HackathonSave s WHERE s.userId = :userId) "
            + "ORDER BY CASE WHEN h.eventStartDate IS NULL THEN 1 ELSE 0 END, h.eventStartDate ASC, h.id DESC")
    List<Hackathon> savedBy(@Param("userId") Long userId, Pageable page);

    /** Hosted events still being built whose submission deadline falls before `until`; locked so two servers don't both send a reminder. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM Hackathon h WHERE h.kind = 'HOSTED' AND h.resultsPublishedAt IS NULL "
            + "AND h.eventStartDate <= :now AND h.eventEndDate > :now AND h.eventEndDate <= :until ORDER BY h.id")
    List<Hackathon> endingBefore(@Param("now") Instant now, @Param("until") Instant until);
}
