-- Questions can be reserved for the final assessment ("new" questions the module quizzes never draw),
-- and a final assessment records what share of its questions is recycled from the course's other questions.
ALTER TABLE questions ADD COLUMN final_only BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE assessments ADD COLUMN reuse_percent INT NULL;
