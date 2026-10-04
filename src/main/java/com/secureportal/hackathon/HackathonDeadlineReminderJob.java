package com.secureportal.hackathon;

import com.secureportal.notification.NotificationCategory;
import com.secureportal.notification.NotificationPriority;
import com.secureportal.notification.NotificationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tells teams that haven't turned their project in when the deadline is a day and an hour away. Each reminder goes out once per
 * event (the flags on the event say so), and only to teams that still have something to submit.
 */
@Component
public class HackathonDeadlineReminderJob {

    private static final Duration DAY = Duration.ofHours(24);
    private static final Duration HOUR = Duration.ofHours(1);

    private final HackathonRepository hackathons;
    private final HackathonTeamRepository teams;
    private final HackathonTeamMemberRepository members;
    private final HackathonSubmissionRepository submissions;
    private final NotificationService notifications;

    public HackathonDeadlineReminderJob(HackathonRepository hackathons, HackathonTeamRepository teams, HackathonTeamMemberRepository members,
                                        HackathonSubmissionRepository submissions, NotificationService notifications) {
        this.hackathons = hackathons;
        this.teams = teams;
        this.members = members;
        this.submissions = submissions;
        this.notifications = notifications;
    }

    @Scheduled(initialDelayString = "PT30S", fixedDelayString = "PT1M")
    public void run() {
        remindDue(Instant.now());
    }

    @Transactional
    public void remindDue(Instant now) {
        for (Hackathon hackathon : hackathons.endingBefore(now, now.plus(DAY))) {
            Duration left = Duration.between(now, hackathon.getEventEndDate());
            if (left.compareTo(HOUR) <= 0) {
                if (!hackathon.isReminded1h()) {
                    remind(hackathon, "1 Hour Left to Submit",
                            "Submissions for \"" + hackathon.getTitle() + "\" lock in under an hour. Finish your project details in the workspace now.",
                            NotificationPriority.CRITICAL);
                    hackathon.markReminded1h();
                    hackathon.markReminded24h(); // too late for the day-ahead reminder to make sense
                }
            } else if (!hackathon.isReminded24h()) {
                remind(hackathon, "Submission Deadline Approaching",
                        "Submissions for \"" + hackathon.getTitle() + "\" lock in about " + left.toHours() + " hours. Make sure your team submits its project.",
                        NotificationPriority.IMPORTANT);
                hackathon.markReminded24h();
            }
            hackathons.save(hackathon);
        }
    }

    private void remind(Hackathon hackathon, String title, String message, NotificationPriority priority) {
        Set<Long> turnedIn = submissions.findByHackathonIdOrderByIdAsc(hackathon.getId()).stream()
                .filter(HackathonSubmission::isSubmitted).map(HackathonSubmission::getTeamId).collect(Collectors.toSet());
        List<Long> waiting = teams.findByHackathonId(hackathon.getId()).stream().map(HackathonTeam::getId)
                .filter(id -> !turnedIn.contains(id)).toList();
        if (waiting.isEmpty()) {
            return;
        }
        String workspace = "/hackathons/" + hackathon.getId() + "/workspace";
        for (HackathonTeamMember member : members.findByTeamIdIn(waiting)) {
            notifications.createNotification(member.getUserId(), NotificationCategory.HACKATHON, title, message, priority, workspace);
        }
    }
}
