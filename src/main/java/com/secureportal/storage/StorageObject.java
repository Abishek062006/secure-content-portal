package com.secureportal.storage;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

/**
 * A byte range read back from private storage. {@code totalSize} is the full
 * object size regardless of what range was requested, so callers can build a
 * correct {@code Content-Range} header for partial responses.
 */
public record StorageObject(
        InputStream content,
        long rangeStart,
        long rangeEnd,
        long totalSize,
        String contentType
) implements Closeable {

    public long rangeLength() {
        return rangeEnd - rangeStart + 1;
    }

    public boolean isPartial() {
        return rangeLength() < totalSize;
    }

    @Override
    public void close() throws IOException {
        content.close();
    }
}
