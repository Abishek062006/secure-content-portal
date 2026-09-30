package com.secureportal.hackathon;

import com.secureportal.audit.AuditService;
import com.secureportal.gamification.GamificationService;
import com.secureportal.notification.NotificationCategory;
import com.secureportal.notification.NotificationPriority;
import com.secureportal.notification.NotificationService;
import com.secureportal.user.User;
import com.secureportal.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Hosted hackathons: teams, submissions, judging and results. The phase comes from the event's dates, and every rule that depends on
 * it is checked here: teams form before the build starts, projects are submitted while it runs, judges score once it has closed, and
 * an admin publishes results only when every project has been scored.
 */
@Service
public class HostedHackathonService {

    static final Pattern TEAM_NAME = Pattern.compile("[\\p{L}\\p{N} _.'&-]{2,80}");
    private static final String CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 10;
    private static final int WINNERS = 3;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final HackathonRepository hackathons;
    private final HackathonTeamRepository teams;
    private final HackathonTeamMemberRepository members;
    private final HackathonSubmissionRepository submissions;
    private final HackathonJudgeRepository judges;
    private final HackathonScoreRepository scores;
    private final HackathonProblemStatementRepository problemStatements;
    private final HackathonCertificateRepository certificates;
    private final HackathonCertificatePdfRenderer certPdfRenderer;
    private final NotificationService notifications;
    private final UserRepository users;
    private final GamificationService gamification;
    private final AuditService audit;

    public HostedHackathonService(HackathonRepository hackathons, HackathonTeamRepository teams, HackathonTeamMemberRepository members,
                                  HackathonSubmissionRepository submissions, HackathonJudgeRepository judges,
                                  HackathonScoreRepository scores, HackathonProblemStatementRepository problemStatements,
                                  HackathonCertificateRepository certificates, HackathonCertificatePdfRenderer certPdfRenderer,
                                  NotificationService notifications, UserRepository users,
                                  GamificationService gamification, AuditService audit) {
        this.hackathons = hackathons;
        this.teams = teams;
        this.members = members;
        this.submissions = submissions;
        this.judges = judges;
        this.scores = scores;
        this.problemStatements = problemStatements;
        this.certificates = certificates;
        this.certPdfRenderer = certPdfRenderer;
        this.notifications = notifications;
        this.users = users;
        this.gamification = gamification;
        this.audit = audit;
    }

    // ---- What the callers see --------------------------------------------------------------------------------------------

    public record MemberView(Long userId, String name, String pictureUrl, boolean leader) {
    }

    public record SubmissionView(Long id, String title, String repoUrl, String demoUrl, String description,
                                 String status, Instant submittedAt, Instant updatedAt) {
    }

    public record TeamView(Long id, Long hackathonId, String name, String track, Long problemStatementId, String inviteCode, boolean leader,
                           List<MemberView> members, SubmissionView submission) {
    }

    public record ProblemStatementInput(String title, String description, String track, String requirements,
                                         String evaluationCriteria, String resourcesUrl) {
    }

    public record ProblemStatementView(Long id, Long hackathonId, String title, String description, String track,
                                       String requirements, String evaluationCriteria, String resourcesUrl,
                                       Instant createdAt, Instant updatedAt) {
        public static ProblemStatementView from(HackathonProblemStatement p) {
            return new ProblemStatementView(p.getId(), p.getHackathonId(), p.getTitle(), p.getDescription(), p.getTrack(),
                    p.getRequirements(), p.getEvaluationCriteria(), p.getResourcesUrl(), p.getCreatedAt(), p.getUpdatedAt());
        }
    }

    public record SubmissionInput(String title, String repoUrl, String demoUrl, String description, Boolean draft) {
    }

    public record ScoreInput(int innovation, int execution, int impact, int presentation, String comment) {
    }

    public record JudgeView(Long userId, String name, String email) {
    }

    public record HackathonCertificateView(Long id, String code, Long hackathonId, String hackathonTitle,
                                          String recipientName, String teamName, String type, Integer rank,
                                          Instant issuedAt) {
        public static HackathonCertificateView from(HackathonCertificate c) {
            return new HackathonCertificateView(c.getId(), c.getCode(), c.getHackathonId(),
                    c.getHackathonTitle(), c.getRecipientName(), c.getTeamName(),
                    c.getType().name(), c.getRank(), c.getIssuedAt());
        }
    }

    /** A project as a judge or admin sees it, with the judge's own score if they have given one. */
    public record ProjectView(Long submissionId, String teamName, String track, String title, String repoUrl, String demoUrl,
                              String description, ProblemStatementView problemStatement,
                              Integer myInnovation, Integer myExecution, Integer myImpact, Integer myPresentation,
                              String myComment, int scoreCount) {
    }

    public record RankedView(int rank, String teamName, String track, String problemStatementTitle,
                             List<String> members, String title, String repoUrl, String demoUrl,
                             double overall, double innovation, double execution, double impact,
                             double presentation, int scoreCount) {
    }

    // ---- Teams (learners) --------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Optional<TeamView> myTeam(Long hackathonId, Long userId) {
        hosted(hackathonId);
        return teams.findOfMember(hackathonId, userId).map(team -> view(team, userId));
    }

    @Transactional
    public TeamView createTeam(Long hackathonId, Long userId, String rawName, String rawTrack) {
        Hackathon hackathon = hosted(hackathonId);
        requireRegistrationOpen(hackathon);
        requireNotJudge(hackathonId, userId);
        if (members.existsByHackathonIdAndUserId(hackathonId, userId)) {
            throw new HackathonStateException("You're already on a team for this hackathon.");
        }
        String name = rawName == null ? "" : rawName.strip();
        if (!TEAM_NAME.matcher(name).matches()) {
            throw new InvalidHackathonException("Give your team a name of 2 to 80 letters, numbers or simple punctuation.");
        }
        String track = trackFor(hackathon, rawTrack);
        try {
            HackathonTeam team = teams.saveAndFlush(new HackathonTeam(hackathonId, name, track, newInviteCode(), userId));
            members.saveAndFlush(new HackathonTeamMember(team.getId(), hackathonId, userId));
            return view(team, userId);
        } catch (DataIntegrityViolationException e) {
            throw new HackathonStateException("That team name is already taken in this hackathon.");
        }
    }

    @Transactional
    public TeamView joinByCode(Long userId, String rawCode) {
        String code = rawCode == null ? "" : rawCode.strip().toUpperCase(Locale.ROOT);
        HackathonTeam found = teams.findByInviteCode(code).orElseThrow(() -> new InvalidHackathonException("That invite link isn't valid."));
        HackathonTeam team = teams.findForUpdate(found.getId()).orElseThrow(HackathonNotFoundException::new);
        Hackathon hackathon = hosted(team.getHackathonId());

        if (members.findByTeamIdOrderByJoinedAtAscIdAsc(team.getId()).stream().anyMatch(m -> m.getUserId().equals(userId))) {
            return view(team, userId);
        }
        requireRegistrationOpen(hackathon);
        requireNotJudge(hackathon.getId(), userId);
        if (members.existsByHackathonIdAndUserId(hackathon.getId(), userId)) {
            throw new HackathonStateException("You're already on a team for this hackathon. Leave it first to join another.");
        }
        if (members.countByTeamId(team.getId()) >= hackathon.getMaxTeamSize()) {
            throw new HackathonStateException("That team is full.");
        }
        members.saveAndFlush(new HackathonTeamMember(team.getId(), hackathon.getId(), userId));

        User joiningUser = users.findById(userId).orElse(null);
        String joinerName = joiningUser != null ? joiningUser.getDisplayName() : "A new member";
        for (HackathonTeamMember m : members.findByTeamIdOrderByJoinedAtAscIdAsc(team.getId())) {
            if (!m.getUserId().equals(userId)) {
                notifications.createNotification(m.getUserId(), NotificationCategory.HACKATHON,
                        "New Team Member Joined",
                        joinerName + " has joined your team \"" + team.getName() + "\" for \"" + hackathon.getTitle() + "\".",
                        NotificationPriority.NORMAL,
                        "/hackathons/" + hackathon.getId() + "/workspace");
            }
        }

        return view(team, userId);
    }

    /** Leaving is only possible while teams can still change. An empty team disappears; the leader role passes to the longest-standing member. */
    @Transactional
    public void leave(Long hackathonId, Long userId) {
        Hackathon hackathon = hosted(hackathonId);
        HackathonTeam found = teams.findOfMember(hackathonId, userId).orElseThrow(HackathonNotFoundException::new);
        HackathonTeam team = teams.findForUpdate(found.getId()).orElseThrow(HackathonNotFoundException::new);
        if (hackathon.phase(Instant.now()) != HackathonPhase.REGISTRATION) {
            throw new HackathonStateException("Teams are locked once the hackathon starts.");
        }
        User leavingUser = users.findById(userId).orElse(null);
        String leaverName = leavingUser != null ? leavingUser.getDisplayName() : "A team member";

        members.remove(team.getId(), userId);
        List<HackathonTeamMember> left = members.findByTeamIdOrderByJoinedAtAscIdAsc(team.getId());
        if (left.isEmpty()) {
            teams.delete(team);
        } else {
            if (team.getLeaderId().equals(userId)) {
                team.handLeadershipTo(left.get(0).getUserId());
                teams.save(team);
            }
            for (HackathonTeamMember m : left) {
                notifications.createNotification(m.getUserId(), NotificationCategory.HACKATHON,
                        "Team Member Left",
                        leaverName + " has left your team \"" + team.getName() + "\".",
                        NotificationPriority.NORMAL,
                        "/hackathons/" + hackathonId + "/workspace");
            }
        }
    }

    // ---- Submissions (learners) ---------------------------------------------------------------------------------------------

    @Transactional
    public SubmissionView submit(Long hackathonId, Long userId, SubmissionInput in) {
        Hackathon hackathon = hosted(hackathonId);
        Instant now = Instant.now();
        // Strict deadline locking: only allowed during BUILDING phase and strictly before the deadline
        if (hackathon.phase(now) != HackathonPhase.BUILDING || (hackathon.getEventEndDate() != null && !now.isBefore(hackathon.getEventEndDate()))) {
            throw new HackathonStateException("Submissions are locked. The deadline has passed or building is not active.");
        }
        HackathonTeam team = teams.findOfMember(hackathonId, userId)
                .orElseThrow(() -> new HackathonStateException("Join or create a team before submitting."));
        if (members.countByTeamId(team.getId()) < hackathon.getMinTeamSize()) {
            throw new HackathonStateException("Your team needs at least " + hackathon.getMinTeamSize() + " members to submit.");
        }
        boolean isDraft = Boolean.TRUE.equals(in.draft());
        String submissionStatus = isDraft ? "DRAFT" : "SUBMITTED";

        String title;
        String repo;
        String demo = in.demoUrl() == null || in.demoUrl().isBlank() ? null : httpsUrl(in.demoUrl(), 500, "The demo link", true);
        String description;

        if (isDraft) {
            title = text(in.title() == null || in.title().isBlank() ? "Untitled Project Draft" : in.title(), 1, 150, "The project title");
            repo = in.repoUrl() == null || in.repoUrl().isBlank() ? "https://github.com/draft-placeholder" : (in.repoUrl().startsWith("http") ? in.repoUrl() : "https://" + in.repoUrl());
            description = text(in.description() == null || in.description().isBlank() ? "Draft submission in progress..." : in.description(), 1, 3000, "The description");
        } else {
            title = text(in.title(), 3, 150, "The project title");
            repo = httpsUrl(in.repoUrl(), 500, "The code repository link", true);
            description = text(in.description(), 30, 3000, "The description");
        }

        HackathonSubmission submission = submissions.findByTeamId(team.getId()).map(existing -> {
            existing.update(title, repo, demo, description, submissionStatus);
            return existing;
        }).orElseGet(() -> new HackathonSubmission(team.getId(), hackathonId, title, repo, demo, description, submissionStatus));
        HackathonSubmission saved = submissions.save(submission);
        User submitter = users.findById(userId).orElse(null);
        String submitterName = submitter != null ? submitter.getDisplayName() : "A team member";
        String action = isDraft ? "saved a draft of" : "submitted/updated";
        for (HackathonTeamMember m : members.findByTeamIdOrderByJoinedAtAscIdAsc(team.getId())) {
            if (!m.getUserId().equals(userId)) {
                notifications.createNotification(m.getUserId(), NotificationCategory.HACKATHON,
                        isDraft ? "Draft Project Updated" : "Project Submitted",
                        submitterName + " has " + action + " the project \"" + title + "\" for team \"" + team.getName() + "\".",
                        NotificationPriority.NORMAL,
                        "/hackathons/" + hackathonId + "/workspace");
            }
        }
        return submissionView(saved, hackathonId);
    }

    // ---- Judging ----------------------------------------------------------------------------------------------------------

    /** Hackathons the user has been asked to judge, once their submissions are closed. */
    @Transactional(readOnly = true)
    public List<Hackathon> judgedEvents(Long userId) {
        Instant now = Instant.now();
        return judges.findByUserId(userId).stream().map(j -> hackathons.findById(j.getHackathonId()).orElse(null))
                .filter(h -> h != null && h.isHosted() && h.phase(now) != HackathonPhase.REGISTRATION && h.phase(now) != HackathonPhase.BUILDING)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProjectView> projectsForJudge(Long hackathonId, Long judgeId) {
        Hackathon hackathon = hosted(hackathonId);
        requireJudge(hackathonId, judgeId);
        if (hackathon.phase(Instant.now()) != HackathonPhase.JUDGING) {
            throw new HackathonStateException("Scoring is open only after submissions close and before results are published.");
        }
        return projects(hackathonId, judgeId);
    }

    @Transactional
    public void score(Long hackathonId, Long submissionId, Long judgeId, ScoreInput in) {
        Hackathon hackathon = hosted(hackathonId);
        requireJudge(hackathonId, judgeId);
        if (hackathon.phase(Instant.now()) != HackathonPhase.JUDGING) {
            throw new HackathonStateException("Scoring is open only after submissions close and before results are published.");
        }
        HackathonSubmission submission = submissions.findByIdAndHackathonId(submissionId, hackathonId).orElseThrow(HackathonNotFoundException::new);
        if ("DRAFT".equalsIgnoreCase(submission.getStatus())) {
            throw new HackathonStateException("Draft submissions cannot be scored.");
        }
        for (int value : new int[]{in.innovation(), in.execution(), in.impact(), in.presentation()}) {
            if (value < 1 || value > 10) {
                throw new InvalidHackathonException("Each score must be between 1 and 10.");
            }
        }
        String comment = in.comment() == null || in.comment().isBlank() ? null : text(in.comment(), 1, 1000, "The comment");
        HackathonScore score = scores.findBySubmissionIdAndJudgeId(submission.getId(), judgeId).orElse(null);
        if (score == null) {
            scores.save(new HackathonScore(submission.getId(), judgeId, in.innovation(), in.execution(), in.impact(), in.presentation(), comment));
        } else {
            score.set(in.innovation(), in.execution(), in.impact(), in.presentation(), comment);
            scores.save(score);
        }
    }

    // ---- Admin ------------------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<JudgeView> judgesOf(Long hackathonId) {
        hosted(hackathonId);
        return judges.findByHackathonIdOrderByIdAsc(hackathonId).stream().map(j -> users.findById(j.getUserId()).orElse(null))
                .filter(u -> u != null).map(u -> new JudgeView(u.getId(), u.getDisplayName(), u.getEmail())).toList();
    }

    @Transactional
    public JudgeView addJudge(Long hackathonId, String rawEmail, String adminEmail) {
        Hackathon hackathon = hosted(hackathonId);
        if (hackathon.getResultsPublishedAt() != null) {
            throw new HackathonStateException("Results are published, so judges can't change.");
        }
        String email = rawEmail == null ? "" : rawEmail.strip();
        User judge = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new InvalidHackathonException("No one has signed up with that email address yet."));
        if (members.existsByHackathonIdAndUserId(hackathonId, judge.getId())) {
            throw new HackathonStateException("That person is on a team in this hackathon, so they can't judge it.");
        }
        if (judges.existsByHackathonIdAndUserId(hackathonId, judge.getId())) {
            throw new HackathonStateException("That person is already a judge.");
        }
        judges.save(new HackathonJudge(hackathonId, judge.getId()));
        notifications.createNotification(judge.getId(), NotificationCategory.HACKATHON,
                "Assigned as Hackathon Judge",
                "You have been appointed as a judge for \"" + hackathon.getTitle() + "\".",
                NotificationPriority.IMPORTANT,
                "/hackathons/" + hackathon.getId() + "/judge");
        audit.log(adminEmail, "HACKATHON_JUDGE_ADD", null, "Added " + judge.getEmail() + " as a judge of \"" + hackathon.getTitle() + "\"");
        return new JudgeView(judge.getId(), judge.getDisplayName(), judge.getEmail());
    }

    @Transactional
    public void removeJudge(Long hackathonId, Long userId, String adminEmail) {
        Hackathon hackathon = hosted(hackathonId);
        if (hackathon.getResultsPublishedAt() != null) {
            throw new HackathonStateException("Results are published, so judges can't change.");
        }
        if (judges.remove(hackathonId, userId) > 0) {
            audit.log(adminEmail, "HACKATHON_JUDGE_REMOVE", null, "Removed a judge from \"" + hackathon.getTitle() + "\"");
        }
    }

    /** Every submitted project with how many scores it has: what an admin checks before publishing. */
    @Transactional(readOnly = true)
    public List<ProjectView> projectsForAdmin(Long hackathonId) {
        hosted(hackathonId);
        return projects(hackathonId, null);
    }

    /** The live ranking, from the scores of the current judges. */
    @Transactional(readOnly = true)
    public List<RankedView> standings(Long hackathonId) {
        hosted(hackathonId);
        return ranking(hackathonId);
    }

    @Transactional
    public void publishResults(Long hackathonId, String adminEmail) {
        Hackathon hackathon = hosted(hackathonId);
        if (hackathon.getResultsPublishedAt() != null) {
            throw new HackathonStateException("The results are already published.");
        }
        if (hackathon.phase(Instant.now()) != HackathonPhase.JUDGING) {
            throw new HackathonStateException("Results can be published once submissions have closed.");
        }
        if (judges.findByHackathonIdOrderByIdAsc(hackathonId).isEmpty()) {
            throw new HackathonStateException("Add at least one judge first.");
        }
        List<RankedView> ranking = ranking(hackathonId);
        if (ranking.isEmpty()) {
            throw new HackathonStateException("There are no submissions to publish.");
        }
        if (ranking.stream().anyMatch(r -> r.scoreCount() == 0)) {
            throw new HackathonStateException("Every project needs at least one score before results can be published.");
        }

        hackathon.publishResults(Instant.now());
        hackathons.save(hackathon);

        Map<Long, List<HackathonTeamMember>> byTeam = new HashMap<>();
        for (HackathonSubmission submission : submissions.findByHackathonIdOrderByIdAsc(hackathonId)) {
            if (!"DRAFT".equalsIgnoreCase(submission.getStatus())) {
                byTeam.put(submission.getTeamId(), members.findByTeamIdOrderByJoinedAtAscIdAsc(submission.getTeamId()));
            }
        }
        Map<String, Integer> rankOfTeam = new HashMap<>();
        ranking.forEach(r -> rankOfTeam.put(r.teamName(), r.rank()));

        List<HackathonTeam> allTeams = teams.findByHackathonId(hackathonId);
        List<HackathonTeamMember> allMembers = members.findByHackathonId(hackathonId);
        Map<Long, User> userCache = usersById(allMembers.stream().map(HackathonTeamMember::getUserId).toList());

        for (HackathonTeam team : allTeams) {
            List<HackathonTeamMember> crew = byTeam.get(team.getId());
            if (crew == null || crew.isEmpty()) {
                continue; // no submission: nothing to recognise
            }
            int rank = rankOfTeam.getOrDefault(team.getName(), Integer.MAX_VALUE);
            boolean winner = rank <= WINNERS;
            HackathonCertificateType certType;
            if (rank == 1) {
                certType = HackathonCertificateType.WINNER;
            } else if (rank == 2 || rank == 3) {
                certType = HackathonCertificateType.RUNNER_UP;
            } else {
                certType = HackathonCertificateType.PARTICIPATION;
            }

            for (HackathonTeamMember member : crew) {
                gamification.grantBadge(member.getUserId(), "HACKATHON_BUILDER");
                if (winner) {
                    gamification.grantBadge(member.getUserId(), "HACKATHON_WINNER");
                }

                if (certificates.findByHackathonIdAndUserId(hackathonId, member.getUserId()).isEmpty()) {
                    String certCode = "HACK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
                    User u = userCache.get(member.getUserId());
                    String recipientName = u != null ? u.getDisplayName() : "Participant";
                    HackathonCertificate cert = new HackathonCertificate(hackathonId, member.getUserId(), certCode,
                            hackathon.getTitle(), recipientName, team.getName(), certType, rank);
                    certificates.save(cert);

                    String certTitle = switch (certType) {
                        case WINNER -> "Winner (1st Place)";
                        case RUNNER_UP -> "Runner-Up (" + rank + (rank == 2 ? "nd" : "rd") + " Place)";
                        case PARTICIPATION -> "Participation";
                    };

                    notifications.createNotification(member.getUserId(), NotificationCategory.HACKATHON,
                            "Certificate Available",
                            "Your " + certTitle + " certificate for \"" + hackathon.getTitle() + "\" is ready to view and download!",
                            NotificationPriority.IMPORTANT,
                            "/hackathons/" + hackathonId + "/certificate");
                }
            }
        }

        // Notify all hackathon team members that results are published
        for (HackathonTeamMember member : allMembers) {
            notifications.createNotification(member.getUserId(), NotificationCategory.HACKATHON,
                    "Results Published",
                    "The final results for \"" + hackathon.getTitle() + "\" are now published! Check the leaderboard to see rankings.",
                    NotificationPriority.IMPORTANT,
                    "/hackathons/" + hackathonId + "/leaderboard");
        }

        // Notify judges that results are published
        for (HackathonJudge judge : judges.findByHackathonIdOrderByIdAsc(hackathonId)) {
            notifications.createNotification(judge.getUserId(), NotificationCategory.HACKATHON,
                    "Results Published",
                    "Final results for \"" + hackathon.getTitle() + "\" are now published on the leaderboard.",
                    NotificationPriority.NORMAL,
                    "/hackathons/" + hackathonId + "/leaderboard");
        }

        audit.log(adminEmail, "HACKATHON_PUBLISH_RESULTS", null, "Published the results of \"" + hackathon.getTitle() + "\"");
    }

    // ---- Results (everyone) --------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<RankedView> results(Long hackathonId) {
        Hackathon hackathon = hosted(hackathonId);
        if (hackathon.getResultsPublishedAt() == null) {
            throw new HackathonStateException("The results haven't been published yet.");
        }
        return ranking(hackathonId);
    }

    // ---- Problem Statements (learners & admins) --------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ProblemStatementView> problemStatements(Long hackathonId) {
        hosted(hackathonId);
        return problemStatements.findByHackathonIdOrderByIdAsc(hackathonId).stream()
                .map(ProblemStatementView::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProblemStatementView problemStatement(Long hackathonId, Long problemId) {
        hosted(hackathonId);
        return problemStatements.findByIdAndHackathonId(problemId, hackathonId)
                .map(ProblemStatementView::from)
                .orElseThrow(HackathonNotFoundException::new);
    }

    @Transactional
    public ProblemStatementView addProblemStatement(Long hackathonId, ProblemStatementInput in, String adminEmail) {
        Hackathon hackathon = hosted(hackathonId);
        if (hackathon.getResultsPublishedAt() != null) {
            throw new HackathonStateException("Results are published, so problem statements can't be added.");
        }
        String title = text(in.title(), 3, 200, "The problem title");
        String description = text(in.description(), 10, 5000, "The description");
        String track = in.track() == null || in.track().isBlank() ? null : text(in.track(), 1, 100, "The track");
        String requirements = in.requirements() == null || in.requirements().isBlank() ? null : text(in.requirements(), 1, 5000, "The requirements");
        String evaluationCriteria = in.evaluationCriteria() == null || in.evaluationCriteria().isBlank() ? null : text(in.evaluationCriteria(), 1, 5000, "The evaluation criteria");
        String resourcesUrl = in.resourcesUrl() == null || in.resourcesUrl().isBlank() ? null : httpsUrl(in.resourcesUrl(), 1000, "The resources link", false);

        HackathonProblemStatement created = problemStatements.save(
                new HackathonProblemStatement(hackathonId, title, description, track, requirements, evaluationCriteria, resourcesUrl)
        );
        audit.log(adminEmail, "HACKATHON_PROBLEM_ADD", null, "Added problem statement \"" + title + "\" to \"" + hackathon.getTitle() + "\"");
        return ProblemStatementView.from(created);
    }

    @Transactional
    public ProblemStatementView updateProblemStatement(Long hackathonId, Long problemId, ProblemStatementInput in, String adminEmail) {
        Hackathon hackathon = hosted(hackathonId);
        if (hackathon.getResultsPublishedAt() != null) {
            throw new HackathonStateException("Results are published, so problem statements can't be modified.");
        }
        HackathonProblemStatement statement = problemStatements.findByIdAndHackathonId(problemId, hackathonId)
                .orElseThrow(HackathonNotFoundException::new);
        String title = text(in.title(), 3, 200, "The problem title");
        String description = text(in.description(), 10, 5000, "The description");
        String track = in.track() == null || in.track().isBlank() ? null : text(in.track(), 1, 100, "The track");
        String requirements = in.requirements() == null || in.requirements().isBlank() ? null : text(in.requirements(), 1, 5000, "The requirements");
        String evaluationCriteria = in.evaluationCriteria() == null || in.evaluationCriteria().isBlank() ? null : text(in.evaluationCriteria(), 1, 5000, "The evaluation criteria");
        String resourcesUrl = in.resourcesUrl() == null || in.resourcesUrl().isBlank() ? null : httpsUrl(in.resourcesUrl(), 1000, "The resources link", false);

        statement.update(title, description, track, requirements, evaluationCriteria, resourcesUrl);
        HackathonProblemStatement saved = problemStatements.save(statement);
        audit.log(adminEmail, "HACKATHON_PROBLEM_UPDATE", null, "Updated problem statement \"" + title + "\" in \"" + hackathon.getTitle() + "\"");
        return ProblemStatementView.from(saved);
    }

    @Transactional
    public void deleteProblemStatement(Long hackathonId, Long problemId, String adminEmail) {
        Hackathon hackathon = hosted(hackathonId);
        if (hackathon.getResultsPublishedAt() != null) {
            throw new HackathonStateException("Results are published, so problem statements can't be deleted.");
        }
        HackathonProblemStatement statement = problemStatements.findByIdAndHackathonId(problemId, hackathonId)
                .orElseThrow(HackathonNotFoundException::new);
        problemStatements.delete(statement);
        audit.log(adminEmail, "HACKATHON_PROBLEM_DELETE", null, "Deleted problem statement \"" + statement.getTitle() + "\" from \"" + hackathon.getTitle() + "\"");
    }

    @Transactional
    public TeamView selectProblemStatement(Long hackathonId, Long userId, Long problemStatementId) {
        Hackathon hackathon = hosted(hackathonId);
        HackathonTeam found = teams.findOfMember(hackathonId, userId).orElseThrow(HackathonNotFoundException::new);
        HackathonTeam team = teams.findForUpdate(found.getId()).orElseThrow(HackathonNotFoundException::new);
        if (!team.getLeaderId().equals(userId)) {
            throw new AccessDeniedException("Only the team leader can select the problem statement.");
        }
        if (hackathon.phase(Instant.now()) == HackathonPhase.JUDGING || hackathon.phase(Instant.now()) == HackathonPhase.RESULTS) {
            throw new HackathonStateException("Problem statement cannot be changed after submissions close.");
        }
        if (problemStatementId != null) {
            problemStatements.findByIdAndHackathonId(problemStatementId, hackathonId)
                    .orElseThrow(() -> new InvalidHackathonException("That problem statement does not exist in this hackathon."));
        }
        team.selectProblemStatement(problemStatementId);
        teams.save(team);

        HackathonProblemStatement statement = problemStatementId != null ? problemStatements.findById(problemStatementId).orElse(null) : null;
        String psTitle = statement != null ? statement.getTitle() : "None";
        for (HackathonTeamMember m : members.findByTeamIdOrderByJoinedAtAscIdAsc(team.getId())) {
            if (!m.getUserId().equals(userId)) {
                notifications.createNotification(m.getUserId(), NotificationCategory.HACKATHON,
                        "Problem Statement Selected",
                        "Team leader updated the selected problem statement to: \"" + psTitle + "\".",
                        NotificationPriority.NORMAL,
                        "/hackathons/" + hackathonId + "/workspace");
            }
        }

        return view(team, userId);
    }

    // ---- Building views ----------------------------------------------------------------------------------------------------

    private TeamView view(HackathonTeam team, Long viewerId) {
        List<HackathonTeamMember> crew = members.findByTeamIdOrderByJoinedAtAscIdAsc(team.getId());
        Map<Long, User> people = usersById(crew.stream().map(HackathonTeamMember::getUserId).toList());
        List<MemberView> memberViews = crew.stream().map(m -> {
            User u = people.get(m.getUserId());
            return new MemberView(m.getUserId(), u == null ? "Former member" : u.getDisplayName(), u == null ? null : u.getPictureUrl(),
                    m.getUserId().equals(team.getLeaderId()));
        }).toList();
        boolean isMember = crew.stream().anyMatch(m -> m.getUserId().equals(viewerId));
        SubmissionView submission = submissions.findByTeamId(team.getId()).map(s -> submissionView(s, team.getHackathonId())).orElse(null);
        // Only people on the team can see the invite code.
        return new TeamView(team.getId(), team.getHackathonId(), team.getName(), team.getTrack(), team.getProblemStatementId(), isMember ? team.getInviteCode() : null,
                team.getLeaderId().equals(viewerId), memberViews, submission);
    }

    private SubmissionView submissionView(HackathonSubmission s, Long hackathonId) {
        if (s == null) {
            return null;
        }
        Instant now = Instant.now();
        Hackathon h = hackathons.findById(hackathonId).orElse(null);
        String effectiveStatus = s.getStatus() != null ? s.getStatus() : "SUBMITTED";
        if (h != null) {
            if ((h.getEventEndDate() != null && !now.isBefore(h.getEventEndDate()))
                    || h.phase(now) == HackathonPhase.JUDGING || h.phase(now) == HackathonPhase.RESULTS) {
                effectiveStatus = "LOCKED";
            }
        }
        return new SubmissionView(s.getId(), s.getTitle(), s.getRepoUrl(), s.getDemoUrl(), s.getDescription(),
                effectiveStatus, s.getSubmittedAt(), s.getUpdatedAt());
    }

    private List<ProjectView> projects(Long hackathonId, Long judgeId) {
        List<HackathonSubmission> all = submissions.findByHackathonIdOrderByIdAsc(hackathonId).stream()
                .filter(s -> !"DRAFT".equalsIgnoreCase(s.getStatus()))
                .toList();
        if (all.isEmpty()) {
            return List.of();
        }
        List<Long> ids = all.stream().map(HackathonSubmission::getId).toList();
        Map<Long, HackathonTeam> teamById = new HashMap<>();
        teams.findAllById(all.stream().map(HackathonSubmission::getTeamId).toList()).forEach(t -> teamById.put(t.getId(), t));
        Map<Long, ProblemStatementView> problemById = problemStatements.findByHackathonIdOrderByIdAsc(hackathonId).stream()
                .collect(Collectors.toMap(HackathonProblemStatement::getId, ProblemStatementView::from));
        List<Long> currentJudges = currentJudgeIds(hackathonId);
        Map<Long, Integer> counts = new HashMap<>();
        for (HackathonScore s : scores.findBySubmissionIdIn(ids)) {
            if (currentJudges.contains(s.getJudgeId())) {
                counts.merge(s.getSubmissionId(), 1, Integer::sum);
            }
        }
        Map<Long, HackathonScore> mine = new HashMap<>();
        if (judgeId != null) {
            scores.ofJudge(judgeId, ids).forEach(s -> mine.put(s.getSubmissionId(), s));
        }
        return all.stream().map(s -> {
            HackathonTeam team = teamById.get(s.getTeamId());
            HackathonScore own = mine.get(s.getId());
            ProblemStatementView statement = (team != null && team.getProblemStatementId() != null)
                    ? problemById.get(team.getProblemStatementId())
                    : null;
            return new ProjectView(s.getId(), team == null ? "" : team.getName(), team == null ? null : team.getTrack(), s.getTitle(),
                    s.getRepoUrl(), s.getDemoUrl(), s.getDescription(), statement,
                    own == null ? null : own.getInnovation(), own == null ? null : own.getExecution(),
                    own == null ? null : own.getImpact(), own == null ? null : own.getPresentation(),
                    own == null ? null : own.getComment(), counts.getOrDefault(s.getId(), 0));
        }).toList();
    }

    private List<Long> currentJudgeIds(Long hackathonId) {
        return judges.findByHackathonIdOrderByIdAsc(hackathonId).stream().map(HackathonJudge::getUserId).toList();
    }

    /** Projects ranked by the mean of the current judges' scores. Equal scores share a rank. */
    private List<RankedView> ranking(Long hackathonId) {
        List<HackathonSubmission> all = submissions.findByHackathonIdOrderByIdAsc(hackathonId).stream()
                .filter(s -> !"DRAFT".equalsIgnoreCase(s.getStatus()))
                .toList();
        if (all.isEmpty()) {
            return List.of();
        }
        List<Long> judgeIds = currentJudgeIds(hackathonId);
        Map<Long, List<HackathonScore>> scoresBySubmission = new HashMap<>();
        for (HackathonScore s : scores.findBySubmissionIdIn(all.stream().map(HackathonSubmission::getId).toList())) {
            if (judgeIds.contains(s.getJudgeId())) {
                scoresBySubmission.computeIfAbsent(s.getSubmissionId(), k -> new ArrayList<>()).add(s);
            }
        }
        Map<Long, HackathonTeam> teamById = new HashMap<>();
        teams.findAllById(all.stream().map(HackathonSubmission::getTeamId).toList()).forEach(t -> teamById.put(t.getId(), t));
        Map<Long, List<HackathonTeamMember>> crewByTeam = new HashMap<>();
        members.findByTeamIdIn(teamById.keySet()).forEach(m -> crewByTeam.computeIfAbsent(m.getTeamId(), k -> new ArrayList<>()).add(m));
        Map<Long, User> people = usersById(crewByTeam.values().stream().flatMap(Collection::stream).map(HackathonTeamMember::getUserId).toList());

        Map<Long, HackathonProblemStatement> problemById = problemStatements.findByHackathonIdOrderByIdAsc(hackathonId).stream()
                .collect(Collectors.toMap(HackathonProblemStatement::getId, p -> p));

        record Row(HackathonSubmission submission, double overall, double innovation, double execution, double impact, double presentation, int count) {
        }
        List<Row> rows = new ArrayList<>();
        for (HackathonSubmission s : all) {
            List<HackathonScore> given = scoresBySubmission.getOrDefault(s.getId(), List.of());
            rows.add(new Row(s, mean(given, HackathonScore::average), mean(given, x -> x.getInnovation()), mean(given, x -> x.getExecution()),
                    mean(given, x -> x.getImpact()), mean(given, x -> x.getPresentation()), given.size()));
        }
        rows.sort(Comparator.comparingDouble(Row::overall).reversed().thenComparing(r -> r.submission().getId()));

        List<RankedView> ranked = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int rank = i > 0 && Math.abs(rows.get(i - 1).overall() - row.overall()) < 1e-9 ? ranked.get(i - 1).rank() : i + 1;
            HackathonTeam team = teamById.get(row.submission().getTeamId());
            List<String> names = crewByTeam.getOrDefault(row.submission().getTeamId(), List.of()).stream()
                    .map(m -> people.get(m.getUserId())).filter(u -> u != null).map(User::getDisplayName).toList();
            String track = team != null ? team.getTrack() : null;
            String problemTitle = null;
            if (team != null && team.getProblemStatementId() != null) {
                HackathonProblemStatement ps = problemById.get(team.getProblemStatementId());
                if (ps != null) {
                    problemTitle = ps.getTitle();
                    if (track == null || track.isBlank()) {
                        track = ps.getTrack();
                    }
                }
            }
            ranked.add(new RankedView(rank, team == null ? "" : team.getName(), track, problemTitle, names, row.submission().getTitle(), row.submission().getRepoUrl(),
                    row.submission().getDemoUrl(), round(row.overall()), round(row.innovation()), round(row.execution()), round(row.impact()),
                    round(row.presentation()), row.count()));
        }
        return ranked;
    }

    // ---- Certificates -----------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Optional<HackathonCertificateView> myCertificate(Long hackathonId, Long userId) {
        hosted(hackathonId);
        return certificates.findByHackathonIdAndUserId(hackathonId, userId)
                .map(HackathonCertificateView::from);
    }

    @Transactional(readOnly = true)
    public byte[] certificatePdf(Long hackathonId, Long userId) {
        hosted(hackathonId);
        HackathonCertificate cert = certificates.findByHackathonIdAndUserId(hackathonId, userId)
                .orElseThrow(HackathonNotFoundException::new);
        return certPdfRenderer.render(cert);
    }

    @Transactional(readOnly = true)
    public Optional<HackathonCertificateView> certificateByCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return certificates.findByCode(code.trim().toUpperCase(Locale.ROOT))
                .map(HackathonCertificateView::from);
    }

    @Transactional(readOnly = true)
    public byte[] certificatePdfByCode(String code) {
        if (code == null || code.isBlank()) {
            throw new HackathonNotFoundException();
        }
        HackathonCertificate cert = certificates.findByCode(code.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(HackathonNotFoundException::new);
        return certPdfRenderer.render(cert);
    }

    private static double mean(List<HackathonScore> given, java.util.function.ToDoubleFunction<HackathonScore> part) {
        return given.isEmpty() ? 0 : given.stream().mapToDouble(part).average().orElse(0);
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private Map<Long, User> usersById(Collection<Long> ids) {
        Map<Long, User> map = new HashMap<>();
        if (!ids.isEmpty()) {
            users.findAllById(ids).forEach(u -> map.put(u.getId(), u));
        }
        return map;
    }

    // ---- Guards and validation ---------------------------------------------------------------------------------------------

    /** A hosted event, or "not found": external listings have no teams. */
    private Hackathon hosted(Long id) {
        Hackathon hackathon = hackathons.findById(id).orElseThrow(HackathonNotFoundException::new);
        if (!hackathon.isHosted()) {
            throw new HackathonNotFoundException();
        }
        return hackathon;
    }

    private void requireRegistrationOpen(Hackathon hackathon) {
        if (!hackathon.registrationOpen(Instant.now())) {
            throw new HackathonStateException("Registration for this hackathon has closed.");
        }
    }

    private void requireNotJudge(Long hackathonId, Long userId) {
        if (judges.existsByHackathonIdAndUserId(hackathonId, userId)) {
            throw new HackathonStateException("You're a judge of this hackathon, so you can't enter it.");
        }
    }

    private void requireJudge(Long hackathonId, Long userId) {
        if (!judges.existsByHackathonIdAndUserId(hackathonId, userId)) {
            throw new AccessDeniedException("You aren't a judge of this hackathon.");
        }
    }

    private static String trackFor(Hackathon hackathon, String rawTrack) {
        if (hackathon.getTracks() == null) {
            return null;
        }
        String wanted = rawTrack == null ? "" : rawTrack.strip();
        return java.util.Arrays.stream(hackathon.getTracks().split(",\\s*")).filter(t -> t.equalsIgnoreCase(wanted)).findFirst()
                .orElseThrow(() -> new InvalidHackathonException("Choose one of the tracks: " + hackathon.getTracks() + "."));
    }

    private static String newInviteCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return code.toString();
    }

    private static String text(String value, int min, int max, String label) {
        String text = value == null ? "" : value.strip();
        if (text.length() < min) {
            throw new InvalidHackathonException(label + " needs at least " + min + " characters.");
        }
        if (text.length() > max) {
            throw new InvalidHackathonException(label + " can be at most " + max + " characters.");
        }
        return text;
    }

    private static String httpsUrl(String value, int max, String label, boolean required) {
        String text = text(value, required ? 1 : 0, max, label);
        try {
            URI uri = new URI(text);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || text.chars().anyMatch(Character::isWhitespace)) {
                throw new InvalidHackathonException(label + " must be a full https:// link.");
            }
        } catch (URISyntaxException e) {
            throw new InvalidHackathonException(label + " must be a full https:// link.");
        }
        return text;
    }
}
