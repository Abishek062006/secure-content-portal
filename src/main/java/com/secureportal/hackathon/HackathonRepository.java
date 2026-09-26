package com.secureportal.hackathon;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HackathonRepository extends JpaRepository<Hackathon, Long> {
    List<Hackathon> findByStatusOrderByFeaturedDescCreatedAtDesc(String status);
    List<Hackathon> findByStreamIgnoreCaseOrderByFeaturedDescCreatedAtDesc(String stream);
    List<Hackathon> findAllByOrderByFeaturedDescCreatedAtDesc();
}
