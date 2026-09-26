package com.secureportal.video;

/** A direct upload can't proceed (unknown, finished, or the stored file isn't what it claimed to be). */
public class VideoUploadException extends RuntimeException {

    public VideoUploadException(String message) {
        super(message);
    }
}
