package com.secureportal.content;

import com.secureportal.common.FileValidator;
import com.secureportal.common.UploadException;
import com.secureportal.common.ValidatedFile;
import com.secureportal.config.AppProperties;
import com.secureportal.content.dto.EditForm;
import com.secureportal.content.dto.UploadForm;
import com.secureportal.html.HtmlSanitizer;
import com.secureportal.storage.StorageService;
import com.secureportal.user.User;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Service
public class ContentService {

    private static final Logger log = LoggerFactory.getLogger(ContentService.class);

    private final ContentRepository contentRepository;
    private final StorageService storageService;
    private final FileValidator fileValidator;
    private final HtmlSanitizer htmlSanitizer;
    private final AppProperties appProperties;

    public ContentService(ContentRepository contentRepository, StorageService storageService,
                           FileValidator fileValidator, HtmlSanitizer htmlSanitizer,
                           AppProperties appProperties) {
        this.contentRepository = contentRepository;
        this.storageService = storageService;
        this.fileValidator = fileValidator;
        this.htmlSanitizer = htmlSanitizer;
        this.appProperties = appProperties;
    }

    /**
     * The upload and the database write can't share a single transaction —
     * one is a remote S3-compatible call, the other is local to Postgres.
     * Instead: upload first, then write the row, and if that write fails,
     * delete the just-uploaded object so nothing is left orphaned in storage.
     *
     * <p>HTML is sanitized here, before it ever reaches storage — the stored
     * bytes are already safe, so there's nothing dangerous sitting in the
     * bucket even if a delivery endpoint were ever misconfigured. PDF and
     * HTML are small enough (32MB / 2MB caps) to buffer in memory for this;
     * video (up to 512MB) is streamed straight through instead.
     */
    public ContentItem create(UploadForm form, User uploadedBy) {
        MultipartFile file = form.getFile();
        ContentType contentType = form.getContentType();
        ValidatedFile validated = fileValidator.validate(file, contentType);

        String storageKey = "content/" + UUID.randomUUID() + "/" + sanitizeFilename(validated.originalFilename());
        String mimeType = validated.detectedMimeType();
        long storedSize;
        Integer pageCount = null;

        if (contentType == ContentType.HTML) {
            byte[] sanitized = sanitizeHtml(file);
            storageService.put(storageKey, new ByteArrayInputStream(sanitized), sanitized.length, mimeType);
            storedSize = sanitized.length;

        } else if (contentType == ContentType.PDF) {
            byte[] pdfBytes = readAllBytes(file);
            int pages = countPdfPages(pdfBytes);
            if (pages > appProperties.getPdfMaxPages()) {
                throw new UploadException("This PDF has " + pages + " pages; the limit is "
                        + appProperties.getPdfMaxPages() + ".");
            }
            storageService.put(storageKey, new ByteArrayInputStream(pdfBytes), pdfBytes.length, mimeType);
            storedSize = pdfBytes.length;
            pageCount = pages;

        } else {
            try (InputStream in = file.getInputStream()) {
                storageService.put(storageKey, in, validated.sizeBytes(), mimeType);
            } catch (IOException e) {
                throw new IllegalStateException("Could not read the uploaded file", e);
            }
            storedSize = validated.sizeBytes();
        }

        try {
            ContentItem item = new ContentItem(
                    form.getTitle(),
                    form.getDescription(),
                    form.getCategory(),
                    contentType,
                    storageKey,
                    validated.originalFilename(),
                    mimeType,
                    storedSize,
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

    /** Recorded as a bulk update in the repository so a view can never race a concurrent metadata edit. */
    @Transactional
    public void recordView(UUID id) {
        contentRepository.recordView(id, Instant.now());
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
     *
     * <p>Cached PDF page renders under {@code derived/<id>/} are not swept
     * here — the storage interface only deletes single keys, not prefixes.
     * They're unreachable once the row is gone (nothing can mint a ticket
     * for a deleted item), just not reclaimed; a known, minor cleanup gap.
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

    private byte[] readAllBytes(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the uploaded file", e);
        }
    }

    private byte[] sanitizeHtml(MultipartFile file) {
        byte[] raw = readAllBytes(file);
        String cleaned = htmlSanitizer.sanitize(new String(raw, StandardCharsets.UTF_8));
        return cleaned.getBytes(StandardCharsets.UTF_8);
    }

    private int countPdfPages(byte[] pdfBytes) {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            return doc.getNumberOfPages();
        } catch (IOException e) {
            throw new UploadException("Could not read this PDF. It may be corrupted.");
        }
    }

    private String sanitizeFilename(String originalFilename) {
        return originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
