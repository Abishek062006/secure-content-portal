package com.secureportal.storage;

import com.secureportal.config.StorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

/**
 * Creates the bucket on first start against a local S3 server. In production the bucket is provisioned separately
 * (with its own policy), so set {@code storage.create-bucket=false} there.
 */
@Component
@ConditionalOnProperty(name = "storage.provider", havingValue = "s3", matchIfMissing = true)
public class BucketInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BucketInitializer.class);

    private final S3Client s3;
    private final StorageProperties properties;

    public BucketInitializer(S3Client s3, StorageProperties properties) {
        this.s3 = s3;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isCreateBucket()) {
            return;
        }
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(properties.getBucket()).build());
        } catch (NoSuchBucketException e) {
            s3.createBucket(CreateBucketRequest.builder().bucket(properties.getBucket()).build());
            log.info("Created storage bucket {}", properties.getBucket());
        } catch (RuntimeException e) {
            // Storage that isn't reachable yet shouldn't stop the app from starting; uploads will report it.
            log.warn("Could not check the storage bucket {}: {}", properties.getBucket(), e.getMessage());
        }
    }
}
