# GradientNovaAI - Complete Chatbot & Conversational AI Documentation

This document provides a comprehensive technical and functional list of all **Chatbots, AI Assistants, and Conversational Engines** implemented across the GradientNovaAI Learning Portal for documentation and reporting purposes.

---

## Executive Overview of Implemented AI Chatbots

| Chatbot / Assistant Name | Primary Audience | Core Purpose & Functionality | Key Capabilities |
| :--- | :--- | :--- | :--- |
| **Nova AI Assistant (Learner Mode)** | Enrolled Students / Learners | Interactive AI Tutor, Mentor, & Coding Companion | Portal Context (XP, Streak, Enrolled Courses), 30-Day Roadmaps, RAG explanations, Code Debugging, File/Screenshot paste analysis, Speech-to-Text input. |
| **Nova Admin Co-Pilot (Admin Mode)** | Platform Administrators | Executive Assistant & Data Analytics Companion | Real-time platform metrics (User count, active enrollments, course totals), Course outline builder, Administrative workflow assistant, Clean executive formatting. |
| **AI Technical Mock Interviewer** | Job Seekers / Learners | Conversational Tech Interview Simulation | Multi-turn Q&A, Dynamic context-aware follow-up questions, Speech-to-Text voice answers, Rubric evaluation (10pt scale), Full readiness report. |
| **AI Resume Feedback Engine** | Job Seekers / Learners | Resume Analysis & ATS Optimization | Automated resume parsing, skill matching, ATS score computation, and actionable resume enhancement recommendations. |
| **AI Assessment & Question Generator** | Course Creators / Admins | Curriculum Content & Quiz Generation | Automated AI generation of multiple-choice and conceptual questions for course modules and practice quizzes. |
| **Peer-to-Peer Messaging Dock** | All Platform Users | Real-Time User Networking & Chat | Floating dock UI, connection-based direct messaging, active status, message history, and unread notification indicators. |

---

## 1. Nova Floating AI Chatbot Widget

**Frontend Component:** `frontend/src/components/AiChatWidget.jsx`  
**Backend API:** `com.secureportal.api.AiChatApiController` (`POST /api/ai/chat`)  
**Backend Service:** `com.secureportal.ai.AiChatService`

### A. Dual Personality Modes

#### 1. Nova Learner Tutor Mode
* **Trigger:** Automatically activated when logged in as a **Learner / Student**.
* **System Prompt & Persona:** Friendly, encouraging, expert technical mentor.
* **Context Integration:**
  * Pulls user's real-time portal metrics: Enrolled courses, completion percentages, XP points, daily streak count, and earned badges.
* **Core Use Cases:**
  * Programming concepts (Java, Python, SQL, React, C, C++).
  * Architecture queries (e.g., RAG - Retrieval-Augmented Generation).
  * Structured study roadmaps (e.g., 30-Day Master Plan to Learn Java).
  * Web technology stack breakdowns (Frontend vs Backend vs Databases).
  * Presentation & AI presentation tool recommendations (Gamma, Tome, Beautiful.ai).

#### 2. Nova Admin Co-Pilot Mode
* **Trigger:** Automatically activated when logged in as an **Administrator**.
* **System Prompt & Persona:** Sleek, professional executive data analyst & co-pilot. No emojis.
* **Context Integration:**
  * Pulls real-time administrative metrics: Total registered users, total active courses, total student enrollments, and total managed hackathons.
* **Core Use Cases:**
  * Platform overview analytics & metrics reporting.
  * Course creation suggestions & curriculum outline generation templates.
  * Hackathon summaries and admin decision support.

---

### B. Interactive Capabilities & UI Features

* **ChatGPT-Style Persistent Chat History:**
  * Local storage persistence (`nova_learner_sessions` & `nova_admin_sessions`).
  * Thread switching, new conversation creation, single thread deletion, and bulk clear history option.
* **Multimodal Attachments & Clipboard Screenshot Paste:**
  * Support for attaching code files (`.java`, `.py`, `.sql`, `.js`, `.txt`, `.json`, `.md`) or images.
  * Direct `Ctrl+V` screenshot pasting into the input box.
* **Hands-Free Speech-to-Text (Voice Input):**
  * Microphone toggle powered by Web Speech API for voice-driven prompt entry.
* **Markdown & Code Syntax Highlighting:**
  * Custom Markdown parser supporting code blocks with language tags, bold text, inline code, headings, lists, and spacing.
* **Intelligent Fallback Knowledge Engine:**
  * Built-in offline knowledge fallback guaranteeing deterministic, high-quality answers for technical, portal, and admin queries even if external LLM endpoints are unreachable.

---

## 2. AI Technical Mock Interviewer Chatbot

**Frontend Components:** `frontend/src/pages/InterviewSession.jsx`, `frontend/src/components/interview/VoiceAnswer.jsx`  
**Backend Controllers:** `com.secureportal.api.MockInterviewApiController`, `com.secureportal.api.VoiceApiController`

### Key Features & Workflow:
1. **Interactive Session Stage:** Calming, full-width session layout showing time elapsed, progress bar, and topic goals.
2. **Adaptive Dynamic Questioning:** Asks technical questions sequentially based on candidate track (e.g., Full-Stack, Java Backend).
3. **Context-Aware Follow-Up Generation:** Generates follow-up questions tailored to the candidate's specific prior answer.
4. **Voice Answer Audio Recorder:** Speech-to-text recording integration allowing candidates to speak their response.
5. **Real-Time Rubric Feedback:**
   * Instant scoring out of 10.
   * Feedback split into **Went well** (strengths), **Work on** (improvements), and **Ideal Answer**.
6. **Final AI Readiness Report:**
   * Overall Readiness Percentage (e.g., Ready, Needs Practice).
   * Rubric averages across Technical Accuracy, Problem Solving, Communication, and Completeness.
   * Highlighted **Top Fix** recommendation and direct link to relevant platform courses.

---

## 3. AI Resume Evaluator & Feedback Bot

**Backend Controller:** `com.secureportal.api.ResumeApiController`

### Key Features:
* **Automated Document Parsing:** Parses uploaded PDFs and text resumes.
* **ATS Compatibility Evaluation:** Computes ATS readiness score and formatting checks.
* **Skills Gap Analysis:** Matches resume keywords against job track requirements and provides concrete suggestions to improve layout and content.

---

## 4. AI Quiz & Question Generator Service

**Backend Service:** `com.secureportal.quiz.QuestionGenerationService`  
**Backend Controller:** `com.secureportal.api.AdminQuestionApiController`

### Key Features:
* **Automated Question Bank Creation:** Generates single-choice, multiple-choice, and conceptual questions for course assessments based on custom topics and difficulty levels.
* **Structured Output:** Formats questions with correct answers, distractors, and explanations.

---

## 5. LinkedIn-Style Real-Time Floating Dock Messaging

**Frontend Pages/Components:** `frontend/src/pages/Network.jsx`, `frontend/src/components/Nav.jsx`  
**Backend Services:** `com.secureportal.api.ConnectionApiController`, `com.secureportal.api.MessageApiController`

### Key Features:
* **Peer-to-Peer Conversational Dock:** Floating bottom dock UI enabling connected learners to chat directly.
* **Connection Lifecycle:** Connection request creation, accept/reject workflows, and suggestions widget ("People You May Know").
* **Message Delivery & Unread Counts:** Real-time thread history, unread message badges, and active chat window management.

---

## Technical Architecture & API Summary

```
                       ┌───────────────────────────────────────────┐
                       │           React Frontend UI               │
                       └─────┬───────────────────────────────┬─────┘
                             │                               │
            ┌────────────────┴──────────────┐   ┌────────────┴─────────────────┐
            │ Nova Floating AI Chat Widget  │   │  AI Technical Mock Interview │
            │ (Learner Tutor / Admin Pilot) │   │  & Speech-to-Text Interface  │
            └────────────────┬──────────────┘   └────────────┬─────────────────┘
                             │                               │
                             ▼                               ▼
                 POST /api/ai/chat              POST /api/interview/{id}/answer
                             │                               │
                       ┌─────┴───────────────────────────────┴─────┐
                       │          Spring Boot Backend              │
                       │           (SecurePortal REST)             │
                       └─────┬───────────────────────────────┬─────┘
                             │                               │
            ┌────────────────┴──────────────┐   ┌────────────┴─────────────────┐
            │ AiChatService & LlmClient     │   │ MockInterviewService & Voice │
            │ + Real-Time Context Extractor │   │ + Rubric Assessment Engine   │
            └───────────────────────────────┘   └──────────────────────────────┘
```

---

## Summary for Documentation

All AI chatbot systems in GradientNovaAI are fully integrated into both the frontend React UI and Spring Boot backend. They feature robust real-time context ingestion, multi-turn history tracking, multimodal file handling, voice inputs, and fallback knowledge engines for seamless operation.
