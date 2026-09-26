package com.secureportal.jobs;

public class GenerationJobNotFoundException extends RuntimeException {

    public GenerationJobNotFoundException() {
        super("That job doesn't exist.");
    }
}
