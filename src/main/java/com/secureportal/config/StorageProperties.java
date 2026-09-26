package com.secureportal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storage")
public class StorageProperties {

    /** {@code s3} (default) or {@code local} — local writes to disk, for running without any cloud storage. */
    private String provider = "s3";

    private String localPath = "./local-storage";

    /** S3-compatible endpoint (Supabase Storage, R2, MinIO, ...). */
    private String endpoint;

    private String region = "us-east-1";

    private String bucket;

    private String accessKey;

    private String secretKey;

    /**
     * Supabase and most S3-compatible services require path-style addressing
     * rather than virtual-host-style bucket subdomains.
     */
    private boolean pathStyleAccess = true;

    /** Create the bucket at startup if it doesn't exist (handy locally; off where the bucket is provisioned by hand). */
    private boolean createBucket = true;

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public boolean isPathStyleAccess() {
        return pathStyleAccess;
    }

    public void setPathStyleAccess(boolean pathStyleAccess) {
        this.pathStyleAccess = pathStyleAccess;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getLocalPath() {
        return localPath;
    }

    public void setLocalPath(String localPath) {
        this.localPath = localPath;
    }

    public boolean isCreateBucket() {
        return createBucket;
    }

    public void setCreateBucket(boolean createBucket) {
        this.createBucket = createBucket;
    }
}
