-- V40: how each interview answer was given (typed or spoken), and for spoken ones the timing the learner's browser measured.
-- Feedback only: none of it counts towards the score or the leaderboard. All optional, so earlier interviews simply have none.

ALTER TABLE mock_interview_questions ADD COLUMN answer_mode VARCHAR(5) NULL;
ALTER TABLE mock_interview_questions ADD COLUMN thinking_seconds INT NULL;
ALTER TABLE mock_interview_questions ADD COLUMN speaking_seconds INT NULL;
ALTER TABLE mock_interview_questions ADD COLUMN words_per_minute INT NULL;
ALTER TABLE mock_interview_questions ADD COLUMN long_pauses INT NULL;
ALTER TABLE mock_interview_questions ADD COLUMN longest_pause_ms INT NULL;
-- False when the room was too noisy (or the voice too faint) to measure speech timing reliably; null for typed answers.
ALTER TABLE mock_interview_questions ADD COLUMN audio_clear BOOLEAN NULL;
ALTER TABLE mock_interview_questions ADD CONSTRAINT mock_interview_questions_mode_check
    CHECK (answer_mode IS NULL OR answer_mode IN ('VOICE', 'TYPED'));
