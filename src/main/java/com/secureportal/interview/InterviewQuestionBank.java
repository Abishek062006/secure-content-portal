package com.secureportal.interview;

import java.util.List;

/**
 * Hand-written questions used when the AI can't supply a set. They are ordinary interview questions, not AI output, so the
 * interview always works even without an AI key or when the provider is down.
 */
final class InterviewQuestionBank {

    record Item(String text, QuestionCategory category) {
    }

    private static final List<Item> AI_DATA = List.of(
            new Item("Explain the difference between L1 and L2 regularization in machine learning models. When would you prefer L1 over L2?", QuestionCategory.TECHNICAL),
            new Item("How do Transformer architectures handle long context windows compared to traditional RNNs/LSTMs?", QuestionCategory.TECHNICAL),
            new Item("Describe a scenario where your machine learning model suffered from data drift in production. How did you diagnose and resolve it?", QuestionCategory.PROBLEM_SOLVING));

    private static final List<Item> CLOUD = List.of(
            new Item("Explain how Kubernetes handles zero-downtime rolling updates. What are liveness and readiness probes?", QuestionCategory.TECHNICAL),
            new Item("How would you design a multi-region distributed system to achieve high availability and disaster recovery?", QuestionCategory.SYSTEM_DESIGN),
            new Item("Walk me through a production outage or latency spike you investigated. What telemetry tools did you use?", QuestionCategory.BEHAVIORAL));

    private static final List<Item> WEB_EASY = List.of(
            new Item("What is the difference between synchronous and asynchronous execution in JavaScript/Node.js? Explain the event loop.", QuestionCategory.TECHNICAL),
            new Item("Explain RESTful API principles and the purpose of key HTTP status codes (200, 201, 401, 403, 404, 500).", QuestionCategory.TECHNICAL),
            new Item("Tell me about a time you had to learn a new framework or programming language quickly for a project.", QuestionCategory.BEHAVIORAL));

    private static final List<Item> WEB_MEDIUM = List.of(
            new Item("How does JWT authentication work? What are the security risks associated with storing JWTs in localStorage vs HTTP-only cookies?", QuestionCategory.TECHNICAL),
            new Item("Design a rate limiter service for an API Gateway. Compare Token Bucket vs Sliding Window Counter algorithms.", QuestionCategory.SYSTEM_DESIGN),
            new Item("Describe a situation where you had a strong technical disagreement with a teammate. How did you reach a consensus?", QuestionCategory.BEHAVIORAL));

    private static final List<Item> WEB_HARD = List.of(
            new Item("Design a real-time collaborative code editor (like Google Docs for code) supporting 10,000 concurrent users. How do you resolve write conflicts?", QuestionCategory.SYSTEM_DESIGN),
            new Item("Explain database indexing using B-Trees and Hash indexes. How do composite indexes impact query performance?", QuestionCategory.TECHNICAL),
            new Item("How do you handle technical debt vs delivering new features when working under tight deadlines with business stakeholders?", QuestionCategory.BEHAVIORAL));

    private InterviewQuestionBank() {
    }

    static List<Item> forStream(String stream, InterviewDifficulty difficulty) {
        String lower = stream.toLowerCase();
        if (lower.contains("ai") || lower.contains("data")) {
            return AI_DATA;
        }
        if (lower.contains("cloud") || lower.contains("infrastructure")) {
            return CLOUD;
        }
        return switch (difficulty) {
            case EASY -> WEB_EASY;
            case HARD -> WEB_HARD;
            case MEDIUM -> WEB_MEDIUM;
        };
    }
}
