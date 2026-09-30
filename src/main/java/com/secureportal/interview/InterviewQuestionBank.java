package com.secureportal.interview;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Hand-written questions used when the AI can't supply a set, and the HR topics an HR interview is drawn from. They are ordinary
 * interview questions, not AI output, so the interview always works even without an AI key or when the provider is down.
 */
final class InterviewQuestionBank {

    record Item(String text, QuestionCategory category) {
    }

    /** One thing HR interviewers ask about, and a few natural ways of asking it. */
    record HrTopic(String name, List<String> questions) {
    }

    private static final Item WARM_UP = new Item("Tell me a little about yourself and what draws you to this kind of role.",
            QuestionCategory.BEHAVIORAL);
    private static final Item CLOSING = new Item(
            "Tell me about a project you're proud of. What was your part in it, and what would you do differently?", QuestionCategory.BEHAVIORAL);

    private static final List<String> HR_WARM_UPS = List.of(
            "Tell me a little about yourself and what draws you to this kind of role.",
            "Walk me through your background and how you ended up applying for this role.",
            "Let's start easy: tell me about yourself, and what you're looking for next.");

    static final List<HrTopic> HR_TOPICS = List.of(
            new HrTopic("motivation for this role", List.of(
                    "What made you want to apply for this role in particular?",
                    "Why are you looking for this kind of opportunity right now?")),
            new HrTopic("strengths", List.of(
                    "What would you say is your biggest strength? Give me an example of it in action.",
                    "What do teammates or classmates usually come to you for, and why?")),
            new HrTopic("a weakness being worked on", List.of(
                    "What's a weakness you're actively working on, and what are you doing about it?",
                    "Tell me about a skill you've had to work hard to get better at.")),
            new HrTopic("teamwork", List.of(
                    "Tell me about a time you worked in a team that wasn't getting along. What did you do?",
                    "Describe your part in the best team you've been a member of. What made it work?")),
            new HrTopic("handling conflict", List.of(
                    "Tell me about a disagreement you had with a teammate or a manager, and how it was resolved.",
                    "Describe a time someone criticised your work. How did you respond?")),
            new HrTopic("failure and what was learned", List.of(
                    "Tell me about a time something you were responsible for didn't go to plan. What did you learn?",
                    "Describe a mistake you made on a project and how you handled it afterwards.")),
            new HrTopic("leadership and initiative", List.of(
                    "Tell me about a time you took the lead on something without being asked.",
                    "Describe something you improved at work or college that nobody asked you to.")),
            new HrTopic("working under pressure", List.of(
                    "Tell me about a time you had a very tight deadline. How did you manage it?",
                    "How do you handle it when several important things are due at once? Give me a real example.")),
            new HrTopic("prioritising", List.of(
                    "When everything seems urgent, how do you decide what to do first?",
                    "Tell me about a time you had to say no to something, or push it back. How did you handle it?")),
            new HrTopic("adapting to change", List.of(
                    "Tell me about a time plans changed suddenly. How did you adapt?",
                    "Describe a time you had to learn something new very quickly to get a job done.")),
            new HrTopic("career goals", List.of(
                    "Where do you see yourself in three to five years?",
                    "What kind of work do you want to be doing next, and how does this role fit into that?")),
            new HrTopic("explaining things clearly", List.of(
                    "Tell me about a time you had to explain something complicated to someone without your background.",
                    "How do you make sure people understand you when you're sharing an idea in a meeting?")),
            new HrTopic("feedback", List.of(
                    "What's the most useful feedback you've ever received, and what did you change because of it?",
                    "Tell me about a time you gave someone difficult feedback. How did you approach it?")),
            new HrTopic("integrity", List.of(
                    "Tell me about a time you had to choose between the right thing and the easy thing.",
                    "What would you do if you noticed a teammate cutting corners on something important?")),
            new HrTopic("proudest achievement", List.of(
                    "What achievement are you proudest of so far, and why that one?",
                    "Tell me about something you built or did that you'd love to show off.")),
            new HrTopic("why we should hire you", List.of(
                    "Why should we pick you over the other candidates for this role?",
                    "What would you want to get done in your first three months here?")),
            new HrTopic("work style", List.of(
                    "Describe the kind of environment where you do your best work.",
                    "How do you like to be managed, and how do you keep yourself on track?")));

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

    /** Asked after the role's own questions when a longer interview needs more. */
    private static final List<Item> GENERAL_TECHNICAL = List.of(
            new Item("Walk me through how you'd debug a feature that works on your machine but fails in production.", QuestionCategory.PROBLEM_SOLVING),
            new Item("How do you decide what and how to test after writing a new piece of code?", QuestionCategory.TECHNICAL),
            new Item("Pick a technical concept you know really well and explain it to me as if I'd just joined your team.", QuestionCategory.TECHNICAL),
            new Item("What happens, step by step, between typing a web address into a browser and seeing the page?", QuestionCategory.TECHNICAL),
            new Item("A database query has become slow as the data grew. How would you find out why and fix it?", QuestionCategory.PROBLEM_SOLVING),
            new Item("How would you design the data model for a simple online shop?", QuestionCategory.SYSTEM_DESIGN),
            new Item("How do you keep code readable and easy to change as a project grows?", QuestionCategory.TECHNICAL),
            new Item("Tell me about a technical trade-off you made on a project. What did you give up, and why?", QuestionCategory.PROBLEM_SOLVING));

    private InterviewQuestionBank() {
    }

    /** A warm-up, questions for the role, and a closing question, {@code count} in all. */
    static List<Item> forRole(String role, InterviewDifficulty difficulty, int count, Random random) {
        List<Item> middle = new ArrayList<>(forStream(role, difficulty));
        List<Item> general = new ArrayList<>(GENERAL_TECHNICAL);
        Collections.shuffle(general, random);
        middle.addAll(general);

        List<Item> items = new ArrayList<>();
        items.add(WARM_UP);
        items.addAll(middle.subList(0, Math.max(0, Math.min(middle.size(), count - 2))));
        items.add(CLOSING);
        return List.copyOf(items.subList(0, Math.min(items.size(), count)));
    }

    /** {@code count} distinct HR topics in a random order: a different interview every time. */
    static List<HrTopic> pickHrTopics(int count, Random random) {
        List<HrTopic> topics = new ArrayList<>(HR_TOPICS);
        Collections.shuffle(topics, random);
        return List.copyOf(topics.subList(0, Math.min(count, topics.size())));
    }

    /** A warm-up, then one question on each topic, each phrasing picked at random. */
    static List<Item> hrQuestions(List<HrTopic> topics, Random random) {
        List<Item> items = new ArrayList<>();
        items.add(new Item(HR_WARM_UPS.get(random.nextInt(HR_WARM_UPS.size())), QuestionCategory.BEHAVIORAL));
        for (HrTopic topic : topics) {
            items.add(new Item(topic.questions().get(random.nextInt(topic.questions().size())), QuestionCategory.BEHAVIORAL));
        }
        return List.copyOf(items);
    }

    private static List<Item> forStream(String stream, InterviewDifficulty difficulty) {
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
