package com.secureportal.common;

import com.secureportal.content.ContentType;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * No Spring context and no network — {@link FileValidator} only depends on
 * Tika, so these run as plain, fast unit tests. Byte fixtures below were
 * chosen by empirically running Tika against them first (see PR description /
 * commit notes); this is what real ffmpeg/browser output actually detects as,
 * not a guess at MP4/WebM's spec-level magic bytes.
 */
class FileValidatorTest {

    private final FileValidator validator = new FileValidator();

    // --- Real-world-shaped fixtures, confirmed against this Tika version ---

    /** major_brand=isom — what ffmpeg and macOS/iOS commonly emit. Detects as video/quicktime. */
    private static byte[] mp4IsomBrand() {
        return new byte[]{
                0x00, 0x00, 0x00, 0x20, 'f', 't', 'y', 'p',
                'i', 's', 'o', 'm',
                0x00, 0x00, 0x02, 0x00,
                'i', 's', 'o', 'm', 'i', 's', 'o', '2', 'a', 'v', 'c', '1', 'm', 'p', '4', '1'
        };
    }

    /** major_brand=mp42 — detects as video/mp4 directly. */
    private static byte[] mp4Mp42Brand() {
        return new byte[]{
                0x00, 0x00, 0x00, 0x1C, 'f', 't', 'y', 'p',
                'm', 'p', '4', '2',
                0x00, 0x00, 0x00, 0x00,
                'm', 'p', '4', '2', 'i', 's', 'o', 'm', 'a', 'v', 'c', '1'
        };
    }

    /** A real WebM's EBML header + DocType — detects as application/x-matroska (shallow detection can't tell webm from mkv). */
    private static byte[] webmWithDocType() {
        return new byte[]{
                (byte) 0x1A, (byte) 0x45, (byte) 0xDF, (byte) 0xA3,
                (byte) 0x9F, (byte) 0x42, (byte) 0x86, (byte) 0x81, 0x01,
                (byte) 0x42, (byte) 0xF7, (byte) 0x81, 0x01,
                (byte) 0x42, (byte) 0xF2, (byte) 0x81, 0x04,
                (byte) 0x42, (byte) 0xF3, (byte) 0x81, 0x08,
                (byte) 0x42, (byte) 0x82, (byte) 0x84, 'w', 'e', 'b', 'm',
                (byte) 0x42, (byte) 0x87, (byte) 0x81, 0x02,
                (byte) 0x42, (byte) 0x85, (byte) 0x81, 0x02
        };
    }

    private static byte[] pdfHeader() {
        return "%PDF-1.4\n%âãÏÓ\n1 0 obj\n".getBytes(StandardCharsets.ISO_8859_1);
    }

    private static byte[] htmlDoc() {
        return "<!DOCTYPE html>\n<html><head><title>x</title></head><body>hi</body></html>"
                .getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] plainText() {
        return "hello, this is just an ordinary text file with nothing special in it"
                .getBytes(StandardCharsets.UTF_8);
    }

    /** Windows PE header — a stand-in for "someone renamed an executable". */
    private static byte[] exeHeader() {
        return new byte[]{'M', 'Z', (byte) 0x90, 0x00, 0x03, 0x00, 0x00, 0x00, 0x04, 0x00, 0x00, 0x00};
    }

    // --- Accept real content for its declared type ---

    @Test
    void acceptsMp4WithIsomBrand() {
        MockMultipartFile file = new MockMultipartFile("file", "clip.mp4", "video/mp4", mp4IsomBrand());
        ValidatedFile result = validator.validate(file, ContentType.VIDEO);
        assertThat(result.detectedMimeType()).isEqualTo("video/quicktime");
        assertThat(result.originalFilename()).isEqualTo("clip.mp4");
    }

    @Test
    void acceptsMp4WithMp42Brand() {
        MockMultipartFile file = new MockMultipartFile("file", "clip.mp4", "video/mp4", mp4Mp42Brand());
        ValidatedFile result = validator.validate(file, ContentType.VIDEO);
        assertThat(result.detectedMimeType()).isEqualTo("video/mp4");
    }

    @Test
    void acceptsWebm() {
        MockMultipartFile file = new MockMultipartFile("file", "clip.webm", "video/webm", webmWithDocType());
        ValidatedFile result = validator.validate(file, ContentType.VIDEO);
        assertThat(result.detectedMimeType()).isEqualTo("application/x-matroska");
    }

    @Test
    void acceptsRealPdf() {
        MockMultipartFile file = new MockMultipartFile("file", "handout.pdf", "application/pdf", pdfHeader());
        ValidatedFile result = validator.validate(file, ContentType.PDF);
        assertThat(result.detectedMimeType()).isEqualTo("application/pdf");
    }

    @Test
    void acceptsRealHtml() {
        MockMultipartFile file = new MockMultipartFile("file", "page.html", "text/html", htmlDoc());
        ValidatedFile result = validator.validate(file, ContentType.HTML);
        assertThat(result.detectedMimeType()).isEqualTo("text/html");
    }

    // --- The actual security requirement: content wins, not the name ---

    @Test
    void rejectsTextFileRenamedToMp4() {
        MockMultipartFile file = new MockMultipartFile("file", "not-a-video.mp4", "video/mp4", plainText());
        assertThatThrownBy(() -> validator.validate(file, ContentType.VIDEO))
                .isInstanceOf(UploadException.class)
                .hasMessageContaining("detected: text/plain");
    }

    @Test
    void rejectsExecutableRenamedToMp4() {
        MockMultipartFile file = new MockMultipartFile("file", "totally-a-video.mp4", "video/mp4", exeHeader());
        assertThatThrownBy(() -> validator.validate(file, ContentType.VIDEO))
                .isInstanceOf(UploadException.class);
    }

    @Test
    void rejectsTextFileRenamedToPdf() {
        MockMultipartFile file = new MockMultipartFile("file", "fake.pdf", "application/pdf", plainText());
        assertThatThrownBy(() -> validator.validate(file, ContentType.PDF))
                .isInstanceOf(UploadException.class)
                .hasMessageContaining("detected: text/plain");
    }

    @Test
    void spoofedContentTypeHeaderIsIgnored() {
        // Browser claims video/mp4; bytes say otherwise. The header must not matter.
        MockMultipartFile file = new MockMultipartFile("file", "clip.mp4", "video/mp4", plainText());
        assertThatThrownBy(() -> validator.validate(file, ContentType.VIDEO))
                .isInstanceOf(UploadException.class);
    }

    // --- Extension and size checks ---

    @Test
    void rejectsDisallowedExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "clip.mov", "video/quicktime", mp4IsomBrand());
        assertThatThrownBy(() -> validator.validate(file, ContentType.VIDEO))
                .isInstanceOf(UploadException.class)
                .hasMessageContaining("mov");
    }

    @Test
    void rejectsFileWithNoExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "clip", "video/mp4", mp4IsomBrand());
        assertThatThrownBy(() -> validator.validate(file, ContentType.VIDEO))
                .isInstanceOf(UploadException.class);
    }

    @Test
    void rejectsOversizedFile() {
        byte[] oversized = new byte[(int) ContentType.HTML.getMaxSizeBytes() + 1];
        Arrays.fill(oversized, (byte) 'a');
        MockMultipartFile file = new MockMultipartFile("file", "big.html", "text/html", oversized);
        assertThatThrownBy(() -> validator.validate(file, ContentType.HTML))
                .isInstanceOf(UploadException.class)
                .hasMessageContaining("smaller");
    }

    @Test
    void rejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.mp4", "video/mp4", new byte[0]);
        assertThatThrownBy(() -> validator.validate(file, ContentType.VIDEO))
                .isInstanceOf(UploadException.class);
    }
}
