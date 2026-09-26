package com.secureportal.interview;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    static final int HISTORY_LIMIT = 50;
    static final int RECENT_FOR_ADMIN = 15;
    static final String DEFAULT_STREAM = "Engineering & Web Dev";

    /** A stream is a short topic label (it goes into the AI prompt), not free-form text. */
    private static final Pattern STREAM_LABEL = Pattern.compile("[\\p{L}\\p{N} &/+.,'()-]{2,100}");

    private final MockInterviewSessionRepository sessions;
    private final MockInterviewQuestionRepository questions;
    private final UserRepository users;
    private final InterviewAi ai;
    private final GamificationService gamification;
    private final TransactionTemplate tx;

    public MockInterviewService(MockInterviewSessionRepository sessions, MockInterviewQuestionRepository questions,
                                UserRepository users, InterviewAi ai, GamificationService gamification,
                                PlatformTransactionManager transactionManager) {
        this.sessions = sessions;
        this.questions = questions;
        this.users = users;
        this.ai = ai;
        this.gamification = gamification;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public record Detail(MockInterviewSession session, List<MockInterviewQuestion> questions) {
    }

    public record Completion(MockInterviewSession session, List<MockInterviewQuestion> questions, int xpEarned) {
    }

    public record AnswerResult(MockInterviewQuestion question, MockInterviewSession session) {
    }

    // ---- Starting -----------------------------------------------------------------------------------------------------

    public MockInterviewSession start(Long userId, String track, String stream, String difficulty) {
        InterviewTrack chosenTrack = track == null || track.isBlank() ? InterviewTrack.STUDENT
                : InterviewTrack.parse(track).orElseThrow(() -> new InvalidInterviewException("Choose Student or Working professional."));
        InterviewDifficulty chosenDifficulty = difficulty == null || difficulty.isBlank() ? InterviewDifficulty.MEDIUM
                : InterviewDifficulty.parse(difficulty).orElseThrow(() -> new InvalidInterviewException("Choose Easy, Medium or Hard."));
        String chosenStream = stream == null || stream.isBlank() ? DEFAULT_STREAM : stream.strip();
        if (!STREAM_LABEL.matcher(chosenStream).matches()) {
            throw new InvalidInterviewException("Choose a stream from the list.");
        }
        if (sessions.countByUserIdAndCreatedAtAfter(userId, Instant.now().minus(1, ChronoUnit.DAYS)) >= MAX_PER_DAY) {
            throw new InterviewLimitException(MAX_PER_DAY);
        }

        // The AI is asked before anything is written, so a failure leaves nothing half-created.
        List<InterviewQuestionBank.Item> items = ai.questionsFor(chosenTrack, chosenStream, chosenDifficulty);

        return tx.execute(status -> {
            sessions.abandonOpen(userId);
            MockInterviewSession session = sessions.save(
                    new MockInterviewSession(userId, chosenTrack, chosenStream, chosenDifficulty, items.size()));
            for (int i = 0; i < items.size(); i++) {
                questions.save(new MockInterviewQuestion(session.getId(), i, items.get(i).text(), items.get(i).category()));
            }
            return session;
        });
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
            return new Snapshot(session, question);
        });

        // 2. The AI, outside any transaction. If it fails, nothing is recorded and the learner can simply try again.
        MockInterviewQuestion.Evaluation evaluation = ai.evaluate(before.question(), before.session(), answer);

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
            sessions.save(session);
            return new AnswerResult(question, session);
        });
    }

    private record Snapshot(MockInterviewSession session, MockInterviewQuestion question) {
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

            int xp = gamification.pointsFor(PointAction.MOCK_INTERVIEW_COMPLETE);
            boolean paid = xp > 0 && gamification.award(userId, PointAction.MOCK_INTERVIEW_COMPLETE, xp,
                    "Completed AI mock interview: " + session.getStream(), session.getStream(), sessionId.toString());
            session.complete(percent, readiness, summaryFor(readiness), paid ? xp : 0);
            sessions.save(session);
            return new Completion(session, all, session.getXpEarned());
        });
    }

    private static String summaryFor(String readiness) {
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
