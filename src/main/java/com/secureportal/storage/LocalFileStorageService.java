package com.secureportal.storage;

import com.secureportal.config.StorageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Disk-backed storage for running the app without any cloud bucket. Keys map
 * to paths under one root directory; nothing here is reachable except through
 * the same ticket-gated controllers that front the S3 implementation.
 */
@Service
@ConditionalOnProperty(name = "storage.provider", havingValue = "local")
public class LocalFileStorageService implements StorageService {

    private final Path root;

    public LocalFileStorageService(StorageProperties properties) {
        this.root = Path.of(properties.getLocalPath()).toAbsolutePath().normalize();
    }

    @Override
    public void put(String key, InputStream data, long contentLength, String contentType) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(data, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + key, e);
        }
    }

    @Override
    public StorageObject get(String key, Long rangeStart, Long rangeEnd) {
        Path file = resolve(key);
        if (!Files.isRegularFile(file)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        try {
            long total = Files.size(file);
            long start = rangeStart != null ? rangeStart : 0;
            long end = rangeEnd != null ? Math.min(rangeEnd, total - 1) : total - 1;
            if (start > end) {
                throw new ResponseStatusException(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
            }
            InputStream in = Files.newInputStream(file);
            in.skipNBytes(start);
            return new StorageObject(new LimitedInputStream(in, end - start + 1), start, end, total,
                    "application/octet-stream");
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not delete " + key, e);
        }
    }

    @Override
    public boolean exists(String key) {
        return Files.isRegularFile(resolve(key));
    }

    private Path resolve(String key) {
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Storage key escapes the storage root: " + key);
        }
        return resolved;
    }

    private static final class LimitedInputStream extends FilterInputStream {
        private long remaining;

        LimitedInputStream(InputStream in, long limit) {
            super(in);
            this.remaining = limit;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int b = super.read();
            if (b >= 0) {
                remaining--;
            }
            return b;
        }

        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int n = super.read(buf, off, (int) Math.min(len, remaining));
            if (n > 0) {
                remaining -= n;
            }
            return n;
        }
    }
}
