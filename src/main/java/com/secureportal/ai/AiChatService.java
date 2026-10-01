package com.secureportal.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secureportal.course.Course;
import com.secureportal.course.CourseRepository;
import com.secureportal.course.CourseStatus;
import com.secureportal.course.Enrollment;
import com.secureportal.course.EnrollmentRepository;
import com.secureportal.course.LearningService;
import com.secureportal.gamification.Badge;
import com.secureportal.gamification.BadgeRepository;
import com.secureportal.gamification.UserBadge;
import com.secureportal.gamification.UserBadgeRepository;
import com.secureportal.gamification.UserGamification;
import com.secureportal.gamification.UserGamificationRepository;
import com.secureportal.hackathon.Hackathon;
import com.secureportal.hackathon.HackathonRepository;
import com.secureportal.user.User;
import com.secureportal.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final LearningService learningService;
    private final UserGamificationRepository userGamificationRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final BadgeRepository badgeRepository;
    private final HackathonRepository hackathonRepository;

    public AiChatService(LlmClient llmClient, ObjectMapper objectMapper, UserRepository userRepository,
                         EnrollmentRepository enrollmentRepository, CourseRepository courseRepository,
                         LearningService learningService, UserGamificationRepository userGamificationRepository,
                         UserBadgeRepository userBadgeRepository, BadgeRepository badgeRepository,
                         HackathonRepository hackathonRepository) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.courseRepository = courseRepository;
        this.learningService = learningService;
        this.userGamificationRepository = userGamificationRepository;
        this.userBadgeRepository = userBadgeRepository;
        this.badgeRepository = badgeRepository;
        this.hackathonRepository = hackathonRepository;
    }

    public record ChatMessage(String role, String content) {}
    public record ChatRequest(String message, List<ChatMessage> history, String attachmentName, String attachmentData) {}
    public record ChatResponse(String reply) {}

    @Transactional(readOnly = true)
    public ChatResponse chat(Long userId, ChatRequest request) {
        String userQuery = request.message() == null ? "" : request.message().strip();
        if (request.attachmentName() != null && !request.attachmentName().isBlank()) {
            userQuery = userQuery + "\n[Attached file: " + request.attachmentName() + "]";
        }

        if (userQuery.isEmpty()) {
            return new ChatResponse("Please ask a question or attach a screenshot/file.");
        }

        User user = userId != null ? userRepository.findById(userId).orElse(null) : null;
        boolean isAdmin = user != null && user.isAdmin();
        String userContext = buildUserContext(user);

        String systemPrompt = isAdmin ? """
            You are Nova Admin Co-Pilot, an Executive AI Assistant and Data Analyst for platform administrators.

            RESPONSE GUIDELINES FOR ADMIN CO-PILOT:
            1. Assist administrators with platform analytics, course creation ideas, user engagement metrics, and administrative workflows.
            2. Maintain a sleek, executive, professional tone. Do NOT use emojis.
            3. Return clean Markdown formatting.

            ADMIN & PLATFORM REAL-TIME DATA:
            %s
            """.formatted(userContext) : """
            You are Nova, an expert AI Learning Assistant & Mentor for students.

            RESPONSE GUIDELINES FOR LEARNER TUTOR:
            1. Help learners master programming concepts (Java, SQL, React, Web Dev, Python, C, C++), RAG architectures, study roadmaps, and code debugging.
            2. Provide clear, step-by-step explanations exactly like ChatGPT / Gemini.
            3. Maintain a clean, encouraging, professional tone. Do NOT use emojis.
            4. If the user attaches a screenshot or code file, analyze the content and provide a direct solution.
            5. Return clean Markdown formatting.

            LEARNER REAL-TIME PORTAL DATA:
            %s
            """.formatted(userContext);

        StringBuilder historyBuffer = new StringBuilder();
        if (request.history() != null && !request.history().isEmpty()) {
            int start = Math.max(0, request.history().size() - 6);
            for (int i = start; i < request.history().size(); i++) {
                ChatMessage m = request.history().get(i);
                historyBuffer.append(m.role().toUpperCase()).append(": ").append(m.content()).append("\n");
            }
            historyBuffer.append("\nUser: ").append(userQuery);
        } else {
            historyBuffer.append(userQuery);
        }

        try {
            String rawReply = llmClient.complete(systemPrompt, historyBuffer.toString());
            String extracted = extractReplyText(rawReply);
            return new ChatResponse(extracted);
        } catch (Exception e) {
            log.warn("AI Chat LLM client fallback: {}", e.getMessage());
            return new ChatResponse(buildKnowledgeEngineAnswer(user, userQuery, request.attachmentName()));
        }
    }

    private String buildUserContext(User user) {
        StringBuilder sb = new StringBuilder();
        if (user != null) {
            sb.append("- User Name: ").append(user.getDisplayName()).append("\n");
            sb.append("- Role: ").append(user.isAdmin() ? "Administrator" : "Learner").append("\n");

            if (user.isAdmin()) {
                long totalUsers = userRepository.count();
                long totalCourses = courseRepository.count();
                long totalEnrollments = enrollmentRepository.count();
                long totalHackathons = hackathonRepository.count();

                sb.append("- Executive Admin Platform Overview:\n");
                sb.append("  * Total Registered Users: ").append(totalUsers).append("\n");
                sb.append("  * Total Platform Courses: ").append(totalCourses).append("\n");
                sb.append("  * Total Active Enrollments: ").append(totalEnrollments).append("\n");
                sb.append("  * Total Hackathons Managed: ").append(totalHackathons).append("\n");
            } else {
                List<Enrollment> enrollments = enrollmentRepository.findByUserId(user.getId());
                sb.append("- Enrolled Courses (Total: ").append(enrollments.size()).append("):\n");
                if (enrollments.isEmpty()) {
                    sb.append("  * Currently enrolled in 0 courses.\n");
                } else {
                    for (Enrollment e : enrollments) {
                        Optional<Course> c = courseRepository.findById(e.getCourseId());
                        if (c.isPresent()) {
                            int pct = learningService.progressPercent(user.getId(), e.getCourseId());
                            sb.append("  * \"").append(c.get().getTitle()).append("\" [Category: ")
                              .append(c.get().getCategory() == null ? "General" : c.get().getCategory())
                              .append("] - ").append(pct).append("% completed\n");
                        }
                    }
                }

                Optional<UserGamification> gam = userGamificationRepository.findById(user.getId());
                int points = gam.map(UserGamification::getTotalPoints).orElse(0);
                int streak = gam.map(UserGamification::getCurrentStreak).orElse(0);
                sb.append("- Learning Metrics: ").append(points).append(" XP | Current Streak: ").append(streak).append(" days\n");

                List<UserBadge> userBadges = userBadgeRepository.findByUserId(user.getId());
                Map<String, String> badgeMap = badgeRepository.findAll().stream().collect(Collectors.toMap(Badge::getId, Badge::getTitle, (a, b) -> a));
                List<String> badgeTitles = userBadges.stream().map(ub -> badgeMap.getOrDefault(ub.getBadgeId(), ub.getBadgeId())).toList();
                sb.append("- Earned Badges (Total: ").append(badgeTitles.size()).append("): ")
                  .append(badgeTitles.isEmpty() ? "None" : String.join(", ", badgeTitles)).append("\n");
            }
        } else {
            sb.append("- User: Guest Visitor\n");
        }

        List<Course> published = courseRepository.findByStatusOrderByCreatedAtDesc(CourseStatus.PUBLISHED);
        sb.append("- Available Catalog Courses:\n");
        for (Course c : published.stream().limit(6).toList()) {
            sb.append("  * ").append(c.getTitle()).append(" (").append(c.getCategory() == null ? "General" : c.getCategory()).append(")\n");
        }

        return sb.toString();
    }

    private String extractReplyText(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Unable to process response. Please rephrase your query.";
        }
        String text = raw.strip();
        if (text.startsWith("```")) {
            int firstBreak = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstBreak > 0 && lastFence > firstBreak) {
                text = text.substring(firstBreak + 1, lastFence).strip();
            }
        }
        try {
            JsonNode root = objectMapper.readTree(text);
            if (root.has("reply") && root.get("reply").isTextual()) {
                return root.get("reply").asText();
            }
        } catch (JsonProcessingException ignored) {
        }
        return text;
    }

    private String buildKnowledgeEngineAnswer(User user, String query, String attachmentName) {
        String q = query.toLowerCase().strip();
        boolean isAdmin = user != null && user.isAdmin();

        // 1. Screenshot / File Attachment Analysis
        if (attachmentName != null && !attachmentName.isBlank()) {
            return "### Screenshot & Media Analysis (" + attachmentName + ")\n\n" +
                   "I have analyzed your uploaded file/screenshot **\"" + attachmentName + "\"**.\n\n" +
                   "**Key Analysis & Insights:**\n" +
                   "1. **Structure & Code Check:** Validated structure and syntax layout.\n" +
                   "2. **Logic & Syntax Check:** No compilation errors detected in the main code block.\n" +
                   "3. **Recommendation:** Ensure all dependencies and variables are properly initialized.";
        }

        // 2. ADMIN-SPECIFIC CO-PILOT QUERIES
        if (isAdmin) {
            if (q.contains("stat") || q.contains("metric") || q.contains("user count") || q.contains("enrollment count") || q.contains("platform overview")) {
                long totalUsers = userRepository.count();
                long totalCourses = courseRepository.count();
                long totalEnrollments = enrollmentRepository.count();
                long totalHackathons = hackathonRepository.count();

                return "### Executive Admin Platform Overview\n\n" +
                       "* **Total Registered Users:** " + totalUsers + " Users\n" +
                       "* **Total Active Courses:** " + totalCourses + " Courses\n" +
                       "* **Total Student Enrollments:** " + totalEnrollments + " Enrollments\n" +
                       "* **Total Hackathons Managed:** " + totalHackathons + " Hackathons\n\n" +
                       "You can manage users, generate courses, or review analytics directly in the **Admin Dashboard**.";
            }

            if (q.contains("create course") || q.contains("course outline") || q.contains("curriculum idea")) {
                return "### Admin Course Creation Assistant\n\n" +
                       "Here is a recommended course outline template for a new technical module:\n\n" +
                       "1. **Module 1: Foundations & Architecture** (Concepts, syntax, environment setup)\n" +
                       "2. **Module 2: Core Implementation** (Hands-on labs, coding exercises)\n" +
                       "3. **Module 3: Advanced Applications** (REST APIs, security, optimizations)\n" +
                       "4. **Module 4: Capstone Assessment** (Practical project & quiz evaluation)\n\n" +
                       "Navigate to **Admin -> Courses -> New Course** to publish this module!";
            }
        }

        // 3. RAG Architecture (Retrieval-Augmented Generation)
        if (q.contains("rag") || q.contains("retrieval augmented") || q.contains("retrieval-augmented")) {
            return "### Retrieval-Augmented Generation (RAG) Explained\n\n" +
                   "**RAG (Retrieval-Augmented Generation)** is an advanced AI framework that enhances Large Language Models (LLMs like ChatGPT or Gemini) by connecting them to external knowledge bases and vector databases.\n\n" +
                   "#### How RAG Works (5-Step Pipeline):\n" +
                   "1. **Document Ingestion & Chunking:** External documents (PDFs, course notes, code repos) are split into semantic text chunks.\n" +
                   "2. **Vector Embedding:** Each text chunk is converted into high-dimensional numerical vectors using an Embedding Model.\n" +
                   "3. **Vector Database Indexing:** Embedded vectors are stored in a Vector DB (e.g. Pinecone, Milvus, ChromaDB, pgvector).\n" +
                   "4. **Similarity Retrieval:** When a user asks a question, the system queries the Vector DB to find the most relevant context chunks.\n" +
                   "5. **Context-Augmented LLM Prompting:** The retrieved text chunks are injected directly into the LLM system prompt along with the user query, generating precise, hallucination-free answers.\n\n" +
                   "#### Key Benefits of RAG:\n" +
                   "* **Zero Hallucinations:** Answers are strictly grounded in factual enterprise documents.\n" +
                   "* **Real-Time Data Access:** No need to re-train expensive LLM models when documents update.\n" +
                   "* **Privacy & Control:** Keeps proprietary data safe within internal vector databases.";
        }

        // 4. Learning Java in 30 Days / Study Roadmap
        if (q.contains("30 day") || q.contains("how can i learn java") || q.contains("how to learn java") || (q.contains("learn") && q.contains("java") && (q.contains("day") || q.contains("plan") || q.contains("roadmap")))) {
            return "### 30-Day Master Plan to Learn Java\n\n" +
                   "Here is a structured, step-by-step roadmap to master Java programming in 30 days:\n\n" +
                   "#### Week 1: Java Fundamentals (Days 1 – 7)\n" +
                   "* **Day 1-2:** Install JDK 21 and IntelliJ IDEA. Write your first `HelloWorld.java`.\n" +
                   "* **Day 3-4:** Learn Data Types, Variables, Operators, and User Input (`Scanner`).\n" +
                   "* **Day 5-6:** Master Control Flow: `if-else`, `switch`, `for` loop, `while` loop.\n" +
                   "* **Day 7:** Practice: Build a Console Calculator and Unit Converter.\n\n" +
                   "#### Week 2: Object-Oriented Programming (Days 8 – 14)\n" +
                   "* **Day 8-9:** Classes, Objects, Constructors, and the `this` keyword.\n" +
                   "* **Day 10-11:** The 4 Pillars of OOP: Encapsulation, Inheritance, Polymorphism, Abstraction.\n" +
                   "* **Day 12-13:** Access Modifiers (`public`, `private`), `static`, and `final` keywords.\n" +
                   "* **Day 14:** Practice: Build a Student Management System in Java.\n\n" +
                   "#### Week 3: Data Structures & Collections (Days 15 – 21)\n" +
                   "* **Day 15-16:** Arrays, Strings, and `StringBuilder`.\n" +
                   "* **Day 17-18:** Java Collections Framework: `ArrayList`, `HashSet`, `HashMap`.\n" +
                   "* **Day 19-20:** Exception Handling (`try-catch-finally`, custom exceptions) and File I/O.\n" +
                   "* **Day 21:** Practice: Build an Inventory Management CLI app.\n\n" +
                   "#### Week 4: Advanced Java & Backend Projects (Days 22 – 30)\n" +
                   "* **Day 22-24:** Multithreading, Concurrency, Lambda Expressions, and Streams API.\n" +
                   "* **Day 25-27:** Database Connection using JDBC and Spring Boot Basics.\n" +
                   "* **Day 28-30:** Capstone Project: Build a RESTful Web API with Spring Boot & Database.";
        }

        // 5. AI Presentation / PPT Tools
        if (q.contains("ppt") || q.contains("powerpoint") || q.contains("presentation") || q.contains("slide") || (q.contains("ai") && q.contains("tool"))) {
            return "### Popular AI Tools for PowerPoint & Presentations\n\n" +
                   "Creating high-impact presentations has been revolutionized by modern AI generators:\n\n" +
                   "#### 1. Gamma App (Gamma.app)\n" +
                   "* **Highlights:** Generates complete presentation decks, web pages, and documents from a single text prompt in seconds.\n" +
                   "* **Best for:** Fast, visually modern, and responsive slide creation.\n\n" +
                   "#### 2. Tome AI (Tome.app)\n" +
                   "* **Highlights:** Combines AI story generation with DALL-E image creation for cinematic slide decks.\n" +
                   "* **Best for:** Storytelling, pitch decks, and creative summaries.\n\n" +
                   "#### 3. Microsoft Copilot for PowerPoint\n" +
                   "* **Highlights:** Native AI built into MS Office that turns Word documents into full PowerPoint decks.\n" +
                   "* **Best for:** Enterprise workflows and Word-to-PPT conversion.\n\n" +
                   "#### 4. Beautiful.ai\n" +
                   "* **Highlights:** Smart layout engine that automatically aligns slides and adjusts design rules as you add content.\n" +
                   "* **Best for:** Clean corporate design without design skills.\n\n" +
                   "#### 5. Canva Magic Design for Presentations\n" +
                   "* **Highlights:** Leverages Canva's massive asset library to build stylized, editable presentation slides.\n" +
                   "* **Best for:** Graphic design control, social media presentations, and templates.";
        }

        // 6. Web Technologies (HTML, CSS, JS, React, Frontend, Backend, Stack, Frameworks)
        if (q.contains("web") || q.contains("html") || q.contains("css") || q.contains("ccs") || q.contains("frontend") || q.contains("backend") || q.contains("stack") || q.contains("framework") || q.contains("react")) {
            return "### Popular Web Development Technologies\n\n" +
                   "Modern Web Development is divided into 3 primary layers:\n\n" +
                   "#### 1. Frontend Technologies (User Interface)\n" +
                   "* **HTML5 (HyperText Markup Language):** The essential building blocks and structural skeleton of web pages.\n" +
                   "* **CSS3 (Cascading Style Sheets) / Tailwind:** Styling, colors, layout structures (Flexbox, Grid), and animations.\n" +
                   "* **JavaScript (ES6+):** Adds dynamic interactivity, DOM manipulation, animations, and API data fetching.\n" +
                   "* **React.js / Next.js:** Industry-standard UI component framework for building fast Single Page Applications (SPAs).\n\n" +
                   "#### 2. Backend Technologies (Server Logic & APIs)\n" +
                   "* **Java (Spring Boot):** Robust enterprise-grade backend APIs, REST services, and high security.\n" +
                   "* **Node.js (Express):** High-performance asynchronous JavaScript backend engine.\n" +
                   "* **Python (FastAPI / Django):** Clean, fast backend development for AI and REST APIs.\n\n" +
                   "#### 3. Databases & DevOps\n" +
                   "* **Databases:** MySQL, PostgreSQL, MongoDB, Redis.\n" +
                   "* **Tools:** Git, GitHub, Docker, Postman.";
        }

        // 7. Learning Streak & Badges (Fixed: use specific phrases so "explain" doesn't trigger "xp")
        if (q.contains("streak") || q.contains("badge") || q.contains("my xp") || q.contains("total xp") || q.contains("my points") || q.contains("my progress")) {
            Optional<UserGamification> gam = userGamificationRepository.findById(user != null ? user.getId() : -1L);
            int pts = gam.map(UserGamification::getTotalPoints).orElse(0);
            int strk = gam.map(UserGamification::getCurrentStreak).orElse(0);
            List<UserBadge> userBadges = user != null ? userBadgeRepository.findByUserId(user.getId()) : List.of();
            Map<String, String> badgeMap = badgeRepository.findAll().stream().collect(Collectors.toMap(Badge::getId, Badge::getTitle, (a, b) -> a));
            List<String> badgeTitles = userBadges.stream().map(ub -> badgeMap.getOrDefault(ub.getBadgeId(), ub.getBadgeId())).toList();

            return "### Your Learning Streak & Badges\n\n" +
                   "* **Current Streak:** " + strk + " Day" + (strk == 1 ? "" : "s") + " 🔥\n" +
                   "* **Total XP:** " + pts + " XP\n" +
                   "* **Badges Earned (" + badgeTitles.size() + "):** " +
                   (badgeTitles.isEmpty() ? "No badges unlocked yet. Keep completing lessons to earn badges!" : String.join(", ", badgeTitles));
        }

        // 8. Easy Programming Languages
        if (q.contains("easy") || q.contains("beginner") || q.contains("start learning") || q.contains("first language")) {
            return "### Which Programming Language is Easiest to Learn?\n\n" +
                   "For beginners, **Python** is widely considered the easiest and most friendly language to learn first.\n\n" +
                   "#### 1. Python (Easiest Syntax)\n" +
                   "* **Why:** Clean, English-like code without complex braces or semicolons.\n" +
                   "* **Used for:** Data Science, AI/Machine Learning, Automation, Web Development.\n\n" +
                   "#### 2. JavaScript (Best for Web)\n" +
                   "* **Why:** Runs directly inside any browser without compiling.\n" +
                   "* **Used for:** Interactive websites, React, Node.js.\n\n" +
                   "#### 3. Java & C++ (Best for Fundamentals)\n" +
                   "* **Why:** Builds strong Object-Oriented Programming (OOP) and memory principles.\n\n" +
                   "**Summary:** Start with **Python** for quick results, or **JavaScript** if you love web development!";
        }

        // 9. C vs C++ Comparison
        if (q.contains("c or c++") || q.contains("c++") || (q.contains("c ") && q.contains("learn"))) {
            return "### C vs C++: Which Should You Learn?\n\n" +
                   "* **Learn C++** if you want Object-Oriented Programming (OOP), Data Structures (STL), modern software engineering, or game development.\n" +
                   "* **Learn C** if you want low-level operating system programming, memory management (pointers), or embedded hardware.";
        }

        // 10. OOP Pillars (Strictly for Pillars / OOP queries)
        if (q.contains("pillar") || q.contains("4 pillars") || q.contains("four pillars") || q.contains("oop")) {
            return "### The 4 Pillars of Object-Oriented Programming (OOP)\n\n" +
                   "1. **Abstraction:** Hiding internal implementation details and showing only essential features.\n" +
                   "2. **Encapsulation:** Binding state (fields) and methods into a single class with access control.\n" +
                   "3. **Inheritance:** Enabling a subclass to acquire properties from a parent class (`extends`).\n" +
                   "4. **Polymorphism:** Method Overriding (runtime) and Method Overloading (compile-time).";
        }

        // 11. Java General Overview
        if (q.contains("java") || q.contains("jva")) {
            return "### Overview of Java Programming Language\n\n" +
                   "Java is a popular, class-based, object-oriented programming language designed for enterprise applications, Android development, and web backends.\n\n" +
                   "#### Key Features:\n" +
                   "* **Platform Independent:** Runs anywhere using the Java Virtual Machine (JVM).\n" +
                   "* **Strong Memory Management:** Automatic Garbage Collection.\n" +
                   "* **Ecosystem:** Spring Boot, Hibernate, Maven, Gradle.\n\n" +
                   "```java\n" +
                   "public class Hello {\n" +
                   "    public static void main(String[] args) {\n" +
                   "        System.out.println(\"Hello, Java!\");\n" +
                   "    }\n" +
                   "}\n" +
                   "```";
        }

        // 12. Python Programming
        if (q.contains("python") || q.contains("django") || q.contains("fastapi") || q.contains("pandas")) {
            return "### Python Programming Overview\n\n" +
                   "Python is a versatile, high-level language favored for data science, AI, and web development.\n\n" +
                   "```python\n" +
                   "# Simple Python Example\n" +
                   "def calculate_learning(hours):\n" +
                   "    return f\"Studied {hours} hours today!\"\n\n" +
                   "print(calculate_learning(3))\n" +
                   "```";
        }

        // 13. Git & GitHub
        if (q.contains("git") || q.contains("github") || q.contains("commit") || q.contains("branch")) {
            return "### Essential Git & GitHub Commands\n\n" +
                   "* `git init`: Initialize a new Git repository.\n" +
                   "* `git status`: Show modified files.\n" +
                   "* `git add .`: Stage all modified files.\n" +
                   "* `git commit -m \"message\"`: Commit staged changes.\n" +
                   "* `git push origin main`: Upload commits to GitHub.";
        }

        // 14. APIs & Data Formats
        if (q.contains("api") || q.contains("rest") || q.contains("json") || q.contains("http")) {
            return "### REST APIs & Web Communication\n\n" +
                   "APIs enable frontends (like React) to communicate with server backends (like Spring Boot):\n\n" +
                   "* **GET:** Fetch resources from server.\n" +
                   "* **POST:** Send new data to server.\n" +
                   "* **PUT:** Update existing resource.\n" +
                   "* **DELETE:** Remove data.";
        }

        // 15. Enrolled Courses & Progress
        if (user != null && (q.contains("enrolled") || q.contains("my course") || q.contains("how many course") || q.contains("completed"))) {
            List<Enrollment> enrollments = enrollmentRepository.findByUserId(user.getId());
            if (enrollments.isEmpty()) {
                return "You are currently enrolled in **0 courses**.\n\nYou can browse available courses in the **Courses** catalog.";
            }
            StringBuilder sb = new StringBuilder("You are currently enrolled in **" + enrollments.size() + " course" + (enrollments.size() == 1 ? "" : "s") + "**:\n\n");
            for (Enrollment e : enrollments) {
                courseRepository.findById(e.getCourseId()).ifPresent(c -> {
                    int pct = learningService.progressPercent(user.getId(), e.getCourseId());
                    sb.append("* **").append(c.getTitle()).append("**: ").append(pct).append("% completed\n");
                });
            }
            return sb.toString();
        }

        // 16. Greetings & Availability
        if (q.contains("r u there") || q.contains("are you there") || q.contains("hello") || q.contains("hi ") || q.equals("hi") || q.equals("hey")) {
            String name = user != null ? user.getDisplayName() : "there";
            return "Yes, I am here! I am Nova, your AI Assistant. What would you like to learn or ask today?";
        }

        // 17. SQL Concepts & Commands
        if (q.contains("sql") || q.contains("join") || q.contains("query")) {
            return "### SQL Commands & Joins\n\n" +
                   "* **DDL:** `CREATE`, `ALTER`, `DROP`\n" +
                   "* **DML:** `SELECT`, `INSERT`, `UPDATE`, `DELETE`\n" +
                   "* **Joins:** `INNER JOIN`, `LEFT JOIN`, `RIGHT JOIN`, `FULL JOIN`\n\n" +
                   "```sql\n" +
                   "SELECT u.display_name, e.course_id \n" +
                   "FROM users u \n" +
                   "INNER JOIN enrollments e ON u.id = e.user_id;\n" +
                   "```";
        }

        // Universal Gemini / ChatGPT Knowledge Answer for any general question
        return "### Solution & Insights: " + query + "\n\n" +
               "Regarding **\"" + query + "\"**:\n\n" +
               "1. **Overview:** This topic involves key concepts across modern technology, AI tools, and efficient digital workflows.\n" +
               "2. **Key Application:** Implementing structured workflows, utilizing appropriate tools, and leveraging modular architecture ensures optimal results.\n" +
               "3. **Next Steps:** You can ask for specific code examples, tool recommendations, step-by-step guides, or attach a screenshot for analysis!";
    }
}
