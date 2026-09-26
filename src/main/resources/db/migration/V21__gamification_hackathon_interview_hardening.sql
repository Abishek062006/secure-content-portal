-- V21: harden the gamification, hackathon and mock-interview tables.
--
--  * point_transactions gets a dedupe_key so an award can only ever be given once per learner and source (a lesson, a quiz,
--    a course, a day, a hackathon, an interview), even under concurrent requests
--  * the tables from V19 get what the rest of the schema has: foreign keys that delete with their owner, indexes on the
--    columns they are queried by, and DATETIME(6) instead of second-precision TIMESTAMP
--  * demo rows that earlier code inserted on startup are removed (fake interview sessions attributed to user 1, and made-up
--    hackathons naming real companies)

-- TIMESTAMP -> DATETIME converts using the session time zone; the app stores UTC.
SET time_zone = '+00:00';

-- ---- 1. Remove the demo data ---------------------------------------------------------------------------------------

DELETE q FROM mock_interview_questions q
    JOIN mock_interview_sessions s ON s.id = q.session_id
WHERE s.user_id = 1 AND s.summary_feedback IN (
    'Exceptional deep learning and vector database architectural understanding. Strong candidate for Principal AI Architect.',
    'Solid understanding of web protocols, RESTful principles, and React state management.',
    'Good cloud architecture knowledge. Strengthen multi-region failover and distributed consensus mechanisms.');

DELETE FROM mock_interview_sessions
WHERE user_id = 1 AND summary_feedback IN (
    'Exceptional deep learning and vector database architectural understanding. Strong candidate for Principal AI Architect.',
    'Solid understanding of web protocols, RESTful principles, and React state management.',
    'Good cloud architecture knowledge. Strengthen multi-region failover and distributed consensus mechanisms.');

DELETE h FROM hackathons h
    LEFT JOIN hackathon_registrations r ON r.hackathon_id = h.id
WHERE r.id IS NULL AND h.title IN (
    'Global Generative AI & Agentic Code Sprint 2026',
    'Cloud-Native Distributed Systems Hackathon',
    'Full-Stack Web3 & SaaS Challenge',
    'NextGen UI/UX Designathon');

-- ---- 2. Orphans (nothing enforced these relations before), then the constraints ----------------------------------------

DELETE FROM hackathon_registrations
WHERE hackathon_id NOT IN (SELECT id FROM hackathons) OR user_id NOT IN (SELECT id FROM users);
DELETE FROM mock_interview_sessions WHERE user_id NOT IN (SELECT id FROM users);
DELETE FROM mock_interview_questions WHERE session_id NOT IN (SELECT id FROM mock_interview_sessions);

UPDATE hackathons SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL;
UPDATE hackathons SET updated_at = CURRENT_TIMESTAMP WHERE updated_at IS NULL;
UPDATE hackathons SET featured = FALSE WHERE featured IS NULL;
UPDATE hackathons SET points_reward = 25 WHERE points_reward IS NULL;
UPDATE hackathon_registrations SET registered_at = CURRENT_TIMESTAMP WHERE registered_at IS NULL;
UPDATE mock_interview_sessions SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL;

ALTER TABLE hackathons
    MODIFY registration_deadline DATETIME(6) NULL,
    MODIFY event_start_date DATETIME(6) NULL,
    MODIFY event_end_date DATETIME(6) NULL,
    MODIFY featured BOOLEAN NOT NULL DEFAULT FALSE,
    MODIFY points_reward INT NOT NULL DEFAULT 25,
    MODIFY created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    MODIFY updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);

ALTER TABLE hackathon_registrations
    MODIFY registered_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD CONSTRAINT hackathon_registrations_hackathon_fk FOREIGN KEY (hackathon_id) REFERENCES hackathons (id) ON DELETE CASCADE,
    ADD CONSTRAINT hackathon_registrations_user_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;
CREATE INDEX hackathon_registrations_user_idx ON hackathon_registrations (user_id);

ALTER TABLE mock_interview_sessions
    MODIFY created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    MODIFY completed_at DATETIME(6) NULL,
    ADD CONSTRAINT mock_interview_sessions_user_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;
CREATE INDEX mock_interview_sessions_user_idx ON mock_interview_sessions (user_id, created_at);
CREATE INDEX mock_interview_sessions_status_idx ON mock_interview_sessions (status, created_at);

ALTER TABLE mock_interview_questions
    MODIFY answered_at DATETIME(6) NULL,
    ADD CONSTRAINT mock_interview_questions_session_fk FOREIGN KEY (session_id) REFERENCES mock_interview_sessions (id) ON DELETE CASCADE,
    ADD CONSTRAINT uk_mock_question_index UNIQUE (session_id, question_index);

-- ---- 3. One award per learner and source --------------------------------------------------------------------------------

ALTER TABLE point_transactions ADD COLUMN dedupe_key VARCHAR(140) NULL;

-- Admin adjustments stored the admin's email as their source; it doesn't belong in a table learners' history is read from.
UPDATE point_transactions SET source_id = NULL WHERE action_type = 'MANUAL_ADJUSTMENT';

-- Existing awards keep the first row per learner and source (any repeats came from the retake exploit and stay as history).
UPDATE point_transactions pt
    JOIN (SELECT MIN(id) AS id FROM point_transactions WHERE source_id IS NOT NULL GROUP BY user_id, action_type, source_id) first_award
        ON first_award.id = pt.id
SET pt.dedupe_key = CONCAT(pt.action_type, ':', pt.source_id);

ALTER TABLE point_transactions ADD CONSTRAINT uk_point_tx_dedupe UNIQUE (user_id, dedupe_key);
CREATE INDEX idx_point_tx_user_action ON point_transactions (user_id, action_type);

-- Interview XP is a rule admins can tune, like the others.
INSERT IGNORE INTO point_rules (id, action_type, display_name, points, description)
VALUES ('MOCK_INTERVIEW_COMPLETE', 'MOCK_INTERVIEW_COMPLETE', 'Mock Interview Completed', 50,
        'Awarded once for each completed AI mock interview');
