package com.secureportal.stream;

import com.secureportal.storage.StorageObject;
import com.secureportal.storage.StorageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/** Streams a stored object honouring a single {@code Range} header, so the video player can seek. */
@Component
public class RangedMediaResponder {

    private final StorageService storageService;

    public RangedMediaResponder(StorageService storageService) {
        this.storageService = storageService;
    }

    public ResponseEntity<InputStreamResource> respond(String storageKey, String mimeType,
                                                       HttpServletRequest request) {
        RangeRequest range = RangeRequest.parse(request.getHeader(HttpHeaders.RANGE)).orElse(null);
        Long rangeStart = range != null ? range.start() : null;
        Long rangeEnd = range != null ? range.end() : null;

        StorageObject object = storageService.get(storageKey, rangeStart, rangeEnd);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ACCEPT_RANGES, "bytes");
        headers.add(HttpHeaders.CONTENT_TYPE, mimeType);
        headers.add(HttpHeaders.CONTENT_LENGTH, String.valueOf(object.rangeLength()));
        headers.add(HttpHeaders.CACHE_CONTROL, "no-store");
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline");
        headers.add("X-Content-Type-Options", "nosniff");

        HttpStatus status = HttpStatus.OK;
        if (range != null) {
            headers.add(HttpHeaders.CONTENT_RANGE,
                    "bytes " + object.rangeStart() + "-" + object.rangeEnd() + "/" + object.totalSize());
            status = HttpStatus.PARTIAL_CONTENT;
        }

        return ResponseEntity.status(status).headers(headers).body(new InputStreamResource(object.content()));
    }
}
