package com.secureportal.hackathon;

import com.secureportal.notification.NotificationCategory;
import com.secureportal.notification.NotificationPriority;
import com.secureportal.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class HackathonDeadlineReminderJob {

    private static final Logger log = LoggerFactory.getLogger(HackathonDeadlineReminderJob.class);

    private final HackathonRepository hackathons;
    private final HackathonTeamRepository teams;
    private final HackathonTeamMemberRepository members;
    private final NotificationService notifications;

    public HackathonDeadlineReminderJob(HackathonRepository hackathons, HackathonTeamRepository teams,
                                        HackathonTeamMemberRepository members, NotificationService notifications) {
        this.hackathons = hackathons;
        this.teams = teams;
        this.members = members;
        this.notifications = notifications;
    }

    @Scheduled(initialDelay = 10000, fixedDelay = 60000)
    @Transactional
    public void checkDeadlineReminders() {
        Instant now = Instant.now();
        List<Hackathon> hostedEvents = hackathons.findAll().stream()
                .filter(h -> h.isHosted() && h.getResultsPublishedAt() == null && h.getEventEndDate() != null)
                .toList();

        for (Hackathon h : hostedEvents) {
            if (h.phase(now) != HackathonPhase.BUILDING) {
                continue;
            }
            long diffSeconds = ChronoUnit.SECONDS.between(now, h.getEventEndDate());
            if (diffSeconds <= 0) {
                continue;
            }

            // 24-hour reminder (diff between 1 hour and 24 hours)
            if (diffSeconds <= 86400 && diffSeconds > 3600 && !h.isReminded24h()) {
                notifyParticipants(h, "24 Hours Remaining: Submission Deadline",
                        "The submission deadline for \"" + h.getTitle() + "\" is in 24 hours. Ensure your team submits your solution in the Hackathon Workspace.",
                        NotificationPriority.IMPORTANT);
                h.setReminded24h(true);
                hackathons.save(h);
                log.info("Sent 24-hour deadline reminder for hackathon ID: {}", h.getId());
            }

            // 1-hour reminder (diff between 0 and 1 hour)
            if (diffSeconds <= 3600 && !h.isReminded1h()) {
                notifyParticipants(h, "1 Hour Remaining: Submissions Closing Soon!",
                        "Submissions for \"" + h.getTitle() + "\" will permanently lock in 1 hour. Finalize your project details immediately in the Hackathon Workspace!",
                        NotificationPriority.CRITICAL);
                h.setReminded1h(true);
                hackathons.save(h);
                log.info("Sent 1-hour deadline reminder for hackathon ID: {}", h.getId());
            }
        }
    }

    private void notifyParticipants(Hackathon h, String title, String message, NotificationPriority priority) {
        List<HackathonTeam> teamList = teams.findByHackathonId(h.getId());
        if (teamList.isEmpty()) {
            return;
        }
        List<HackathonTeamMember> memberList = members.findByTeamIdIn(
                teamList.stream().map(HackathonTeam::getId).toList()
        );
        String actionUrl = "/hackathons/" + h.getId() + "/workspace";
        for (HackathonTeamMember member : memberList) {
            try {
                notifications.createNotification(member.getUserId(), NotificationCategory.HACKATHON,
                        title, message, priority, actionUrl);
            } catch (Exception e) {
                log.warn("Failed to send deadline reminder to user {}: {}", member.getUserId(), e.getMessage());
            }
        }
    }
}
