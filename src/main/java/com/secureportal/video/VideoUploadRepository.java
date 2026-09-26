package com.secureportal.video;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface VideoUploadRepository extends JpaRepository<VideoUpload, UUID> {
}
