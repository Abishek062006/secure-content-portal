-- V38: who conducts the interview (shapes how questions are worded, and which character the learner sees and hears)

ALTER TABLE mock_interview_sessions ADD COLUMN interviewer VARCHAR(12) NULL;
