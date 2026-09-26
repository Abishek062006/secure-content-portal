package com.secureportal.storage;

import com.secureportal.config.StorageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;
import java.util.List;

@Service
@ConditionalOnProperty(name = "storage.provider", havingValue = "s3", matchIfMissing = true)
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

        ResponseInputStream<GetObjectResponse> s3Object;
        try {
            s3Object = s3Client.getObject(builder.build());
        } catch (NoSuchKeyException e) {
            // Same answer the local-disk store gives, so a missing file is a 404 rather than a server error.
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        }
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
    public void deletePrefix(String prefix) {
        String token = null;
        do {
            ListObjectsV2Response page = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(bucket).prefix(prefix).continuationToken(token).build());
            List<ObjectIdentifier> ids = page.contents().stream()
                    .map(o -> ObjectIdentifier.builder().key(o.key()).build()).toList();
            if (!ids.isEmpty()) {
                s3Client.deleteObjects(DeleteObjectsRequest.builder().bucket(bucket)
                        .delete(Delete.builder().objects(ids).quiet(true).build()).build());
            }
            token = Boolean.TRUE.equals(page.isTruncated()) ? page.nextContinuationToken() : null;
        } while (token != null);
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
