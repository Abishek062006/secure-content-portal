package com.secureportal.hackathon;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface HackathonRepository extends JpaRepository<Hackathon, Long> {

    @Query("SELECT h FROM Hackathon h WHERE (:stream IS NULL OR LOWER(h.stream) = LOWER(:stream)) AND (:mode IS NULL OR h.mode = :mode) "
            + "ORDER BY h.featured DESC, CASE WHEN h.eventStartDate IS NULL THEN 1 ELSE 0 END, h.eventStartDate ASC, h.id DESC")
    List<Hackathon> search(@Param("stream") String stream, @Param("mode") String mode, Pageable page);

    @Query("SELECT h FROM Hackathon h WHERE h.id IN (SELECT s.hackathonId FROM HackathonSave s WHERE s.userId = :userId) "
            + "ORDER BY CASE WHEN h.eventStartDate IS NULL THEN 1 ELSE 0 END, h.eventStartDate ASC, h.id DESC")
    List<Hackathon> savedBy(@Param("userId") Long userId, Pageable page);
}
