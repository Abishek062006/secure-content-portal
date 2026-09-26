package com.secureportal.hackathon;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface HackathonSaveRepository extends JpaRepository<HackathonSave, Long> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "INSERT IGNORE INTO hackathon_saves (hackathon_id, user_id, saved_at) VALUES (:hackathonId, :userId, UTC_TIMESTAMP(6))",
            nativeQuery = true)
    int saveOnce(@Param("hackathonId") Long hackathonId, @Param("userId") Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM HackathonSave s WHERE s.hackathonId = :hackathonId AND s.userId = :userId")
    int remove(@Param("hackathonId") Long hackathonId, @Param("userId") Long userId);

    @Query("SELECT s.hackathonId FROM HackathonSave s WHERE s.userId = :userId AND s.hackathonId IN :ids")
    List<Long> savedAmong(@Param("userId") Long userId, @Param("ids") Collection<Long> ids);
}
