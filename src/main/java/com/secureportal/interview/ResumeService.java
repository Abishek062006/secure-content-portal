package com.secureportal.interview;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * A learner's resume for interview practice. The PDF is read in memory and only its text is kept: one resume per learner, private to
 * them, deleted when they ask, when the retention period ends, or with their account. Uploading needs their explicit consent.
 */
@Service
public class ResumeService {

    private static final Logger log = LoggerFactory.getLogger(ResumeService.class);

    static final int MAX_BYTES = 3 * 1024 * 1024;
    public static final int MAX_BYTES_ALLOWED = MAX_BYTES;
    static final int MAX_PAGES = 10;
    static final int MAX_CHARACTERS = 12_000;
    static final int MIN_CHARACTERS = 200;
    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private final InterviewResumeRepository resumes;
    private final Duration retention;

    public ResumeService(InterviewResumeRepository resumes, @Value("${app.interview.resume-retention-days:90}") long retentionDays) {
        this.resumes = resumes;
        this.retention = Duration.ofDays(retentionDays);
    }

    @Transactional(readOnly = true)
    public Optional<InterviewResume> find(Long userId) {
        return resumes.findByUserId(userId);
    }

    /** Replaces any earlier resume. Nothing is stored unless the file is a readable PDF with real text in it. */
    @Transactional
    public InterviewResume upload(Long userId, String filename, byte[] pdf, boolean consent) {
        if (!consent) {
            throw new InvalidInterviewException("Please confirm you're happy for us to read your resume to write your questions.");
        }
        if (pdf == null || pdf.length == 0) {
            throw new InvalidInterviewException("Choose a PDF file.");
        }
        if (pdf.length > MAX_BYTES) {
            throw new InvalidInterviewException("The resume must be under " + (MAX_BYTES / (1024 * 1024)) + " MB.");
        }
        if (!startsWithPdfMagic(pdf)) {
            throw new InvalidInterviewException("That doesn't look like a PDF. Export your resume as a PDF and try again.");
        }

        String text = extractText(pdf);
        if (text.length() < MIN_CHARACTERS) {
            throw new InvalidInterviewException("We couldn't find readable text in that PDF. If it's a scan or a picture, export a text-based PDF instead.");
        }

        resumes.deleteForUser(userId);
        Instant now = Instant.now();
        InterviewResume saved = resumes.save(new InterviewResume(userId, cleanFilename(filename), text, now, now.plus(retention)));
        log.info("Resume stored for user {} ({} characters)", userId, saved.getCharacters());
        return saved;
    }

    @Transactional
    public void delete(Long userId) {
        resumes.deleteForUser(userId);
    }

    /** Removes resumes past their retention period. Safe to run from any number of instances. */
    @Transactional
    public int deleteExpired() {
        int removed = resumes.deleteExpired(Instant.now());
        if (removed > 0) {
            log.info("Removed {} expired resume(s)", removed);
        }
        return removed;
    }

    private static boolean startsWithPdfMagic(byte[] data) {
        if (data.length < PDF_MAGIC.length) {
            return false;
        }
        for (int i = 0; i < PDF_MAGIC.length; i++) {
            if (data[i] != PDF_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    private static String extractText(byte[] pdf) {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            if (doc.isEncrypted()) {
                throw new InvalidInterviewException("That PDF is password protected. Remove the password and try again.");
            }
            if (doc.getNumberOfPages() > MAX_PAGES) {
                throw new InvalidInterviewException("A resume should be at most " + MAX_PAGES + " pages.");
            }
            String raw = new PDFTextStripper().getText(doc);
            String text = raw.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", " ").replaceAll("[ \\t]+", " ").replaceAll("\\n\\s*\\n+", "\n").strip();
            return text.length() > MAX_CHARACTERS ? text.substring(0, MAX_CHARACTERS) : text;
        } catch (IOException | RuntimeException e) {
            if (e instanceof InvalidInterviewException invalid) {
                throw invalid;
            }
            throw new InvalidInterviewException("We couldn't read that PDF. Try exporting it again.");
        }
    }

    /** Only shown back to the learner, but still reduced to a plain file name. */
    private static String cleanFilename(String filename) {
        String name = filename == null ? "" : filename.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").strip();
        if (name.isEmpty()) {
            name = "resume.pdf";
        }
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }
}
