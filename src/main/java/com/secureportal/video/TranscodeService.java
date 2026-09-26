package com.secureportal.video;

import com.secureportal.course.HlsStatus;
import com.secureportal.course.Lesson;
import com.secureportal.course.LessonRepository;
import com.secureportal.storage.StorageObject;
import com.secureportal.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Turns a lesson's uploaded video into adaptive HLS: several sizes cut into short segments, so playback starts
 * fast, seeks quickly and follows the viewer's connection. The original stays in storage and plays until this is done.
 */
@Service
public class TranscodeService {

    private static final Logger log = LoggerFactory.getLogger(TranscodeService.class);

    private final LessonRepository lessonRepository;
    private final StorageService storageService;
    private final TranscodeProperties properties;

    public TranscodeService(LessonRepository lessonRepository, StorageService storageService, TranscodeProperties properties) {
        this.lessonRepository = lessonRepository;
        this.storageService = storageService;
        this.properties = properties;
    }

    public void transcode(UUID lessonId) {
        Lesson lesson = lessonRepository.findById(lessonId).orElse(null);
        if (lesson == null) {
            return;
        }
        Path work = null;
        try {
            lesson.setHls(HlsStatus.PROCESSING, null);
            lessonRepository.save(lesson);

            work = Files.createTempDirectory("transcode-");
            Path source = work.resolve("source" + extension(lesson.getVideoFilename()));
            try (StorageObject object = storageService.get(lesson.getVideoKey(), null, null); InputStream in = object.content()) {
                Files.copy(in, source);
            }

            int[] size = probe(source);
            List<Hls.Rendition> renditions = Hls.plan(size[0], size[1], properties.getHeights());
            Path out = work.resolve("out");
            for (Hls.Rendition rendition : renditions) {
                encode(source, out.resolve(rendition.directory()), rendition);
            }
            Files.writeString(out.resolve("master.m3u8"), Hls.masterPlaylist(renditions), StandardCharsets.UTF_8);

            storageService.deletePrefix(lesson.hlsPrefix());
            upload(out, lesson.hlsPrefix());

            lesson = lessonRepository.findById(lessonId).orElse(null);
            if (lesson == null) {
                // Deleted while it was being processed: don't leave its files behind.
                storageService.deletePrefix("hls/" + lessonId + "/");
                return;
            }
            lesson.setHls(HlsStatus.READY, null);
            lessonRepository.save(lesson);
            log.info("HLS ready for lesson {} ({} renditions)", lessonId, renditions.size());
        } catch (Exception e) {
            log.warn("Transcoding lesson {} failed", lessonId, e);
            lessonRepository.findById(lessonId).ifPresent(l -> {
                l.setHls(HlsStatus.FAILED, message(e));
                lessonRepository.save(l);
            });
        } finally {
            if (work != null) {
                deleteQuietly(work);
            }
        }
    }

    private String message(Exception e) {
        if (e instanceof IOException && e.getMessage() != null && e.getMessage().contains("No such file")) {
            return "ffmpeg isn't installed on the server, so the original video is used instead.";
        }
        return "Could not prepare adaptive streaming; the original video is used instead. " + e.getMessage();
    }

    /** Width and height of the first video stream. */
    private int[] probe(Path source) throws IOException, InterruptedException {
        String output = run(List.of(properties.getFfprobe(), "-v", "error", "-select_streams", "v:0",
                "-show_entries", "stream=width,height", "-of", "csv=p=0:s=x", source.toString()), null).trim();
        String[] parts = output.lines().findFirst().orElse("").split("x");
        try {
            return new int[]{Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())};
        } catch (RuntimeException e) {
            throw new IOException("The video has no readable picture (ffprobe said: " + output + ")");
        }
    }

    private void encode(Path source, Path dir, Hls.Rendition rendition) throws IOException, InterruptedException {
        Files.createDirectories(dir);
        List<String> command = new ArrayList<>(List.of(properties.getFfmpeg(), "-y", "-i", source.toString(),
                "-vf", "scale=" + rendition.width() + ":" + rendition.height(),
                "-c:v", "libx264", "-preset", "veryfast", "-profile:v", "main", "-pix_fmt", "yuv420p",
                "-b:v", rendition.videoKbps() + "k", "-maxrate", (int) (rendition.videoKbps() * 1.2) + "k",
                "-bufsize", rendition.videoKbps() * 2 + "k", "-g", String.valueOf(properties.getSegmentSeconds() * 25),
                "-keyint_min", String.valueOf(properties.getSegmentSeconds() * 25), "-sc_threshold", "0",
                "-c:a", "aac", "-b:a", "128k", "-ac", "2",
                "-f", "hls", "-hls_time", String.valueOf(properties.getSegmentSeconds()), "-hls_playlist_type", "vod",
                "-hls_segment_filename", dir.resolve("seg_%04d.ts").toString(), dir.resolve("index.m3u8").toString()));
        run(command, dir);
    }

    private String run(List<String> command, Path workingDir) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        if (workingDir != null) {
            builder.directory(workingDir.toFile());
        }
        Process process = builder.start();
        // Drain output on its own thread so a chatty ffmpeg can never fill the pipe and stall, and so the wait below
        // can really time out.
        java.io.ByteArrayOutputStream captured = new java.io.ByteArrayOutputStream();
        Thread reader = new Thread(() -> {
            try (InputStream in = process.getInputStream()) {
                in.transferTo(captured);
            } catch (IOException ignored) {
                // The process ended or was killed; whatever was read is enough for the error message.
            }
        }, "ffmpeg-output");
        reader.setDaemon(true);
        reader.start();
        if (!process.waitFor(properties.getTimeoutMinutes(), TimeUnit.MINUTES)) {
            process.destroyForcibly();
            throw new IOException("The command took too long and was stopped: " + command.get(0));
        }
        reader.join(5000);
        String text = captured.toString(StandardCharsets.UTF_8);
        if (process.exitValue() != 0) {
            String tail = text.length() > 600 ? text.substring(text.length() - 600) : text;
            throw new IOException(command.get(0) + " failed (exit " + process.exitValue() + "): " + tail.trim());
        }
        return text;
    }

    private void upload(Path out, String prefix) throws IOException {
        try (Stream<Path> files = Files.walk(out)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String key = prefix + out.relativize(file).toString().replace('\\', '/');
                try (InputStream in = Files.newInputStream(file)) {
                    storageService.put(key, in, Files.size(file), Hls.contentType(key));
                }
            }
        }
    }

    private static String extension(String filename) {
        int dot = filename == null ? -1 : filename.lastIndexOf('.');
        return dot < 0 ? ".mp4" : filename.substring(dot).replaceAll("[^a-zA-Z0-9.]", "");
    }

    private static void deleteQuietly(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException e) {
            log.warn("Could not clean {}", dir, e);
        }
    }
}
