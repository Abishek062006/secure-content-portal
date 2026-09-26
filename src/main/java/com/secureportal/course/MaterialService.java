package com.secureportal.course;

import com.secureportal.common.FileValidator;
import com.secureportal.common.UploadException;
import com.secureportal.common.ValidatedFile;
import com.secureportal.config.AppProperties;
import com.secureportal.content.ContentType;
import com.secureportal.html.HtmlSanitizer;
import com.secureportal.storage.StorageService;
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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Study material on a module. PDFs, HTML and videos are opened inside the portal through the same protected viewers
 * as the library (watermarked page images, sandboxed HTML, ticketed streaming); the admin chooses whether learners
 * may also download them. Office documents can't be shown safely in a browser, so they are download-only.
 */
@Service
public class MaterialService {

    static final long MAX_VIDEO_BYTES = 500L * 1024 * 1024;
    static final long MAX_DOCUMENT_BYTES = 50L * 1024 * 1024;
    private static final Set<String> DOCUMENT_EXTENSIONS = Set.of("doc", "docx", "ppt", "pptx", "xls", "xlsx", "txt", "csv", "zip");

    private static final Logger log = LoggerFactory.getLogger(MaterialService.class);

    private final CourseMaterialRepository materialRepository;
    private final CourseStructureService structureService;
    private final FileValidator fileValidator;
    private final HtmlSanitizer htmlSanitizer;
    private final StorageService storageService;
    private final AppProperties appProperties;

    public MaterialService(CourseMaterialRepository materialRepository, CourseStructureService structureService,
                           FileValidator fileValidator, HtmlSanitizer htmlSanitizer, StorageService storageService,
                           AppProperties appProperties) {
        this.materialRepository = materialRepository;
        this.structureService = structureService;
        this.fileValidator = fileValidator;
        this.htmlSanitizer = htmlSanitizer;
        this.storageService = storageService;
        this.appProperties = appProperties;
    }

    public CourseMaterial find(UUID id) {
        return materialRepository.findById(id).orElseThrow(MaterialNotFoundException::new);
    }

    public List<CourseMaterial> forCourse(UUID courseId) {
        return materialRepository.findByCourseIdOrderByCreatedAtAsc(courseId);
    }

    /** Adds a file (kind chosen from its extension) or, with no file, a link. */
    public CourseMaterial add(UUID moduleId, String title, String description, String url, boolean downloadable,
                              MultipartFile file) {
        CourseModule module = structureService.findModule(moduleId);
        String name = clean(title, 200);
        if (name == null) {
            throw new UploadException("Give the material a title.");
        }
        String note = clean(description, 1000);
        UUID id = UUID.randomUUID();
        boolean hasFile = file != null && !file.isEmpty();

        if (!hasFile) {
            if (url == null || url.isBlank()) {
                throw new UploadException("Choose a file or enter a link.");
            }
            CourseMaterial link = new CourseMaterial(id, moduleId, module.getCourseId(), MaterialKind.LINK, name, note, false);
            link.setLink(cleanUrl(url));
            return materialRepository.save(link);
        }

        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = original.lastIndexOf('.');
        String extension = dot < 0 ? "" : original.substring(dot + 1).toLowerCase(Locale.ROOT);
        MaterialKind kind = switch (extension) {
            case "pdf" -> MaterialKind.PDF;
            case "html", "htm" -> MaterialKind.HTML;
            case "mp4", "webm" -> MaterialKind.VIDEO;
            default -> DOCUMENT_EXTENSIONS.contains(extension) ? MaterialKind.DOCUMENT : null;
        };
        if (kind == null) {
            throw new UploadException("Upload a PDF, HTML page, MP4/WebM video, or a Word, PowerPoint, Excel, text, CSV or ZIP file.");
        }

        String cleanName = original.replace('\\', '/');
        cleanName = cleanName.substring(cleanName.lastIndexOf('/') + 1).replaceAll("[^a-zA-Z0-9._-]", "_");
        String key = "materials/" + id + "/" + cleanName;
        CourseMaterial material = new CourseMaterial(id, moduleId, module.getCourseId(), kind, name, note,
                kind == MaterialKind.DOCUMENT || downloadable);

        switch (kind) {
            case PDF -> {
                ValidatedFile validated = fileValidator.validate(file, ContentType.PDF);
                byte[] bytes = read(file);
                int pages = countPages(bytes);
                if (pages > appProperties.getPdfMaxPages()) {
                    throw new UploadException("This PDF has " + pages + " pages; the limit is " + appProperties.getPdfMaxPages() + ".");
                }
                put(key, bytes, validated.detectedMimeType());
                material.setFile(key, validated.originalFilename(), validated.detectedMimeType(), bytes.length, pages);
            }
            case HTML -> {
                ValidatedFile validated = fileValidator.validate(file, ContentType.HTML);
                byte[] safe = htmlSanitizer.sanitize(new String(read(file), StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
                put(key, safe, "text/html");
                material.setFile(key, validated.originalFilename(), "text/html", safe.length, null);
            }
            case VIDEO -> {
                if (file.getSize() > MAX_VIDEO_BYTES) {
                    throw new UploadException("A material video can be at most 500 MB. Longer videos belong in a lesson.");
                }
                ValidatedFile validated = fileValidator.validate(file, ContentType.VIDEO);
                try (InputStream in = file.getInputStream()) {
                    storageService.put(key, in, validated.sizeBytes(), validated.detectedMimeType());
                } catch (IOException e) {
                    throw new IllegalStateException("Could not read the uploaded video", e);
                }
                material.setFile(key, validated.originalFilename(), validated.detectedMimeType(), validated.sizeBytes(), null);
            }
            default -> {
                if (file.getSize() > MAX_DOCUMENT_BYTES) {
                    throw new UploadException("Documents can be at most 50 MB.");
                }
                byte[] bytes = read(file);
                // Not shown in the browser, only handed over as an attachment, so the type stays generic.
                put(key, bytes, "application/octet-stream");
                material.setFile(key, original, "application/octet-stream", bytes.length, null);
            }
        }

        try {
            return materialRepository.save(material);
        } catch (RuntimeException e) {
            deleteQuietly(key);
            throw e;
        }
    }

    @Transactional
    public CourseMaterial update(UUID id, String title, String description, boolean downloadable, String url) {
        CourseMaterial material = find(id);
        String name = clean(title, 200);
        if (name == null) {
            throw new UploadException("Give the material a title.");
        }
        boolean allowDownload = material.getKind() == MaterialKind.DOCUMENT || (material.getKind() != MaterialKind.LINK && downloadable);
        material.edit(name, clean(description, 1000), allowDownload);
        if (material.getKind() == MaterialKind.LINK && url != null && !url.isBlank()) {
            material.setLink(cleanUrl(url));
        }
        return material;
    }

    /** Row first, then storage: a leftover object is harmless, a row pointing at a missing file is not. */
    public void delete(UUID id) {
        CourseMaterial material = find(id);
        materialRepository.delete(material);
        deleteQuietly(material.getStorageKey());
    }

    /** Storage keys of every material of a module, for cleaning up when the module or course goes away. */
    public List<String> keysForModule(UUID moduleId) {
        return materialRepository.findByModuleIdOrderByCreatedAtAsc(moduleId).stream().map(CourseMaterial::getStorageKey).toList();
    }

    public List<String> keysForCourse(UUID courseId) {
        return forCourse(courseId).stream().map(CourseMaterial::getStorageKey).toList();
    }

    private static String cleanUrl(String value) {
        String text = value.trim();
        if (text.length() > 1000) {
            throw new UploadException("The link is too long.");
        }
        String lower = text.toLowerCase(Locale.ROOT);
        boolean web = lower.startsWith("http://") || lower.startsWith("https://");
        // "javascript:...", "ftp://...", "mailto:..." are other schemes; "host:8080" is fine.
        if (!web && lower.matches("^[a-z][a-z0-9+.-]*:(//|[^0-9/]).*")) {
            throw new UploadException("Links must be web addresses starting with http:// or https://.");
        }
        try {
            URI uri = URI.create(web ? text : "https://" + text);
            if (uri.getHost() == null) {
                throw new IllegalArgumentException();
            }
            return uri.toString();
        } catch (IllegalArgumentException e) {
            throw new UploadException("Enter a valid web address.");
        }
    }

    private static String clean(String value, int max) {
        String text = value == null ? "" : value.trim();
        if (text.length() > max) {
            throw new UploadException("That's too long (at most " + max + " characters).");
        }
        return text.isEmpty() ? null : text;
    }

    private static byte[] read(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the uploaded file", e);
        }
    }

    private static int countPages(byte[] pdf) {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return doc.getNumberOfPages();
        } catch (IOException e) {
            throw new UploadException("This PDF could not be read.");
        }
    }

    private void put(String key, byte[] bytes, String mime) {
        storageService.put(key, new ByteArrayInputStream(bytes), bytes.length, mime);
    }

    private void deleteQuietly(String key) {
        if (key == null) {
            return;
        }
        try {
            storageService.delete(key);
        } catch (RuntimeException e) {
            log.warn("Could not delete storage object {}", key, e);
        }
    }
}
