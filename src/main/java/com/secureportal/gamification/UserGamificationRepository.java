package com.secureportal.gamification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface UserGamificationRepository extends JpaRepository<UserGamification, Long> {

    List<UserGamification> findAllByOrderByTotalPointsDesc();

    @Query("SELECT COUNT(u) + 1 FROM UserGamification u WHERE u.totalPoints > :points")
    long findRankByPoints(int points);
}
