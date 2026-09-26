package com.secureportal.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * Talks to any S3-compatible store. With an endpoint and keys (local SeaweedFS, Supabase, ...) it uses them as given.
 * On AWS itself leave the endpoint and keys empty: the SDK then addresses real S3 and signs with the container's IAM
 * role, so no long-lived keys exist anywhere.
 */
@Configuration
public class StorageConfig {

    @Bean
    @ConditionalOnProperty(name = "storage.provider", havingValue = "s3", matchIfMissing = true)
    public S3Client s3Client(StorageProperties storageProperties) {
        var builder = S3Client.builder()
                .region(Region.of(storageProperties.getRegion()))
                .credentialsProvider(credentials(storageProperties))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(storageProperties.isPathStyleAccess())
                        .build());
        if (StringUtils.hasText(storageProperties.getEndpoint())) {
            builder.endpointOverride(URI.create(storageProperties.getEndpoint()));
        }
        return builder.build();
    }

    /** Signs the short-lived URLs a browser uploads video parts to directly, bypassing this server. */
    @Bean
    @ConditionalOnProperty(name = "storage.provider", havingValue = "s3", matchIfMissing = true)
    public S3Presigner s3Presigner(StorageProperties storageProperties) {
        var builder = S3Presigner.builder()
                .region(Region.of(storageProperties.getRegion()))
                .credentialsProvider(credentials(storageProperties))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(storageProperties.isPathStyleAccess())
                        .build());
        if (StringUtils.hasText(storageProperties.getEndpoint())) {
            builder.endpointOverride(URI.create(storageProperties.getEndpoint()));
        }
        return builder.build();
    }

    private static AwsCredentialsProvider credentials(StorageProperties properties) {
        if (StringUtils.hasText(properties.getAccessKey()) && StringUtils.hasText(properties.getSecretKey())) {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey()));
        }
        return DefaultCredentialsProvider.create();
    }
}
