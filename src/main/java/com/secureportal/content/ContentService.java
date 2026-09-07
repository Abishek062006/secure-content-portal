package com.secureportal.content;

import com.secureportal.common.FileValidator;
import com.secureportal.common.ValidatedFile;
import com.secureportal.content.dto.EditForm;
import com.secureportal.content.dto.UploadForm;
import com.secureportal.storage.StorageService;
import com.secureportal.user.User;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;

@Service
public class ContentService {

    private static final Logger log = LoggerFactory.getLogger(ContentService.class);

    private final ContentRepository contentRepository;
    private final StorageService storageService;
    private final FileValidator fileValidator;

    public ContentService(ContentRepository contentRepository, StorageService storageService,
                           FileValidator fileValidator) {
        this.contentRepository = contentRepository;
        this.storageService = storageService;
        this.fileValidator = fileValidator;
    }

    /**
     * The upload and the database write can't share a single transaction —
     * one is a remote S3-compatible call, the other is local to Postgres.
     * Instead: upload first, then write the row, and if that write fails,
     * delete the just-uploaded object so nothing is left orphaned in storage.
     */
    public ContentItem create(UploadForm form, User uploadedBy) {
        MultipartFile file = form.getFile();
        ValidatedFile validated = fileValidator.validate(file, form.getContentType());

        String storageKey = "content/" + UUID.randomUUID() + "/" + sanitize(validated.originalFilename());

        try (InputStream in = file.getInputStream()) {
            storageService.put(storageKey, in, validated.sizeBytes(), validated.detectedMimeType());
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the uploaded file", e);
        }

        Integer pageCount = form.getContentType() == ContentType.PDF
                ? countPdfPages(file, storageKey)
                : null;

        try {
            ContentItem item = new ContentItem(
                    form.getTitle(),
                    form.getDescription(),
                    form.getCategory(),
                    form.getContentType(),
                    storageKey,
                    validated.originalFilename(),
                    validated.detectedMimeType(),
                    validated.sizeBytes(),
                    pageCount,
                    uploadedBy
            );
            return contentRepository.save(item);
        } catch (RuntimeException e) {
            log.warn("Content row save failed after upload; removing orphaned object {}", storageKey, e);
            storageService.delete(storageKey);
            throw e;
        }
    }

    @Transactional
    public ContentItem update(UUID id, EditForm form) {
        ContentItem item = contentRepository.findById(id)
                .orElseThrow(() -> new ContentNotFoundException(id));
        item.setTitle(form.getTitle());
        item.setDescription(form.getDescription());
        item.setCategory(form.getCategory());
        item.setUpdatedAt(Instant.now());
        return item;
    }

    /**
     * Deletes the database row first, then best-effort deletes the storage
     * object. That ordering matters: if the row is gone but the object
     * lingers, it's just harmless dangling data nothing points to. The
     * reverse order could leave a row referencing a file that no longer
     * exists, which is a broken reference a viewer would actually hit.
     */
    public void delete(UUID id) {
        ContentItem item = contentRepository.findById(id)
                .orElseThrow(() -> new ContentNotFoundException(id));
        String storageKey = item.getStorageKey();
        contentRepository.delete(item);
        try {
            storageService.delete(storageKey);
        } catch (RuntimeException e) {
            log.warn("Content row {} deleted but storage cleanup failed for key {}", id, storageKey, e);
        }
    }

    private Integer countPdfPages(MultipartFile file, String storageKey) {
        try (InputStream in = file.getInputStream();
             PDDocument doc = Loader.loadPDF(in.readAllBytes())) {
            return doc.getNumberOfPages();
        } catch (IOException e) {
            log.warn("Could not count PDF pages for {}", storageKey, e);
            return null;
        }
    }

    private String sanitize(String originalFilename) {
        return originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
