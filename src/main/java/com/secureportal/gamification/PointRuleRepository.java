package com.secureportal.gamification;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PointRuleRepository extends JpaRepository<PointRule, String> {
    Optional<PointRule> findByActionType(String actionType);
}
