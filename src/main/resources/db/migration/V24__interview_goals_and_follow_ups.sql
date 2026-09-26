-- V24: interviews start from a goal (skills, a job description or a course) and can ask a follow-up.
--
--  * sessions remember what they were built from, so a learner can practise again with the same setup
--  * a question can be a follow-up to another one, and answers are scored on four parts as well as overall
--  * a finished interview records the one thing to work on first

ALTER TABLE mock_interview_sessions
    ADD COLUMN source VARCHAR(12) NOT NULL DEFAULT 'SKILLS',
    ADD COLUMN target_role VARCHAR(100) NULL,
    ADD COLUMN skills VARCHAR(600) NULL,
    ADD COLUMN job_description TEXT NULL,
    ADD COLUMN course_id VARCHAR(36) NULL,
    ADD COLUMN top_fix TEXT NULL;

ALTER TABLE mock_interview_questions
    ADD COLUMN parent_question_id BIGINT NULL,
    ADD COLUMN relevance_score INT NULL,
    ADD COLUMN depth_score INT NULL,
    ADD COLUMN structure_score INT NULL,
    ADD COLUMN communication_score INT NULL;
