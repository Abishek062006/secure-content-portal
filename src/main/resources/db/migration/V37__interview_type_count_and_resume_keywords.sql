-- V37: technical vs HR interviews, a learner-chosen question count, and the skills read off a resume

ALTER TABLE mock_interview_sessions
    ADD COLUMN interview_type VARCHAR(12) NOT NULL DEFAULT 'TECHNICAL',
    ADD COLUMN planned_questions INT NOT NULL DEFAULT 5;
ALTER TABLE mock_interview_sessions
    ADD CONSTRAINT mock_interview_sessions_type_check CHECK (interview_type IN ('TECHNICAL', 'HR'));

ALTER TABLE interview_resumes ADD COLUMN keywords VARCHAR(700) NULL;
