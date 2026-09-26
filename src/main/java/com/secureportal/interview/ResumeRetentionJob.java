package com.secureportal.interview;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Deletes resumes that have passed their retention period. */
@Component
@EnableScheduling
public class ResumeRetentionJob {

    private final ResumeService resumes;

    public ResumeRetentionJob(ResumeService resumes) {
        this.resumes = resumes;
    }

    @Scheduled(initialDelayString = "PT10M", fixedDelayString = "PT6H")
    public void run() {
        resumes.deleteExpired();
    }
}
