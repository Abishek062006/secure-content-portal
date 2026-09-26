package com.secureportal.hackathon;

/** Where a hosted event is on its timeline. Worked out from the dates, so it advances by itself. */
public enum HackathonPhase {
    /** Before the build starts. Teams can form until the registration deadline. */
    REGISTRATION,
    /** Teams are building; submissions are accepted until the event ends. */
    BUILDING,
    /** Submissions are closed; judges score them. */
    JUDGING,
    /** An admin has published the results. */
    RESULTS
}
