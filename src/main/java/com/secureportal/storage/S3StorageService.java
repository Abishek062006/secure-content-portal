package com.secureportal.storage;

import com.secureportal.config.StorageProperties;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;

@Service
public class S3StorageService implements StorageService {

    private final S3Client s3Client;
    private final String bucket;

    public S3StorageService(S3Client s3Client, StorageProperties storageProperties) {
        this.s3Client = s3Client;
        this.bucket = storageProperties.getBucket();
    }

    @Override
    public void put(String key, InputStream data, long contentLength, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .contentLength(contentLength)
                .build();
        s3Client.putObject(request, RequestBody.fromInputStream(data, contentLength));
    }

    @Override
    public StorageObject get(String key, Long rangeStart, Long rangeEnd) {
        GetObjectRequest.Builder builder = GetObjectRequest.builder().bucket(bucket).key(key);
        if (rangeStart != null) {
            String range = rangeEnd != null
                    ? "bytes=" + rangeStart + "-" + rangeEnd
                    : "bytes=" + rangeStart + "-";
            builder.range(range);
        }

        ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(builder.build());
        GetObjectResponse response = s3Object.response();

        long totalSize;
        long effectiveStart;
        long effectiveEnd;
        String contentRange = response.contentRange();
        if (contentRange != null) {
            // Format: "bytes start-end/total"
            String[] parts = contentRange.substring("bytes ".length()).split("[-/]");
            effectiveStart = Long.parseLong(parts[0]);
            effectiveEnd = Long.parseLong(parts[1]);
            totalSize = Long.parseLong(parts[2]);
        } else {
            effectiveStart = 0;
            effectiveEnd = response.contentLength() - 1;
            totalSize = response.contentLength();
        }

        return new StorageObject(s3Object, effectiveStart, effectiveEnd, totalSize, response.contentType());
    }

    @Override
    public void delete(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    @Override
    public boolean exists(String key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }
}
