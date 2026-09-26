package com.secureportal.course;

/** The admin chose to let learners view this material but not download it. */
public class MaterialDownloadNotAllowedException extends RuntimeException {

    public MaterialDownloadNotAllowedException() {
        super("This material can't be downloaded. You can view it here in the portal.");
    }
}
