package com.secureportal.interview;

import com.secureportal.course.Course;
import com.secureportal.course.CourseRepository;
import com.secureportal.course.CourseStatus;
import com.secureportal.gamification.GamificationService;
import com.secureportal.gamification.PointAction;
import com.secureportal.user.User;
import com.secureportal.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * AI mock interviews. The rules that keep them honest live here: an interview belongs to the learner who started it (and looks
 * like it doesn't exist to anyone else), a question is answered once, an interview is completed once and pays its XP once, and
 * the AI is called outside any database transaction so a slow reply never holds a connection.
 */
@Service
public class MockInterviewService {

    static final int MAX_PER_DAY = 15;
    static final int MIN_ANSWER = 10;
    static final int MAX_ANSWER = 4000;
    static final int MAX_SKILLS = 12;
    static final int MAX_SKILL_LENGTH = 40;
    static final int MAX_JOB_DESCRIPTION = 4000;
    static final int MAX_FOLLOW_UPS = 2;
    /** A follow-up is only worth asking when the answer was weak. */
    static final int FOLLOW_UP_BELOW = 7;
    static final int HISTORY_LIMIT = 50;
    static final int RECENT_FOR_ADMIN = 15;
    static final int DEFAULT_QUESTIONS = 5;
    static final int MIN_QUESTIONS = 3;
    static final int MAX_QUESTIONS = 10;

    /** A role or skill is a short label (it goes into the AI prompt), not free-form text. */
    private static final Pattern ROLE_LABEL = Pattern.compile("[\\p{L}\\p{N} &/+.,'()-]{2,100}");
    private static final Pattern SKILL_LABEL = Pattern.compile("[\\p{L}\\p{N} &/+#.'()-]{1,40}");

    private final MockInterviewSessionRepository sessions;
    private final MockInterviewQuestionRepository questions;
    private final UserRepository users;
    private final CourseRepository courses;
    private final ResumeService resumes;
    private final VoiceService voice;
    private final InterviewAi ai;
    private final GamificationService gamification;
    private final TransactionTemplate tx;

    public MockInterviewService(MockInterviewSessionRepository sessions, MockInterviewQuestionRepository questions,
                                UserRepository users, CourseRepository courses, ResumeService resumes, VoiceService voice, InterviewAi ai,
                                GamificationService gamification,
                                PlatformTransactionManager transactionManager) {
        this.sessions = sessions;
        this.questions = questions;
        this.users = users;
        this.courses = courses;
        this.resumes = resumes;
        this.voice = voice;
        this.ai = ai;
        this.gamification = gamification;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public record Detail(MockInterviewSession session, List<MockInterviewQuestion> questions) {
    }

    public record Completion(MockInterviewSession session, List<MockInterviewQuestion> questions, int xpEarned) {
    }

    public record AnswerResult(MockInterviewQuestion question, MockInterviewSession session, MockInterviewQuestion followUp) {
    }

    /**
     * What the learner asks for. A technical interview needs skills, a job description, a resume or a course to build on; an HR interview
     * only needs the role (or a resume to read it from).
     */
    public record StartRequest(String track, String difficulty, String targetRole, List<String> skills, String jobDescription,
                               String courseId, boolean useResume, String interviewType, Integer questionCount, String interviewer) {
    }

    public record Quota(long used, int limit, int voiceUsed, int voiceLimit) {
    }

    // ---- Starting -----------------------------------------------------------------------------------------------------

    public MockInterviewSession start(Long userId, StartRequest request) {
        InterviewTrack track = request.track() == null || request.track().isBlank() ? InterviewTrack.STUDENT
                : InterviewTrack.parse(request.track()).orElseThrow(() -> new InvalidInterviewException("Choose Student or Working professional."));
        InterviewDifficulty difficulty = request.difficulty() == null || request.difficulty().isBlank() ? InterviewDifficulty.MEDIUM
                : InterviewDifficulty.parse(request.difficulty()).orElseThrow(() -> new InvalidInterviewException("Choose Easy, Medium or Hard."));
        InterviewType type = request.interviewType() == null || request.interviewType().isBlank() ? InterviewType.TECHNICAL
                : InterviewType.parse(request.interviewType()).orElseThrow(() -> new InvalidInterviewException("Choose a Technical or an HR interview."));
        int count = request.questionCount() == null ? DEFAULT_QUESTIONS : request.questionCount();
        if (count < MIN_QUESTIONS || count > MAX_QUESTIONS) {
            throw new InvalidInterviewException("Choose between " + MIN_QUESTIONS + " and " + MAX_QUESTIONS + " questions.");
        }
        Interviewer interviewer = request.interviewer() == null || request.interviewer().isBlank() ? Interviewer.defaultFor(type)
                : Interviewer.parse(request.interviewer()).orElseThrow(() -> new InvalidInterviewException("Choose one of the interviewers."));
        MockInterviewSession.Goal goal = goalFrom(request, type);
        return begin(userId, track, type, interviewer, difficulty, count, goal, resumeTextFor(userId, goal));
    }

    /** Practise again with the same setup as an earlier interview of the learner's own. */
    public MockInterviewSession retry(Long userId, Long sessionId) {
        MockInterviewSession earlier = ownedSession(sessionId, userId);
        return begin(userId, InterviewTrack.valueOf(earlier.getTrack()), InterviewType.valueOf(earlier.getInterviewType()),
                earlier.getInterviewer(), InterviewDifficulty.valueOf(earlier.getDifficulty()), earlier.getPlannedQuestions(), earlier.goal(),
                resumeTextFor(userId, earlier.goal()));
    }

    private MockInterviewSession begin(Long userId, InterviewTrack track, InterviewType type, Interviewer interviewer,
                                       InterviewDifficulty difficulty, int count, MockInterviewSession.Goal goal, String resumeText) {
        if (sessions.countByUserIdAndCreatedAtAfter(userId, Instant.now().minus(1, ChronoUnit.DAYS)) >= MAX_PER_DAY) {
            throw new InterviewLimitException(MAX_PER_DAY);
        }

        // The AI is asked before anything is written, so a failure leaves nothing half-created.
        List<InterviewQuestionBank.Item> items = ai.questionsFor(track, type, interviewer, difficulty, count, goal, resumeText);

        return tx.execute(status -> {
            sessions.abandonOpen(userId);
            MockInterviewSession session = sessions.save(new MockInterviewSession(userId, track, type, interviewer, difficulty, goal,
                    items.size()));
            for (int i = 0; i < items.size(); i++) {
                questions.save(new MockInterviewQuestion(session.getId(), i, items.get(i).text(), items.get(i).category()));
            }
            return session;
        });
    }

    /** The learner's current resume text when the interview is built from it: it is read fresh, never copied into the interview. */
    private String resumeTextFor(Long userId, MockInterviewSession.Goal goal) {
        if (goal.source() != InterviewSource.RESUME) {
            return null;
        }
        return resumes.find(userId).map(InterviewResume::getContentText)
                .orElseThrow(() -> new InvalidInterviewException("Upload your resume first. It may have been deleted or expired."));
    }

    /** Turns what the learner typed into a goal, checking every piece: it all ends up in an AI prompt. */
    private MockInterviewSession.Goal goalFrom(StartRequest request, InterviewType type) {
        String courseId = request.courseId() == null || request.courseId().isBlank() ? null : request.courseId().strip();
        if (courseId != null) {
            Course course = courses.findById(parseCourseId(courseId))
                    .filter(c -> c.getStatus() == CourseStatus.PUBLISHED).orElseThrow(InterviewNotFoundException::new);
            String role = course.getCategory() == null || course.getCategory().isBlank() ? course.getTitle() : course.getCategory();
            return new MockInterviewSession.Goal(InterviewSource.COURSE, clipLabel(role), clipSkills(course.getTitle()), null,
                    course.getId().toString());
        }

        String role = request.targetRole() == null ? "" : request.targetRole().strip();
        if (!role.isEmpty() && !ROLE_LABEL.matcher(role).matches()) {
            throw new InvalidInterviewException("Tell us the role you're preparing for, for example \"Backend developer\".");
        }
        if (role.isEmpty() && !request.useResume()) {
            throw new InvalidInterviewException("Tell us the role you're preparing for, for example \"Backend developer\".");
        }
        List<String> skills = new ArrayList<>();
        if (request.skills() != null) {
            for (String raw : request.skills()) {
                String skill = raw == null ? "" : raw.strip();
                if (skill.isEmpty()) continue;
                if (!SKILL_LABEL.matcher(skill).matches()) {
                    throw new InvalidInterviewException("Each skill must be a short name of at most " + MAX_SKILL_LENGTH + " characters.");
                }
                if (skills.stream().noneMatch(skill::equalsIgnoreCase)) {
                    skills.add(skill);
                }
            }
        }
        if (skills.size() > MAX_SKILLS) {
            throw new InvalidInterviewException("Add at most " + MAX_SKILLS + " skills.");
        }
        String jobDescription = request.jobDescription() == null || request.jobDescription().isBlank() ? null : request.jobDescription().strip();
        if (jobDescription != null && jobDescription.length() > MAX_JOB_DESCRIPTION) {
            throw new InvalidInterviewException("Keep the job description under " + MAX_JOB_DESCRIPTION + " characters.");
        }
        if (type == InterviewType.TECHNICAL && skills.isEmpty() && jobDescription == null && !request.useResume()) {
            throw new InvalidInterviewException("Add some skills, paste a job description or use your resume so the questions fit you.");
        }
        InterviewSource source = request.useResume() ? InterviewSource.RESUME
                : jobDescription == null ? InterviewSource.SKILLS : InterviewSource.JOB;
        String resolvedRole = role.isEmpty() ? "General role" : role;
        return new MockInterviewSession.Goal(source, resolvedRole,
                skills.isEmpty() ? null : String.join(", ", skills), jobDescription, null);
    }

    private static UUID parseCourseId(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new InvalidInterviewException("That isn't a valid course.");
        }
    }

    private static String clipLabel(String value) {
        String clean = value.replaceAll("[^\\p{L}\\p{N} &/+.,'()-]", " ").replaceAll("\\s+", " ").strip();
        clean = clean.length() > 100 ? clean.substring(0, 100).strip() : clean;
        return clean.length() < 2 ? "Learner" : clean;
    }

    private static String clipSkills(String value) {
        String clean = clipLabel(value);
        return clean.length() > 590 ? clean.substring(0, 590) : clean;
    }

    /**
     * A short line of context for the speech model: the role and skills of the learner's own interview. It helps spell technical words
     * (Redis, Kubernetes) correctly. Null when the interview isn't theirs, so nothing about anyone else's leaks into a request.
     */
    public String speechHint(Long userId, Long sessionId) {
        if (sessionId == null) {
            return null;
        }
        return sessions.findByIdAndUserId(sessionId, userId).map(s -> {
            String hint = "Interview answer for a " + s.getTargetRole() + "."
                    + (s.getSkills() == null ? "" : " Terms: " + s.getSkills() + ".");
            return hint.length() > 220 ? hint.substring(0, 220) : hint;
        }).orElse(null);
    }

    public Quota quota(Long userId) {
        VoiceService.Usage spoken = voice.usage(userId);
        return new Quota(sessions.countByUserIdAndCreatedAtAfter(userId, Instant.now().minus(1, ChronoUnit.DAYS)), MAX_PER_DAY,
                spoken.used(), spoken.limit());
    }

    // ---- Reading ------------------------------------------------------------------------------------------------------

    public Detail detail(Long sessionId, Long userId) {
        return tx.execute(status -> {
            MockInterviewSession session = ownedSession(sessionId, userId);
            return new Detail(session, questions.findBySessionIdOrderByQuestionIndexAsc(sessionId));
        });
    }

    public List<MockInterviewSession> history(Long userId) {
        return sessions.findByUserIdOrderByCreatedAtDescIdDesc(userId, PageRequest.of(0, HISTORY_LIMIT));
    }

    // ---- Answering ----------------------------------------------------------------------------------------------------

    public AnswerResult submitAnswer(Long sessionId, Long userId, Long questionId, String rawAnswer) {
        String answer = rawAnswer == null ? "" : rawAnswer.strip();
        if (answer.length() < MIN_ANSWER) {
            throw new InvalidInterviewException("Write a fuller answer (at least " + MIN_ANSWER + " characters) so it can be assessed.");
        }
        if (answer.length() > MAX_ANSWER) {
            throw new InvalidInterviewException("Keep the answer under " + MAX_ANSWER + " characters.");
        }

        // 1. Check everything before spending an AI call.
        Snapshot before = tx.execute(status -> {
            MockInterviewSession session = ownedSession(sessionId, userId);
            requireInProgress(session);
            MockInterviewQuestion question = questions.findByIdAndSessionId(questionId, sessionId).orElseThrow(InterviewNotFoundException::new);
            if (question.isAnswered()) {
                throw new InterviewStateException("You've already answered this question.");
            }
            return new Snapshot(session, question, questions.countBySessionIdAndParentQuestionIdIsNotNull(sessionId));
        });

        // 2. The AI, outside any transaction. If it fails, nothing is recorded and the learner can simply try again.
        boolean followUpAllowed = !before.question().isFollowUp() && before.followUps() < MAX_FOLLOW_UPS;
        MockInterviewQuestion.Evaluation evaluation = ai.evaluate(before.question(), before.session(), answer, followUpAllowed);

        // 3. Record it, re-checking under a lock in case a second request got here first.
        return tx.execute(status -> {
            MockInterviewSession session = ownedSession(sessionId, userId);
            MockInterviewQuestion question = questions.findForUpdate(questionId, sessionId).orElseThrow(InterviewNotFoundException::new);
            requireInProgress(session);
            if (question.isAnswered()) {
                throw new InterviewStateException("You've already answered this question.");
            }
            question.recordAnswer(answer, evaluation);
            session.questionAnswered();
            questions.save(question);

            // A weak answer can earn one follow-up, placed right after it. Re-checked here under the lock: the counts read earlier
            // may be stale, and the limit must hold however requests interleave.
            MockInterviewQuestion followUp = null;
            if (evaluation.followUp() != null && !question.isFollowUp() && evaluation.score() < FOLLOW_UP_BELOW
                    && questions.countBySessionIdAndParentQuestionIdIsNotNull(sessionId) < MAX_FOLLOW_UPS) {
                questions.shiftAfter(sessionId, question.getQuestionIndex(), 100);
                followUp = questions.save(MockInterviewQuestion.followUp(sessionId, question.getQuestionIndex() + 1,
                        evaluation.followUp(), question));
                questions.shiftAfter(sessionId, question.getQuestionIndex() + 1, -99);
                session.followUpAdded();
            }
            sessions.save(session);
            return new AnswerResult(question, session, followUp);
        });
    }

    private record Snapshot(MockInterviewSession session, MockInterviewQuestion question, long followUps) {
    }

    // ---- Finishing ----------------------------------------------------------------------------------------------------

    /** Finishes the interview and pays its XP, once. Asking again just returns the finished result. */
    public Completion complete(Long sessionId, Long userId) {
        return tx.execute(status -> {
            MockInterviewSession session = ownedSession(sessionId, userId);
            List<MockInterviewQuestion> all = questions.findBySessionIdOrderByQuestionIndexAsc(sessionId);
            if (session.isCompleted()) {
                return new Completion(session, all, session.getXpEarned());
            }
            requireInProgress(session);
            if (all.isEmpty() || all.stream().anyMatch(q -> !q.isAnswered())) {
                throw new InterviewStateException("Answer every question before finishing the interview.");
            }

            int total = all.stream().mapToInt(MockInterviewQuestion::getScore).sum();
            int percent = (int) Math.round(100.0 * total / (all.size() * 10));
            String readiness = percent >= 85 ? "EXCELLENT" : percent >= 65 ? "GOOD" : "NEEDS_PRACTICE";

            int xp = xpFor(gamification.pointsFor(PointAction.MOCK_INTERVIEW_COMPLETE), percent);
            boolean paid = xp > 0 && gamification.award(userId, PointAction.MOCK_INTERVIEW_COMPLETE, xp,
                    "Completed AI mock interview: " + session.getStream() + " (" + percent + "%)", session.getStream(), sessionId.toString());
            session.complete(percent, readiness, summaryFor(readiness, session.getInterviewType()), topFixOf(all), paid ? xp : 0);
            sessions.save(session);
            return new Completion(session, all, session.getXpEarned());
        });
    }

    /** The improvement note from the weakest answer: the one thing to work on first. */
    private static String topFixOf(List<MockInterviewQuestion> all) {
        return all.stream().min(java.util.Comparator.comparingInt(MockInterviewQuestion::getScore))
                .map(MockInterviewQuestion::getAreasToImprove).filter(text -> text != null && !text.isBlank()).orElse(null);
    }

    /**
     * The base points for finishing, plus the same again scaled by the score: a 70% interview on the default 50 earns 85. The score is
     * what reaches the leaderboard, so a better interview ranks higher, and the base still rewards finishing at all.
     */
    static int xpFor(int base, int percent) {
        return base <= 0 ? 0 : base + (int) Math.round(base * percent / 100.0);
    }

    private static String summaryFor(String readiness, String interviewType) {
        if (InterviewType.HR.name().equals(interviewType)) {
            return switch (readiness) {
                case "EXCELLENT" -> "A confident HR round. Your examples were specific, you owned your part in them, and you came "
                        + "across clearly and honestly.";
                case "GOOD" -> "A good HR round. Tighten your examples around what you did and what changed because of it, and "
                        + "end each answer on the result.";
                default -> "A start to build on. Prepare three or four real stories you can reuse, and practise telling them as "
                        + "situation, what you did, and the result.";
            };
        }
        return switch (readiness) {
            case "EXCELLENT" -> "Outstanding interview performance! Your technical depth, problem-solving structure, and communication "
                    + "make you highly competitive for top-tier roles.";
            case "GOOD" -> "Good performance with strong fundamentals. Focus on detailing trade-offs and handling complex edge cases "
                    + "to reach top-tier readiness.";
            default -> "Solid start. Spend more time revising core system design patterns, data structures, and practicing "
                    + "structured communication.";
        };
    }

    // ---- Admin --------------------------------------------------------------------------------------------------------

    public record RecentSession(MockInterviewSession session, String candidateName, String candidateEmail) {
    }

    public record Analytics(long totalSessions, long completedSessions, long averageScore, Map<String, Long> byTrack,
                            Map<String, Long> byDifficulty, Map<String, Integer> averageByStream, List<RecentSession> recent) {
    }

    public Analytics analytics() {
        return tx.execute(status -> {
            Map<String, Long> byTrack = new LinkedHashMap<>();
            sessions.countByTrack().forEach(r -> byTrack.put((String) r[0], ((Number) r[1]).longValue()));
            Map<String, Long> byDifficulty = new LinkedHashMap<>();
            sessions.countByDifficulty().forEach(r -> byDifficulty.put((String) r[0], ((Number) r[1]).longValue()));
            Map<String, Integer> byStream = new LinkedHashMap<>();
            sessions.averageScoreByStream().forEach(r -> byStream.put((String) r[0], (int) Math.round(((Number) r[1]).doubleValue())));

            List<RecentSession> recent = sessions.recentWithUsers(PageRequest.of(0, RECENT_FOR_ADMIN)).stream()
                    .map(r -> new RecentSession((MockInterviewSession) r[0], ((User) r[1]).getDisplayName(), ((User) r[1]).getEmail()))
                    .toList();

            return new Analytics(sessions.count(), sessions.countByStatus(InterviewStatus.COMPLETED.name()),
                    Math.round(sessions.averageScore()), byTrack, byDifficulty, byStream, recent);
        });
    }

    public record AdminDetail(MockInterviewSession session, List<MockInterviewQuestion> questions, String candidateName,
                              String candidateEmail) {
    }

    public AdminDetail adminDetail(Long sessionId) {
        return tx.execute(status -> {
            MockInterviewSession session = sessions.findById(sessionId).orElseThrow(InterviewNotFoundException::new);
            User candidate = users.findById(session.getUserId()).orElse(null);
            return new AdminDetail(session, questions.findBySessionIdOrderByQuestionIndexAsc(sessionId),
                    candidate == null ? "Deleted user" : candidate.getDisplayName(), candidate == null ? "" : candidate.getEmail());
        });
    }

    // ---- Helpers ------------------------------------------------------------------------------------------------------

    /** The learner's own interview, or "not found": someone else's looks exactly like one that doesn't exist. */
    private MockInterviewSession ownedSession(Long sessionId, Long userId) {
        return sessions.findByIdAndUserId(sessionId, userId).orElseThrow(InterviewNotFoundException::new);
    }

    private static void requireInProgress(MockInterviewSession session) {
        if (!session.isInProgress()) {
            throw new InterviewStateException("This interview is already finished.");
        }
    }
}
