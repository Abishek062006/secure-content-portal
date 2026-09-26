package com.secureportal.video;

import com.secureportal.common.FileValidator;
import com.secureportal.common.UploadException;
import com.secureportal.config.StorageProperties;
import com.secureportal.course.CourseModule;
import com.secureportal.course.CourseStructureService;
import com.secureportal.storage.StorageService;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DirectUploadServiceTest {

    private final UUID module = UUID.randomUUID();
    private final UUID course = UUID.randomUUID();
    private final S3Client s3 = mock(S3Client.class);
    private final VideoUploadRepository repository = mock(VideoUploadRepository.class);
    private final CourseStructureService structure = mock(CourseStructureService.class);
    private final DirectUploadService service;

    DirectUploadServiceTest() {
        StorageProperties properties = new StorageProperties();
        properties.setBucket("content");
        CourseModule found = mock(CourseModule.class);
        when(found.getCourseId()).thenReturn(course);
        when(structure.findModule(module)).thenReturn(found);
        when(s3.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn(CreateMultipartUploadResponse.builder().uploadId("s3-upload-1").build());
        when(repository.save(any(VideoUpload.class))).thenAnswer(i -> i.getArgument(0));
        service = new DirectUploadService(s3, mock(S3Presigner.class), properties, repository, structure, new FileValidator(),
                mock(StorageService.class));
    }

    @Test
    void startingPlansTheMultipartUploadAndKeepsTheFileInsideTheCoursesFolder() {
        VideoUpload upload = service.start(module, "  Intro  ", null, "../../etc/My Lecture (1).MP4", 40L * 1024 * 1024, 7L);

        assertThat(upload.getStorageKey()).startsWith("courses/" + course + "/lessons/").endsWith("/video/My_Lecture__1_.MP4");
        assertThat(upload.getStorageKey()).doesNotContain("..");
        assertThat(upload.getTitle()).isEqualTo("Intro");
        assertThat(upload.partCount()).as("40 MiB in 16 MiB parts").isEqualTo(3);
        assertThat(upload.getS3UploadId()).isEqualTo("s3-upload-1");
        assertThat(upload.getStatus()).isEqualTo(UploadStatus.UPLOADING);
    }

    @Test
    void thePartCountRoundsUpAndFitsTheFiveGigabyteCapWellInsideS3sLimit() {
        assertThat(service.start(module, "T", null, "a.mp4", 1, 1L).partCount()).isEqualTo(1);
        assertThat(service.start(module, "T", null, "a.mp4", DirectUploadService.PART_SIZE, 1L).partCount()).isEqualTo(1);
        assertThat(service.start(module, "T", null, "a.mp4", DirectUploadService.PART_SIZE + 1, 1L).partCount()).isEqualTo(2);
        assertThat(service.start(module, "T", null, "a.mp4", 5L * 1024 * 1024 * 1024, 1L).partCount()).isLessThan(10_000).isGreaterThan(300);
    }

    @Test
    void badRequestsAreRefusedBeforeAnythingIsCreatedInStorage() {
        assertThatThrownBy(() -> service.start(module, "T", null, "malware.exe", 100, 1L)).isInstanceOf(UploadException.class);
        assertThatThrownBy(() -> service.start(module, "T", null, "noextension", 100, 1L)).isInstanceOf(UploadException.class);
        assertThatThrownBy(() -> service.start(module, "T", null, "a.mp4", 0, 1L)).isInstanceOf(UploadException.class);
        assertThatThrownBy(() -> service.start(module, "T", null, "a.mp4", 6L * 1024 * 1024 * 1024, 1L)).isInstanceOf(UploadException.class);
        assertThatThrownBy(() -> service.start(module, "  ", null, "a.mp4", 100, 1L)).isInstanceOf(UploadException.class);
        verify(s3, never()).createMultipartUpload(any(CreateMultipartUploadRequest.class));
    }

    @Test
    void unknownOrFinishedUploadsCannotBeUsed() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.presign(id, List.of(1))).isInstanceOf(VideoUploadException.class);

        VideoUpload done = new VideoUpload(id, module, course, "T", null, "a.mp4", 10, DirectUploadService.PART_SIZE, "k", "u", 1L);
        done.markComplete();
        when(repository.findById(id)).thenReturn(Optional.of(done));
        assertThatThrownBy(() -> service.uploaded(id)).hasMessageContaining("already finished");
        assertThatThrownBy(() -> service.complete(id, List.of())).hasMessageContaining("already finished");
    }

    @Test
    void completingWithTheWrongNumberOfPartsIsRefusedAndNeverTouchesStorage() {
        VideoUpload upload = new VideoUpload(UUID.randomUUID(), module, course, "T", null, "a.mp4", 40L * 1024 * 1024,
                DirectUploadService.PART_SIZE, "k", "u", 1L);
        when(repository.findById(upload.getId())).thenReturn(Optional.of(upload));

        assertThatThrownBy(() -> service.complete(upload.getId(), List.of(new DirectUploadService.Part(1, "e1"))))
                .hasMessageContaining("expected 3, got 1");
        verify(s3, never()).completeMultipartUpload(any(software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest.class));
    }

    @Test
    void storedVideosAreCheckedByExtensionSizeAndWhatTheirFirstBytesAre() {
        FileValidator validator = new FileValidator();
        byte[] mp4 = {0x00, 0x00, 0x00, 0x1C, 'f', 't', 'y', 'p', 'm', 'p', '4', '2', 0, 0, 0, 0, 'm', 'p', '4', '2', 'i', 's', 'o', 'm'};

        assertThat(validator.validateStoredVideo("lecture.mp4", 1000, mp4).detectedMimeType()).isEqualTo("video/mp4");
        assertThatThrownBy(() -> validator.validateStoredVideo("lecture.mp4", 1000, "just text".getBytes())).hasMessageContaining("don't match a video");
        assertThatThrownBy(() -> validator.validateStoredVideo("lecture.exe", 1000, mp4)).isInstanceOf(UploadException.class);
        assertThatThrownBy(() -> validator.validateStoredVideo("lecture.mp4", 6L * 1024 * 1024 * 1024, mp4)).isInstanceOf(UploadException.class);
    }
}
