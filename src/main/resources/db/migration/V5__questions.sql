-- The question bank. Every question belongs to a lesson (and, through it, a module) so quizzes and
-- assessments can later draw from a module's lessons. AI-written questions start as DRAFT until an
-- admin approves them; questions an admin writes or imports are approved straight away.
CREATE TABLE questions (
    id             BINARY(16)    NOT NULL PRIMARY KEY,
    course_id      BINARY(16)    NOT NULL,
    lesson_id      BINARY(16)    NOT NULL,
    question_text  VARCHAR(1000) NOT NULL,
    difficulty     VARCHAR(8)    NOT NULL,
    explanation    VARCHAR(1000),
    -- Where in the lesson the answer is discussed, so learners can jump back to it.
    source_seconds INT,
    status         VARCHAR(12)   NOT NULL DEFAULT 'DRAFT',
    source         VARCHAR(8)    NOT NULL DEFAULT 'AI',
    created_at     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT questions_course_fk FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE CASCADE,
    CONSTRAINT questions_lesson_fk FOREIGN KEY (lesson_id) REFERENCES lessons (id) ON DELETE CASCADE,
    CONSTRAINT questions_difficulty_check CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD')),
    CONSTRAINT questions_status_check     CHECK (status IN ('DRAFT', 'APPROVED')),
    CONSTRAINT questions_source_check     CHECK (source IN ('AI', 'MANUAL', 'IMPORT'))
) ENGINE = InnoDB;

CREATE INDEX questions_course_idx ON questions (course_id, status, difficulty);
CREATE INDEX questions_lesson_idx ON questions (lesson_id);

CREATE TABLE question_options (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    question_id BINARY(16)   NOT NULL,
    sort_order  INT          NOT NULL,
    option_text VARCHAR(500) NOT NULL,
    is_correct  BOOLEAN      NOT NULL,
    CONSTRAINT question_options_question_fk FOREIGN KEY (question_id) REFERENCES questions (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE INDEX question_options_question_idx ON question_options (question_id);
