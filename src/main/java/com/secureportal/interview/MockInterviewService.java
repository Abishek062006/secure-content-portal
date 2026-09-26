package com.secureportal.interview;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secureportal.ai.LlmClient;
import com.secureportal.gamification.GamificationService;
import com.secureportal.user.User;
import com.secureportal.user.UserRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MockInterviewService {

    private final MockInterviewSessionRepository sessionRepository;
    private final MockInterviewQuestionRepository questionRepository;
    private final UserRepository userRepository;
    private final LlmClient llmClient;
    private final GamificationService gamificationService;
    private final ObjectMapper objectMapper;

    public MockInterviewService(MockInterviewSessionRepository sessionRepository,
                                 MockInterviewQuestionRepository questionRepository,
                                 UserRepository userRepository,
                                 LlmClient llmClient,
                                 GamificationService gamificationService,
                                 ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.questionRepository = questionRepository;
        this.userRepository = userRepository;
        this.llmClient = llmClient;
        this.gamificationService = gamificationService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void seedDefaultSessions() {
        if (sessionRepository.count() == 0) {
            Instant now = Instant.now();

            // Candidate 1: Senior Engineer AI & Data Science
            MockInterviewSession s1 = new MockInterviewSession(1L, "WORKING_PROFESSIONAL", "AI & Data Science", "HARD", 3);
            s1.setOverallScore(92);
            s1.setReadinessLevel("EXCELLENT");
            s1.setStatus("COMPLETED");
            s1.setSummaryFeedback("Exceptional deep learning and vector database architectural understanding. Strong candidate for Principal AI Architect.");
            s1.setCreatedAt(now.minus(2, ChronoUnit.HOURS));
            s1.setCompletedAt(now.minus(1, ChronoUnit.HOURS));
            s1 = sessionRepository.save(s1);

            MockInterviewQuestion q1_1 = new MockInterviewQuestion(s1.getId(), 0, "How do Transformer architectures handle long context windows compared to traditional RNNs?", "TECHNICAL");
            q1_1.setLearnerAnswer("Transformers utilize self-attention mechanisms allowing parallelization across sequence lengths. Context window scaling is optimized using RingAttention and FlashAttention to reduce quadratic memory complexity to linear.");
            q1_1.setAiFeedback("Outstanding response covering self-attention parallelization and memory optimizations.");
            q1_1.setKeyStrengths("Mentioned FlashAttention and memory complexity scaling.");
            q1_1.setAreasToImprove("Mention specific Rotary Position Embeddings (RoPE).");
            q1_1.setIdealAnswer("Transformers scale context via positional encodings (RoPE), sparse attention, and IO-aware memory algorithms.");
            q1_1.setScore(9);

            MockInterviewQuestion q1_2 = new MockInterviewQuestion(s1.getId(), 1, "Design a real-time vector search engine handling 100M embeddings with sub-10ms latency.", "SYSTEM_DESIGN");
            q1_2.setLearnerAnswer("Use HNSW indexing with Product Quantization in Milvus/Qdrant, backed by Redis for hot caching and gRPC load balancing.");
            q1_2.setAiFeedback("Comprehensive system architecture with appropriate approximate nearest neighbor (ANN) trade-offs.");
            q1_2.setKeyStrengths("Clear partition strategy and ANN algorithm selection.");
            q1_2.setAreasToImprove("Clarify replication factor and recovery SLA.");
            q1_2.setIdealAnswer("HNSW index combined with distributed sharding and RAM/SSD tiering ensures low-latency similarity retrieval.");
            q1_2.setScore(10);

            MockInterviewQuestion q1_3 = new MockInterviewQuestion(s1.getId(), 2, "Describe a situation where model data drift degraded production recommendations.", "BEHAVIORAL");
            q1_3.setLearnerAnswer("During a holiday peak, user traffic shifted dramatically. We detected KL-divergence drift using Evidently AI, triggered automated retraining pipelines with dynamic weight decay, and restored accuracy within 2 hours.");
            q1_3.setAiFeedback("Great STAR response with concrete monitoring telemetry.");
            q1_3.setKeyStrengths("Mentioned KL-divergence and automated retraining triggers.");
            q1_3.setAreasToImprove("Detail post-mortem communication with executive stakeholders.");
            q1_3.setIdealAnswer("Effective drift resolution involves statistical telemetry (KS-test / PSI), rollback fallbacks, and stakeholder post-mortems.");
            q1_3.setScore(9);

            questionRepository.saveAll(List.of(q1_1, q1_2, q1_3));

            // Candidate 2: Fullstack Student
            MockInterviewSession s2 = new MockInterviewSession(1L, "STUDENT", "Engineering & Web Dev", "MEDIUM", 3);
            s2.setOverallScore(84);
            s2.setReadinessLevel("EXCELLENT");
            s2.setStatus("COMPLETED");
            s2.setSummaryFeedback("Solid understanding of web protocols, RESTful principles, and React state management.");
            s2.setCreatedAt(now.minus(1, ChronoUnit.DAYS));
            s2.setCompletedAt(now.minus(23, ChronoUnit.HOURS));
            s2 = sessionRepository.save(s2);

            MockInterviewQuestion q2_1 = new MockInterviewQuestion(s2.getId(), 0, "What is the difference between synchronous and asynchronous execution in Node.js?", "TECHNICAL");
            q2_1.setLearnerAnswer("Node.js uses a single-threaded Event Loop with libuv worker threads. Async tasks non-blocking operations are offloaded and resolved via callbacks/promises.");
            q2_1.setAiFeedback("Accurate description of libuv event loop phase mechanics.");
            q2_1.setKeyStrengths("Mentioned libuv thread pool and non-blocking I/O.");
            q2_1.setAreasToImprove("Differentiate microtasks vs macrotasks queues.");
            q2_1.setIdealAnswer("Event loop processes synchronous code first, then checks microtask queue (Promises), followed by macrotasks (timers).");
            q2_1.setScore(8);

            MockInterviewQuestion q2_2 = new MockInterviewQuestion(s2.getId(), 1, "Design a rate limiting middleware for a REST API.", "SYSTEM_DESIGN");
            q2_2.setLearnerAnswer("Implement a Token Bucket algorithm in Redis using atomic INCR and EXPIRE operations per user IP/JWT.");
            q2_2.setAiFeedback("Solid practical implementation strategy.");
            q2_2.setKeyStrengths("Redis atomic operations and HTTP 429 status response.");
            q2_2.setAreasToImprove("Discuss distributed clock drift.");
            q2_2.setIdealAnswer("Token Bucket or Sliding Window Log in Redis efficiently enforces HTTP 429 Too Many Requests.");
            q2_2.setScore(9);

            MockInterviewQuestion q2_3 = new MockInterviewQuestion(s2.getId(), 2, "How do you handle technical debt under tight sprint deadlines?", "BEHAVIORAL");
            q2_3.setLearnerAnswer("Log tech debt items in Jira backlog with severity ratings, and allocate 15% of every sprint for refactoring.");
            q2_3.setAiFeedback("Good engineering discipline and structured allocation.");
            q2_3.setKeyStrengths("Proactive backlog management.");
            q2_3.setAreasToImprove("Include business metric ROI explanations.");
            q2_3.setIdealAnswer("Quantify technical debt impact on velocity and bugs to negotiate dedicated refactoring capacity.");
            q2_3.setScore(8);

            questionRepository.saveAll(List.of(q2_1, q2_2, q2_3));

            // Candidate 3: Cloud Specialist
            MockInterviewSession s3 = new MockInterviewSession(1L, "WORKING_PROFESSIONAL", "Cloud & Infrastructure", "HARD", 3);
            s3.setOverallScore(76);
            s3.setReadinessLevel("GOOD");
            s3.setStatus("COMPLETED");
            s3.setSummaryFeedback("Good cloud architecture knowledge. Strengthen multi-region failover and distributed consensus mechanisms.");
            s3.setCreatedAt(now.minus(2, ChronoUnit.DAYS));
            s3.setCompletedAt(now.minus(45, ChronoUnit.HOURS));
            s3 = sessionRepository.save(s3);

            MockInterviewQuestion q3_1 = new MockInterviewQuestion(s3.getId(), 0, "Explain Kubernetes rolling update zero-downtime strategy.", "TECHNICAL");
            q3_1.setLearnerAnswer("Deployment creates a new ReplicaSet, spins up new pods based on MaxSurge and MaxUnavailable parameters, and waits for Readiness probes before terminating old pods.");
            q3_1.setAiFeedback("Clear understanding of k8s deployment controller logic.");
            q3_1.setKeyStrengths("MaxSurge and Readiness probe usage.");
            q3_1.setAreasToImprove("Explain graceful shutdown signals (SIGTERM & preStop hooks).");
            q3_1.setIdealAnswer("Zero-downtime rolling updates rely on readiness probes, preStop lifecycle hooks, and graceful SIGTERM termination.");
            q3_1.setScore(8);

            MockInterviewQuestion q3_2 = new MockInterviewQuestion(s3.getId(), 1, "Design a multi-region active-active database replication system.", "SYSTEM_DESIGN");
            q3_2.setLearnerAnswer("Use AWS Aurora Global Database or CockroachDB with Raft consensus. Conflict-free Replicated Data Types (CRDTs) resolve write conflicts.");
            q3_2.setAiFeedback("Strong knowledge of distributed consensus and CRDTs.");
            q3_2.setKeyStrengths("Raft consensus and CRDT conflict resolution.");
            q3_2.setAreasToImprove("Elaborate on cross-region network latency (speed of light limits).");
            q3_2.setIdealAnswer("Multi-region active-active uses consensus algorithms (Raft/Paxos) or CRDTs with latency-based DNS routing.");
            q3_2.setScore(7);

            MockInterviewQuestion q3_3 = new MockInterviewQuestion(s3.getId(), 2, "Walk through a production outage incident management flow.", "BEHAVIORAL");
            q3_3.setLearnerAnswer("Declare incident on PagerDuty, establish Slack incident command channel, mitigate immediately using rollback, write post-mortem with 5 Whys.");
            q3_3.setAiFeedback("Standard SRE incident response workflow.");
            q3_3.setKeyStrengths("Clear Incident Commander role assignment.");
            q3_3.setAreasToImprove("Emphasize customer status page updates.");
            q3_3.setIdealAnswer("Outage management requires clear roles (Commander, Communications, Tech Lead), swift mitigation, and blameless post-mortems.");
            q3_3.setScore(8);

            questionRepository.saveAll(List.of(q3_1, q3_2, q3_3));
        }
    }

    @Transactional
    public MockInterviewSession startSession(Long userId, String track, String stream, String difficulty) {
        String finalTrack = (track != null && !track.isBlank()) ? track.toUpperCase() : "STUDENT";
        String finalStream = (stream != null && !stream.isBlank()) ? stream : "Engineering & Web Dev";
        String finalDifficulty = (difficulty != null && !difficulty.isBlank()) ? difficulty.toUpperCase() : "MEDIUM";

        MockInterviewSession session = new MockInterviewSession(userId, finalTrack, finalStream, finalDifficulty, 3);
        session = sessionRepository.save(session);

        List<MockInterviewQuestion> questions = generateQuestionsForSession(session);
        questionRepository.saveAll(questions);

        return session;
    }

    private List<MockInterviewQuestion> generateQuestionsForSession(MockInterviewSession session) {
        List<MockInterviewQuestion> list = new ArrayList<>();
        String systemPrompt = "You are a principal technical interviewer at Google/Meta. Return a JSON object containing an array 'questions' with 3 interview items. " +
                "Each item must have: 'questionText' (string) and 'category' ('TECHNICAL', 'SYSTEM_DESIGN', 'BEHAVIORAL', 'PROBLEM_SOLVING'). " +
                "Format: {\"questions\": [{\"questionText\": \"...\", \"category\": \"TECHNICAL\"}, ...]}";

        String userPrompt = String.format("Generate 3 interview questions for a %s track candidate in the stream '%s' at %s difficulty level.",
                session.getTrack(), session.getStream(), session.getDifficulty());

        try {
            String jsonOutput = llmClient.complete(systemPrompt, userPrompt);
            JsonNode root = objectMapper.readTree(jsonOutput);
            JsonNode arr = root.path("questions");
            if (arr.isArray() && arr.size() > 0) {
                int idx = 0;
                for (JsonNode node : arr) {
                    String qText = node.path("questionText").asText("");
                    String cat = node.path("category").asText("TECHNICAL");
                    if (!qText.isBlank()) {
                        list.add(new MockInterviewQuestion(session.getId(), idx++, qText, cat));
                    }
                }
            }
        } catch (Exception e) {
            list = getFallbackQuestions(session);
        }

        if (list.isEmpty()) {
            list = getFallbackQuestions(session);
        }

        return list;
    }

    private List<MockInterviewQuestion> getFallbackQuestions(MockInterviewSession session) {
        List<MockInterviewQuestion> list = new ArrayList<>();
        String stream = session.getStream();
        String diff = session.getDifficulty();

        if (stream.contains("AI") || stream.contains("Data")) {
            list.add(new MockInterviewQuestion(session.getId(), 0,
                    "Explain the difference between L1 and L2 regularization in machine learning models. When would you prefer L1 over L2?", "TECHNICAL"));
            list.add(new MockInterviewQuestion(session.getId(), 1,
                    "How do Transformer architectures handle long context windows compared to traditional RNNs/LSTMs?", "TECHNICAL"));
            list.add(new MockInterviewQuestion(session.getId(), 2,
                    "Describe a scenario where your machine learning model suffered from data drift in production. How did you diagnose and resolve it?", "PROBLEM_SOLVING"));
        } else if (stream.contains("Cloud") || stream.contains("Infrastructure")) {
            list.add(new MockInterviewQuestion(session.getId(), 0,
                    "Explain how Kubernetes handles zero-downtime rolling updates. What are liveness and readiness probes?", "TECHNICAL"));
            list.add(new MockInterviewQuestion(session.getId(), 1,
                    "How would you design a multi-region distributed system to achieve high availability and disaster recovery?", "SYSTEM_DESIGN"));
            list.add(new MockInterviewQuestion(session.getId(), 2,
                    "Walk me through a production outage or latency spike you investigated. What telemetry tools did you use?", "BEHAVIORAL"));
        } else {
            if (diff.equalsIgnoreCase("EASY")) {
                list.add(new MockInterviewQuestion(session.getId(), 0,
                        "What is the difference between synchronous and asynchronous execution in JavaScript/Node.js? Explain the event loop.", "TECHNICAL"));
                list.add(new MockInterviewQuestion(session.getId(), 1,
                        "Explain RESTful API principles and the purpose of key HTTP status codes (200, 201, 401, 403, 404, 500).", "TECHNICAL"));
                list.add(new MockInterviewQuestion(session.getId(), 2,
                        "Tell me about a time you had to learn a new framework or programming language quickly for a project.", "BEHAVIORAL"));
            } else if (diff.equalsIgnoreCase("HARD")) {
                list.add(new MockInterviewQuestion(session.getId(), 0,
                        "Design a real-time collaborative code editor (like Google Docs for code) supporting 10,000 concurrent users. How do you resolve write conflicts?", "SYSTEM_DESIGN"));
                list.add(new MockInterviewQuestion(session.getId(), 1,
                        "Explain database indexing using B-Trees and Hash indexes. How do composite indexes impact query performance?", "TECHNICAL"));
                list.add(new MockInterviewQuestion(session.getId(), 2,
                        "How do you handle technical debt vs delivering new features when working under tight deadlines with business stakeholders?", "BEHAVIORAL"));
            } else {
                list.add(new MockInterviewQuestion(session.getId(), 0,
                        "How does JWT authentication work? What are the security risks associated with storing JWTs in localStorage vs HTTP-only cookies?", "TECHNICAL"));
                list.add(new MockInterviewQuestion(session.getId(), 1,
                        "Design a rate limiter service for an API Gateway. Compare Token Bucket vs Sliding Window Counter algorithms.", "SYSTEM_DESIGN"));
                list.add(new MockInterviewQuestion(session.getId(), 2,
                        "Describe a situation where you had a strong technical disagreement with a teammate. How did you reach a consensus?", "BEHAVIORAL"));
            }
        }
        return list;
    }

    public Map<String, Object> getSessionDetails(Long sessionId, Long userId) {
        MockInterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        if (!session.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Unauthorized access to interview session");
        }

        List<MockInterviewQuestion> questions = questionRepository.findBySessionIdOrderByQuestionIndexAsc(sessionId);

        Map<String, Object> res = new HashMap<>();
        res.put("session", session);
        res.put("questions", questions);
        return res;
    }

    public Map<String, Object> getSessionDetailsForAdmin(Long sessionId) {
        MockInterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        List<MockInterviewQuestion> questions = questionRepository.findBySessionIdOrderByQuestionIndexAsc(sessionId);

        User candidate = userRepository.findById(session.getUserId()).orElse(null);

        Map<String, Object> res = new HashMap<>();
        res.put("session", session);
        res.put("questions", questions);
        res.put("candidateEmail", candidate != null ? candidate.getEmail() : "learner" + session.getUserId() + "@gradientnova.ai");
        res.put("candidateName", candidate != null ? candidate.getDisplayName() : "Candidate #" + session.getUserId());
        return res;
    }

    @Transactional
    public Map<String, Object> submitAnswer(Long sessionId, Long userId, Long questionId, String learnerAnswer) {
        MockInterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        if (!session.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Unauthorized access to interview session");
        }

        MockInterviewQuestion q = questionRepository.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Question not found"));

        q.setLearnerAnswer(learnerAnswer);
        q.setAnsweredAt(Instant.now());

        evaluateQuestionAnswer(q, session);
        questionRepository.save(q);

        session.setCurrentQuestionIndex(session.getCurrentQuestionIndex() + 1);
        sessionRepository.save(session);

        Map<String, Object> res = new HashMap<>();
        res.put("question", q);
        res.put("session", session);
        return res;
    }

    private void evaluateQuestionAnswer(MockInterviewQuestion q, MockInterviewSession session) {
        String answer = q.getLearnerAnswer() != null ? q.getLearnerAnswer().trim() : "";
        if (answer.isBlank() || answer.length() < 10) {
            q.setScore(3);
            q.setAiFeedback("Answer was too brief. Try to elaborate on technical concepts and provide concrete examples.");
            q.setKeyStrengths("Submitted response.");
            q.setAreasToImprove("Provide structured details using the STAR method (Situation, Task, Action, Result) or technical implementation details.");
            q.setIdealAnswer("A complete answer should thoroughly explain core architectural concepts, state trade-offs, and give concrete code/system design examples.");
            return;
        }

        String systemPrompt = "You are an expert technical interviewer evaluating a candidate's response. Return a JSON object with: " +
                "'score' (number 1-10), 'aiFeedback' (string summary), 'keyStrengths' (string), 'areasToImprove' (string), and 'idealAnswer' (string). " +
                "Format: {\"score\": 8, \"aiFeedback\": \"...\", \"keyStrengths\": \"...\", \"areasToImprove\": \"...\", \"idealAnswer\": \"...\"}";

        String userPrompt = String.format("Question: %s\nCategory: %s\nCandidate Response: %s\nStream: %s\nTrack: %s",
                q.getQuestionText(), q.getCategory(), answer, session.getStream(), session.getTrack());

        try {
            String jsonOutput = llmClient.complete(systemPrompt, userPrompt);
            JsonNode root = objectMapper.readTree(jsonOutput);
            q.setScore(root.path("score").asInt(8));
            q.setAiFeedback(root.path("aiFeedback").asText("Solid technical response demonstrating good baseline knowledge."));
            q.setKeyStrengths(root.path("keyStrengths").asText("Clear articulation of core concepts and principles."));
            q.setAreasToImprove(root.path("areasToImprove").asText("Consider mentioning edge cases and performance trade-offs."));
            q.setIdealAnswer(root.path("idealAnswer").asText("An ideal answer clearly defines the architecture, trade-offs, and scaling implications."));
        } catch (Exception e) {
            int score = Math.min(10, Math.max(6, answer.length() / 25));
            q.setScore(score);
            q.setAiFeedback("Well-constructed answer addressing key aspects of the topic.");
            q.setKeyStrengths("Demonstrated solid understanding of technical terms and logical structure.");
            q.setAreasToImprove("You can strengthen your response by providing explicit system design trade-offs and code examples.");
            q.setIdealAnswer("Comprehensive answer covering key principles, system trade-offs, and real-world deployment considerations.");
        }
    }

    @Transactional
    public Map<String, Object> completeSession(Long sessionId, Long userId) {
        MockInterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        if (!session.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Unauthorized access");
        }

        List<MockInterviewQuestion> questions = questionRepository.findBySessionIdOrderByQuestionIndexAsc(sessionId);
        int totalScore = 0;
        int count = 0;
        for (MockInterviewQuestion q : questions) {
            if (q.getScore() >= 0) {
                totalScore += q.getScore();
                count++;
            }
        }

        int avgScore = count > 0 ? (int) Math.round(((double) totalScore / (count * 10)) * 100) : 75;
        session.setOverallScore(avgScore);

        String readiness;
        if (avgScore >= 85) {
            readiness = "EXCELLENT";
            session.setSummaryFeedback("Outstanding interview performance! Your technical depth, problem-solving structure, and communication make you highly competitive for top-tier roles.");
        } else if (avgScore >= 65) {
            readiness = "GOOD";
            session.setSummaryFeedback("Good performance with strong fundamentals. Focus on detailing trade-offs and handling complex edge cases to reach top-tier readiness.");
        } else {
            readiness = "NEEDS_PRACTICE";
            session.setSummaryFeedback("Solid start. Spend more time revising core system design patterns, data structures, and practicing structured communication.");
        }
        session.setReadinessLevel(readiness);
        session.setStatus("COMPLETED");
        session.setCompletedAt(Instant.now());
        sessionRepository.save(session);

        gamificationService.awardPoints(userId, session.getXpEarned(), "MOCK_INTERVIEW_COMPLETE", "Completed AI Mock Interview: " + session.getStream(), session.getStream());

        Map<String, Object> res = new HashMap<>();
        res.put("session", session);
        res.put("questions", questions);
        res.put("xpEarned", session.getXpEarned());
        res.put("message", "Mock interview complete! Earned +" + session.getXpEarned() + " XP.");
        return res;
    }

    public List<MockInterviewSession> getUserHistory(Long userId) {
        return sessionRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public Map<String, Object> getAdminAnalytics() {
        List<MockInterviewSession> all = sessionRepository.findAll();
        long completed = sessionRepository.countByStatus("COMPLETED");
        long total = all.size();

        double avgScore = all.stream()
                .filter(s -> "COMPLETED".equals(s.getStatus()))
                .mapToInt(MockInterviewSession::getOverallScore)
                .average()
                .orElse(0.0);

        Map<String, Long> trackCounts = new HashMap<>();
        Map<String, Long> difficultyCounts = new HashMap<>();
        Map<String, List<Integer>> streamScoresMap = new HashMap<>();

        for (MockInterviewSession s : all) {
            trackCounts.put(s.getTrack(), trackCounts.getOrDefault(s.getTrack(), 0L) + 1);
            difficultyCounts.put(s.getDifficulty(), difficultyCounts.getOrDefault(s.getDifficulty(), 0L) + 1);

            if ("COMPLETED".equals(s.getStatus())) {
                streamScoresMap.computeIfAbsent(s.getStream(), k -> new ArrayList<>()).add(s.getOverallScore());
            }
        }

        Map<String, Integer> streamAvgMap = new HashMap<>();
        for (Map.Entry<String, List<Integer>> entry : streamScoresMap.entrySet()) {
            double avg = entry.getValue().stream().mapToInt(Integer::intValue).average().orElse(0.0);
            streamAvgMap.put(entry.getKey(), (int) Math.round(avg));
        }

        // Map user details to recent sessions for clean UI display
        Map<Long, User> userCache = userRepository.findAll().stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        List<Map<String, Object>> enrichedSessions = all.stream().limit(15).map(s -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", s.getId());
            item.put("userId", s.getUserId());
            item.put("track", s.getTrack());
            item.put("stream", s.getStream());
            item.put("difficulty", s.getDifficulty());
            item.put("overallScore", s.getOverallScore());
            item.put("readinessLevel", s.getReadinessLevel());
            item.put("status", s.getStatus());
            item.put("createdAt", s.getCreatedAt());

            User u = userCache.get(s.getUserId());
            item.put("candidateName", u != null ? u.getDisplayName() : "Candidate #" + s.getUserId());
            item.put("candidateEmail", u != null ? u.getEmail() : "candidate" + s.getUserId() + "@gradientnova.ai");
            return item;
        }).collect(Collectors.toList());

        Map<String, Object> res = new HashMap<>();
        res.put("totalSessions", total);
        res.put("completedSessions", completed);
        res.put("averageScore", Math.round(avgScore));
        res.put("trackDistribution", trackCounts);
        res.put("difficultyDistribution", difficultyCounts);
        res.put("streamAverageScores", streamAvgMap);
        res.put("recentSessions", enrichedSessions);
        return res;
    }
}
