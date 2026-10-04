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
    private static final int MAX_PROBLEM_STATEMENTS = 20;
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

        String joinerName = users.findById(userId).map(User::getDisplayName).orElse("A new member");
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
        String leaverName = users.findById(userId).map(User::getDisplayName).orElse("A team member");

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
        if (hackathon.phase(Instant.now()) != HackathonPhase.BUILDING) {
            throw new HackathonStateException("Submissions are locked. The deadline has passed or building is not active.");
        }
        HackathonTeam team = teams.findOfMember(hackathonId, userId)
                .orElseThrow(() -> new HackathonStateException("Join or create a team before submitting."));
        if (members.countByTeamId(team.getId()) < hackathon.getMinTeamSize()) {
            throw new HackathonStateException("Your team needs at least " + hackathon.getMinTeamSize() + " members to submit.");
        }
        boolean draft = Boolean.TRUE.equals(in.draft());
        Optional<HackathonSubmission> existing = submissions.findByTeamId(team.getId());
        if (draft && existing.map(HackathonSubmission::isSubmitted).orElse(false)) {
            throw new HackathonStateException("This project is already submitted. Edit it and submit again instead.");
        }

        // A draft keeps whatever the team has so far (only a title is filled in for them); a submission needs everything.
        String title = draft && (in.title() == null || in.title().isBlank()) ? "Untitled draft" : text(in.title(), draft ? 1 : 3, 150, "The project title");
        String repo = draft && isBlank(in.repoUrl()) ? null : httpsUrl(in.repoUrl(), 500, "The code repository link", true);
        String demo = isBlank(in.demoUrl()) ? null : httpsUrl(in.demoUrl(), 500, "The demo link", true);
        String description = draft && isBlank(in.description()) ? null : text(in.description(), draft ? 1 : 30, 3000, "The description");
        SubmissionStatus status = draft ? SubmissionStatus.DRAFT : SubmissionStatus.SUBMITTED;

        HackathonSubmission saved = submissions.save(existing.map(current -> {
            current.update(title, repo, demo, description, status);
            return current;
        }).orElseGet(() -> new HackathonSubmission(team.getId(), hackathonId, title, repo, demo, description, status)));

        // Teammates hear about a real submission, not about every saved draft.
        if (!draft) {
            String submitterName = users.findById(userId).map(User::getDisplayName).orElse("A team member");
            for (HackathonTeamMember m : members.findByTeamIdOrderByJoinedAtAscIdAsc(team.getId())) {
                if (!m.getUserId().equals(userId)) {
                    notifications.createNotification(m.getUserId(), NotificationCategory.HACKATHON, "Project Submitted",
                            submitterName + " submitted the project \"" + title + "\" for team \"" + team.getName() + "\".",
                            NotificationPriority.NORMAL, "/hackathons/" + hackathonId + "/workspace");
                }
            }
        }
        return submissionView(saved, hackathon);
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
        if (!submission.isSubmitted()) {
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
                "/judging");
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
            if (submission.isSubmitted()) {
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
            Integer rank = rankOfTeam.get(team.getName());
            if (rank == null) {
                continue; // not ranked: nothing to recognise
            }
            boolean winner = rank <= WINNERS;
            HackathonCertificateType certType = certificateTypeFor(rank);

            for (HackathonTeamMember member : crew) {
                gamification.grantBadge(member.getUserId(), "HACKATHON_BUILDER");
                if (winner) {
                    gamification.grantBadge(member.getUserId(), "HACKATHON_WINNER");
                }
                if (certificates.existsByHackathonIdAndUserId(hackathonId, member.getUserId())) {
                    continue;
                }
                User recipient = userCache.get(member.getUserId());
                certificates.save(new HackathonCertificate(hackathonId, member.getUserId(), newCertificateCode(), hackathon.getTitle(),
                        recipient == null ? "Participant" : recipient.getDisplayName(), team.getName(), certType, rank));
                notifications.createNotification(member.getUserId(), NotificationCategory.HACKATHON, "Certificate Available",
                        "Your " + awardName(certType, rank) + " certificate for \"" + hackathon.getTitle() + "\" is ready to view and download.",
                        NotificationPriority.IMPORTANT, "/hackathons/" + hackathonId + "/certificate");
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
        requireNoResults(hackathon, "added");
        if (problemStatements.countByHackathonId(hackathonId) >= MAX_PROBLEM_STATEMENTS) {
            throw new HackathonStateException("A hackathon can have at most " + MAX_PROBLEM_STATEMENTS + " problem statements.");
        }
        HackathonProblemStatement created = problemStatements.save(problemStatement(hackathonId, in));
        audit.log(adminEmail, "HACKATHON_PROBLEM_ADD", null, "Added problem statement \"" + created.getTitle() + "\" to \"" + hackathon.getTitle() + "\"");
        return ProblemStatementView.from(created);
    }

    @Transactional
    public ProblemStatementView updateProblemStatement(Long hackathonId, Long problemId, ProblemStatementInput in, String adminEmail) {
        Hackathon hackathon = hosted(hackathonId);
        requireNoResults(hackathon, "modified");
        HackathonProblemStatement statement = problemStatements.findByIdAndHackathonId(problemId, hackathonId)
                .orElseThrow(HackathonNotFoundException::new);
        HackathonProblemStatement edited = problemStatement(hackathonId, in);
        statement.update(edited.getTitle(), edited.getDescription(), edited.getTrack(), edited.getRequirements(),
                edited.getEvaluationCriteria(), edited.getResourcesUrl());
        audit.log(adminEmail, "HACKATHON_PROBLEM_UPDATE", null, "Updated problem statement \"" + statement.getTitle() + "\" in \"" + hackathon.getTitle() + "\"");
        return ProblemStatementView.from(problemStatements.save(statement));
    }

    @Transactional
    public void deleteProblemStatement(Long hackathonId, Long problemId, String adminEmail) {
        Hackathon hackathon = hosted(hackathonId);
        requireNoResults(hackathon, "deleted");
        HackathonProblemStatement statement = problemStatements.findByIdAndHackathonId(problemId, hackathonId)
                .orElseThrow(HackathonNotFoundException::new);
        problemStatements.delete(statement);
        audit.log(adminEmail, "HACKATHON_PROBLEM_DELETE", null, "Deleted problem statement \"" + statement.getTitle() + "\" from \"" + hackathon.getTitle() + "\"");
    }

    /** The team leader picks which problem the team works on, or clears it with null. Open until submissions close. */
    @Transactional
    public TeamView selectProblemStatement(Long hackathonId, Long userId, Long problemStatementId) {
        Hackathon hackathon = hosted(hackathonId);
        HackathonTeam found = teams.findOfMember(hackathonId, userId).orElseThrow(HackathonNotFoundException::new);
        HackathonTeam team = teams.findForUpdate(found.getId()).orElseThrow(HackathonNotFoundException::new);
        if (!team.getLeaderId().equals(userId)) {
            throw new AccessDeniedException("Only the team leader can select the problem statement.");
        }
        HackathonPhase phase = hackathon.phase(Instant.now());
        if (phase == HackathonPhase.JUDGING || phase == HackathonPhase.RESULTS) {
            throw new HackathonStateException("Problem statement cannot be changed after submissions close.");
        }
        String chosenTitle = "None";
        if (problemStatementId != null) {
            chosenTitle = problemStatements.findByIdAndHackathonId(problemStatementId, hackathonId)
                    .orElseThrow(() -> new InvalidHackathonException("That problem statement does not exist in this hackathon.")).getTitle();
        }
        team.selectProblemStatement(problemStatementId);
        teams.save(team);

        for (HackathonTeamMember m : members.findByTeamIdOrderByJoinedAtAscIdAsc(team.getId())) {
            if (!m.getUserId().equals(userId)) {
                notifications.createNotification(m.getUserId(), NotificationCategory.HACKATHON, "Problem Statement Selected",
                        "Your team leader chose the problem statement: \"" + chosenTitle + "\".",
                        NotificationPriority.NORMAL, "/hackathons/" + hackathonId + "/workspace");
            }
        }
        return view(team, userId);
    }

    private static void requireNoResults(Hackathon hackathon, String verb) {
        if (hackathon.getResultsPublishedAt() != null) {
            throw new HackathonStateException("Results are published, so problem statements can't be " + verb + ".");
        }
    }

    /** Validates the admin's input and builds the statement; nothing is saved here. */
    private static HackathonProblemStatement problemStatement(Long hackathonId, ProblemStatementInput in) {
        return new HackathonProblemStatement(hackathonId,
                text(in.title(), 3, 200, "The problem title"),
                text(in.description(), 10, 5000, "The description"),
                isBlank(in.track()) ? null : text(in.track(), 1, 100, "The track"),
                isBlank(in.requirements()) ? null : text(in.requirements(), 1, 5000, "The requirements"),
                isBlank(in.evaluationCriteria()) ? null : text(in.evaluationCriteria(), 1, 5000, "The evaluation criteria"),
                isBlank(in.resourcesUrl()) ? null : httpsUrl(in.resourcesUrl(), 1000, "The resources link", false));
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
        Hackathon hackathon = hackathons.findById(team.getHackathonId()).orElseThrow(HackathonNotFoundException::new);
        SubmissionView submission = submissions.findByTeamId(team.getId()).map(s -> submissionView(s, hackathon)).orElse(null);
        // Only people on the team can see the invite code.
        return new TeamView(team.getId(), team.getHackathonId(), team.getName(), team.getTrack(), team.getProblemStatementId(), isMember ? team.getInviteCode() : null,
                team.getLeaderId().equals(viewerId), memberViews, submission);
    }

    /** A submitted project reads as LOCKED once submissions have closed; a draft stays a draft, because it was never turned in. */
    private static SubmissionView submissionView(HackathonSubmission s, Hackathon hackathon) {
        HackathonPhase phase = hackathon.phase(Instant.now());
        boolean closed = phase == HackathonPhase.JUDGING || phase == HackathonPhase.RESULTS;
        String status = s.isSubmitted() && closed ? "LOCKED" : s.getStatus().name();
        return new SubmissionView(s.getId(), s.getTitle(), s.getRepoUrl(), s.getDemoUrl(), s.getDescription(),
                status, s.getSubmittedAt(), s.getUpdatedAt());
    }

    private List<ProjectView> projects(Long hackathonId, Long judgeId) {
        List<HackathonSubmission> all = submissions.findByHackathonIdOrderByIdAsc(hackathonId).stream()
                .filter(HackathonSubmission::isSubmitted)
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
                .filter(HackathonSubmission::isSubmitted)
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

    private static HackathonCertificateType certificateTypeFor(int rank) {
        if (rank == 1) {
            return HackathonCertificateType.WINNER;
        }
        return rank <= WINNERS ? HackathonCertificateType.RUNNER_UP : HackathonCertificateType.PARTICIPATION;
    }

    private static String awardName(HackathonCertificateType type, int rank) {
        return switch (type) {
            case WINNER -> "Winner";
            case RUNNER_UP -> "Runner-Up (rank " + rank + ")";
            case PARTICIPATION -> "Participation";
        };
    }

    /** HACK- plus twelve random hex digits: unguessable, and short enough to read off a printout. */
    private static String newCertificateCode() {
        return "HACK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
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
