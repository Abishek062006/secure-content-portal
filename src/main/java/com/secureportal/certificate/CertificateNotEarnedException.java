package com.secureportal.certificate;

import java.util.List;

/** The learner asked for a certificate before finishing everything the course requires. */
public class CertificateNotEarnedException extends RuntimeException {

    private final List<String> missing;

    public CertificateNotEarnedException(List<String> missing) {
        super("Not everything required for this certificate is done yet: " + String.join(" ", missing));
        this.missing = missing;
    }

    public List<String> getMissing() {
        return missing;
    }
}
