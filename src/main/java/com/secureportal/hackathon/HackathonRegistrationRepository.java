package com.secureportal.hackathon;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface HackathonRegistrationRepository extends JpaRepository<HackathonRegistration, Long> {

    /** Registers the learner unless they already are; returns 1 only for the request that created the registration. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "INSERT IGNORE INTO hackathon_registrations (hackathon_id, user_id, points_claimed, registered_at) "
            + "VALUES (:hackathonId, :userId, :points, UTC_TIMESTAMP(6))", nativeQuery = true)
    int registerOnce(@Param("hackathonId") Long hackathonId, @Param("userId") Long userId, @Param("points") int points);

    /** {hackathon id, registrations} for just these hackathons. */
    @Query("SELECT r.hackathonId, COUNT(r) FROM HackathonRegistration r WHERE r.hackathonId IN :ids GROUP BY r.hackathonId")
    List<Object[]> countByHackathons(@Param("ids") Collection<Long> ids);

    @Query("SELECT r.hackathonId FROM HackathonRegistration r WHERE r.userId = :userId AND r.hackathonId IN :ids")
    List<Long> registeredAmong(@Param("userId") Long userId, @Param("ids") Collection<Long> ids);
}
