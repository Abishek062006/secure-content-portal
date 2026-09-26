package com.secureportal.profile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProfileEntryRepository extends JpaRepository<ProfileEntry, UUID> {

    List<ProfileEntry> findByUserId(Long userId);

    long countByUserIdAndKind(Long userId, EntryKind kind);
}
