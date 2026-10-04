-- V39: coding questions (fill the code, predict the output) with typed answers, and the admin's say on whether options are shown

ALTER TABLE questions ADD COLUMN question_type VARCHAR(16) NOT NULL DEFAULT 'MULTIPLE_CHOICE';
ALTER TABLE questions ADD COLUMN code_snippet VARCHAR(4000) NULL;
-- Only meaningful for the coding types: when false the learner types the answer instead of picking an option.
ALTER TABLE questions ADD COLUMN show_options BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE questions ADD CONSTRAINT questions_type_check CHECK (question_type IN ('MULTIPLE_CHOICE', 'FILL_CODE', 'PREDICT_OUTPUT'));

-- Other answers a typed response may match, besides the text of the correct option.
CREATE TABLE question_accepted_answers (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    question_id BINARY(16)   NOT NULL,
    sort_order  INT          NOT NULL,
    answer_text VARCHAR(500) NOT NULL,
    CONSTRAINT question_accepted_answers_question_fk FOREIGN KEY (question_id) REFERENCES questions (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE INDEX question_accepted_answers_question_idx ON question_accepted_answers (question_id);

ALTER TABLE attempt_questions ADD COLUMN typed_answer VARCHAR(1000) NULL;

-- How a generation job should mix question styles.
ALTER TABLE generation_jobs ADD COLUMN question_mix VARCHAR(10) NOT NULL DEFAULT 'MIXED';
ALTER TABLE generation_jobs ADD CONSTRAINT generation_jobs_mix_check CHECK (question_mix IN ('MIXED', 'CHOICE', 'CODING'));
