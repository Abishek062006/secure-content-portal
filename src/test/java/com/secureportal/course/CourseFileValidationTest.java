package com.secureportal.course;

import com.secureportal.common.FileValidator;
import com.secureportal.common.UploadException;
import com.secureportal.common.ValidatedFile;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CourseFileValidationTest {

    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

    private final FileValidator validator = new FileValidator();

    @Test
    void acceptsARealPngThumbnail() {
        ValidatedFile file = validator.validateThumbnail(new MockMultipartFile("t", "cover.png", "image/png", PNG));

        assertThat(file.detectedMimeType()).isEqualTo("image/png");
    }

    @Test
    void rejectsAThumbnailWhoseBytesAreNotAnImage() {
        var fake = new MockMultipartFile("t", "cover.png", "image/png", "not an image".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> validator.validateThumbnail(fake))
                .isInstanceOf(UploadException.class)
                .hasMessageContaining("Renaming a file's extension");
    }

    @Test
    void rejectsAThumbnailWithADisallowedExtension() {
        var gif = new MockMultipartFile("t", "cover.gif", "image/gif", PNG);

        assertThatThrownBy(() -> validator.validateThumbnail(gif)).isInstanceOf(UploadException.class);
    }

    @Test
    void acceptsAWebVttTranscript() {
        var vtt = new MockMultipartFile("t", "zoom.vtt", "text/vtt",
                "WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nHi\n".getBytes(StandardCharsets.UTF_8));

        assertThat(validator.validateTranscript(vtt).originalFilename()).isEqualTo("zoom.vtt");
    }

    @Test
    void rejectsATranscriptThatDoesNotStartWithTheWebVttSignature() {
        var notVtt = new MockMultipartFile("t", "zoom.vtt", "text/vtt", "just some text".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> validator.validateTranscript(notVtt))
                .isInstanceOf(UploadException.class)
                .hasMessageContaining("WEBVTT");
    }

    @Test
    void rejectsATranscriptWithTheWrongExtension() {
        var txt = new MockMultipartFile("t", "zoom.txt", "text/plain", "WEBVTT".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> validator.validateTranscript(txt)).isInstanceOf(UploadException.class);
    }
}
