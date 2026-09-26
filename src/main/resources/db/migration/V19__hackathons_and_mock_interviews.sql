-- Flyway Migration V19: Hackathons and AI Mock Interviews schema
CREATE TABLE IF NOT EXISTS hackathons (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    organizer VARCHAR(255) DEFAULT 'GradientNova & Global Partners',
    description TEXT,
    banner_url VARCHAR(255),
    stream VARCHAR(255) NOT NULL,
    mode VARCHAR(255) NOT NULL,
    location VARCHAR(255),
    prize_pool VARCHAR(255),
    registration_url VARCHAR(1000) NOT NULL,
    registration_deadline TIMESTAMP NULL,
    event_start_date TIMESTAMP NULL,
    event_end_date TIMESTAMP NULL,
    featured BOOLEAN DEFAULT FALSE,
    status VARCHAR(255) NOT NULL,
    points_reward INT DEFAULT 25,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS hackathon_registrations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    hackathon_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    points_awarded INT DEFAULT 25,
    registered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_hackathon_user UNIQUE (hackathon_id, user_id)
);

CREATE TABLE IF NOT EXISTS mock_interview_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    track VARCHAR(255) NOT NULL,
    stream VARCHAR(255) NOT NULL,
    difficulty VARCHAR(255) NOT NULL,
    total_questions INT DEFAULT 3,
    current_question_index INT DEFAULT 0,
    overall_score INT DEFAULT 0,
    readiness_level VARCHAR(255) DEFAULT 'PENDING',
    summary_feedback TEXT,
    status VARCHAR(255) NOT NULL DEFAULT 'IN_PROGRESS',
    xp_earned INT DEFAULT 50,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL
);

CREATE TABLE IF NOT EXISTS mock_interview_questions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    question_index INT NOT NULL,
    question_text TEXT NOT NULL,
    category VARCHAR(255),
    learner_answer TEXT,
    ai_feedback TEXT,
    key_strengths TEXT,
    areas_to_improve TEXT,
    ideal_answer TEXT,
    score INT DEFAULT -1,
    answered_at TIMESTAMP NULL
);
