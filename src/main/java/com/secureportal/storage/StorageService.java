package com.secureportal.storage;

import java.io.InputStream;

/**
 * Private object storage. There is no method here that returns a public URL —
 * that is deliberate. The only way to reach an object's bytes is through this
 * interface, from server-side code, gated by whatever the caller decides
 * (a signed ticket, an authorization check, etc.).
 */
public interface StorageService {

    void put(String key, InputStream data, long contentLength, String contentType);

    /**
     * Reads back the given inclusive byte range. Pass {@code null} for both
     * bounds to read the whole object.
     */
    StorageObject get(String key, Long rangeStart, Long rangeEnd);

    void delete(String key);

    boolean exists(String key);
}
