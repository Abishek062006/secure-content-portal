package com.secureportal.video;

import com.secureportal.common.FileValidator;
import com.secureportal.common.UploadException;
import com.secureportal.common.ValidatedFile;
import com.secureportal.config.StorageProperties;
import com.secureportal.course.CourseModule;
import com.secureportal.course.CourseStructureService;
import com.secureportal.course.Lesson;
import com.secureportal.storage.StorageObject;
import com.secureportal.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListPartsRequest;
import software.amazon.awssdk.services.s3.model.ListPartsResponse;
import software.amazon.awssdk.services.s3.model.NoSuchUploadException;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Large videos go from the browser straight to the bucket as S3 multipart uploads: this server signs a URL per
 * part and never carries the video bytes. When the upload is completed it checks the stored object (size and what
 * its first bytes really are) before turning it into a lesson.
 */
@Service
@ConditionalOnProperty(name = "storage.provider", havingValue = "s3", matchIfMissing = true)
public class DirectUploadService {

    /** S3 needs at least 5 MiB per part and allows 10,000 parts; 16 MiB covers the 5 GB cap in about 320. */
    public static final long PART_SIZE = 16L * 1024 * 1024;
    static final Duration URL_TTL = Duration.ofHours(2);

    private static final Logger log = LoggerFactory.getLogger(DirectUploadService.class);

    public record Part(int partNumber, String etag) {
    }

    public record PartUrl(int partNumber, String url) {
    }

    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;
    private final VideoUploadRepository uploadRepository;
    private final CourseStructureService structureService;
    private final FileValidator fileValidator;
    private final StorageService storageService;

    public DirectUploadService(S3Client s3, S3Presigner presigner, StorageProperties properties,
                               VideoUploadRepository uploadRepository, CourseStructureService structureService,
                               FileValidator fileValidator, StorageService storageService) {
        this.s3 = s3;
        this.presigner = presigner;
        this.bucket = properties.getBucket();
        this.uploadRepository = uploadRepository;
        this.structureService = structureService;
        this.fileValidator = fileValidator;
        this.storageService = storageService;
    }

    public VideoUpload start(UUID moduleId, String title, String description, String filename, long sizeBytes, Long userId) {
        CourseModule module = structureService.findModule(moduleId);
        String name = title == null ? "" : title.trim();
        if (name.isEmpty() || name.length() > 200) {
            throw new UploadException("Give the lesson a title (up to 200 characters).");
        }
        String safe = safeName(filename);
        String extension = safe.substring(safe.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!safe.contains(".") || !(extension.equals("mp4") || extension.equals("webm"))) {
            throw new UploadException("Video files must be .mp4 or .webm.");
        }
        if (sizeBytes <= 0 || sizeBytes > com.secureportal.content.ContentType.VIDEO.getMaxSizeBytes()) {
            throw new UploadException("Video files must be " + com.secureportal.content.ContentType.VIDEO.getMaxSizeLabel() + " or smaller.");
        }

        UUID id = UUID.randomUUID();
        String key = "courses/" + module.getCourseId() + "/lessons/" + id + "/video/" + safe;
        String contentType = extension.equals("webm") ? "video/webm" : "video/mp4";
        String s3UploadId = s3.createMultipartUpload(CreateMultipartUploadRequest.builder()
                .bucket(bucket).key(key).contentType(contentType).build()).uploadId();
        return uploadRepository.save(new VideoUpload(id, moduleId, module.getCourseId(), name,
                description == null || description.isBlank() ? null : description.trim(), safe, sizeBytes, PART_SIZE, key,
                s3UploadId, userId));
    }

    public List<PartUrl> presign(UUID uploadId, List<Integer> partNumbers) {
        VideoUpload upload = open(uploadId);
        List<PartUrl> urls = new ArrayList<>();
        for (int number : partNumbers) {
            if (number < 1 || number > upload.partCount()) {
                throw new VideoUploadException("There is no part " + number + " in this upload.");
            }
            String url = presigner.presignUploadPart(UploadPartPresignRequest.builder()
                    .signatureDuration(URL_TTL)
                    .uploadPartRequest(UploadPartRequest.builder().bucket(bucket).key(upload.getStorageKey())
                            .uploadId(upload.getS3UploadId()).partNumber(number).build())
                    .build()).url().toString();
            urls.add(new PartUrl(number, url));
        }
        return urls;
    }

    /** Parts the bucket already holds, so an interrupted upload can carry on where it stopped. */
    public List<Part> uploaded(UUID uploadId) {
        VideoUpload upload = open(uploadId);
        List<Part> parts = new ArrayList<>();
        Integer marker = null;
        try {
            ListPartsResponse page;
            do {
                page = s3.listParts(ListPartsRequest.builder().bucket(bucket).key(upload.getStorageKey())
                        .uploadId(upload.getS3UploadId()).partNumberMarker(marker).build());
                page.parts().forEach(p -> parts.add(new Part(p.partNumber(), p.eTag())));
                marker = page.nextPartNumberMarker();
            } while (Boolean.TRUE.equals(page.isTruncated()));
        } catch (NoSuchUploadException e) {
            throw new VideoUploadException("This upload is no longer available. Start it again.");
        }
        return parts;
    }

    public Lesson complete(UUID uploadId, List<Part> parts) {
        VideoUpload upload = open(uploadId);
        if (parts == null || parts.size() != upload.partCount()) {
            throw new VideoUploadException("Some parts are missing: expected " + upload.partCount() + ", got "
                    + (parts == null ? 0 : parts.size()) + ".");
        }
        List<CompletedPart> completed = parts.stream().sorted(Comparator.comparingInt(Part::partNumber))
                .map(p -> CompletedPart.builder().partNumber(p.partNumber()).eTag(p.etag()).build()).toList();
        try {
            s3.completeMultipartUpload(CompleteMultipartUploadRequest.builder().bucket(bucket).key(upload.getStorageKey())
                    .uploadId(upload.getS3UploadId()).multipartUpload(CompletedMultipartUpload.builder().parts(completed).build()).build());
        } catch (RuntimeException e) {
            log.warn("Completing upload {} failed", uploadId, e);
            throw new VideoUploadException("The storage service could not assemble the video. Try uploading again.");
        }

        ValidatedFile validated;
        try {
            long stored = s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(upload.getStorageKey()).build()).contentLength();
            if (stored != upload.getSizeBytes()) {
                throw new VideoUploadException("The uploaded video is " + stored + " bytes but " + upload.getSizeBytes() + " were expected.");
            }
            validated = fileValidator.validateStoredVideo(upload.getFilename(), stored, head(upload.getStorageKey()));
        } catch (VideoUploadException | UploadException e) {
            discard(upload);
            throw e;
        }

        Lesson lesson = structureService.registerLesson(upload.getModuleId(), upload.getTitle(), upload.getDescription(),
                upload.getStorageKey(), validated.originalFilename(), validated.detectedMimeType(), validated.sizeBytes());
        upload.markComplete();
        uploadRepository.save(upload);
        return lesson;
    }

    public void abort(UUID uploadId) {
        VideoUpload upload = uploadRepository.findById(uploadId).orElseThrow(() -> new VideoUploadException("That upload doesn't exist."));
        if (upload.getStatus() == UploadStatus.UPLOADING) {
            try {
                s3.abortMultipartUpload(AbortMultipartUploadRequest.builder().bucket(bucket).key(upload.getStorageKey())
                        .uploadId(upload.getS3UploadId()).build());
            } catch (RuntimeException e) {
                log.warn("Aborting upload {} failed", uploadId, e);
            }
            upload.markAborted();
            uploadRepository.save(upload);
        }
    }

    private VideoUpload open(UUID id) {
        VideoUpload upload = uploadRepository.findById(id).orElseThrow(() -> new VideoUploadException("That upload doesn't exist."));
        if (upload.getStatus() != UploadStatus.UPLOADING) {
            throw new VideoUploadException("That upload is already finished.");
        }
        return upload;
    }

    /** The first bytes of the stored video, enough to tell what kind of file it really is. */
    private byte[] head(String key) {
        try (StorageObject object = storageService.get(key, 0L, 8191L)) {
            return object.content().readAllBytes();
        } catch (IOException e) {
            throw new VideoUploadException("Could not read the uploaded video back to check it.");
        }
    }

    private void discard(VideoUpload upload) {
        try {
            storageService.delete(upload.getStorageKey());
        } catch (RuntimeException e) {
            log.warn("Could not remove rejected upload {}", upload.getStorageKey(), e);
        }
        upload.markAborted();
        uploadRepository.save(upload);
    }

    private static String safeName(String filename) {
        String name = filename == null ? "" : filename.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[^a-zA-Z0-9._-]", "_");
        if (name.isBlank()) {
            throw new UploadException("The video needs a file name.");
        }
        return name;
    }
}
