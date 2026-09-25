-- A module can end with a QUIZ (practice, unlimited retakes, instant feedback) or an ASSESSMENT
-- (graded, with a pass mark, optional time limit and attempt limit); a course can also have one
-- final assessment. module_id NULL means the course-level final. No row means "skipped".
CREATE TABLE assessments (
    id                 BINARY(16)   NOT NULL PRIMARY KEY,
    course_id          BINARY(16)   NOT NULL,
    module_id          BINARY(16),
    type               VARCHAR(12)  NOT NULL,
    title              VARCHAR(200) NOT NULL,
    -- How many questions of each difficulty are drawn (randomly) from the approved bank per attempt.
    easy_count         INT          NOT NULL DEFAULT 0,
    medium_count       INT          NOT NULL DEFAULT 0,
    hard_count         INT          NOT NULL DEFAULT 0,
    pass_percent       INT,
    time_limit_minutes INT,
    max_attempts       INT,
    -- Passing this assessment is required before the next module opens.
    gates_next         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT assessments_module_uq UNIQUE (module_id),
    CONSTRAINT assessments_course_fk FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE CASCADE,
    CONSTRAINT assessments_module_fk FOREIGN KEY (module_id) REFERENCES modules (id) ON DELETE CASCADE,
    CONSTRAINT assessments_type_check CHECK (type IN ('QUIZ', 'ASSESSMENT'))
) ENGINE = InnoDB;

CREATE INDEX assessments_course_idx ON assessments (course_id);

CREATE TABLE attempts (
    id            BINARY(16)  NOT NULL PRIMARY KEY,
    assessment_id BINARY(16)  NOT NULL,
    user_id       BIGINT      NOT NULL,
    status        VARCHAR(12) NOT NULL,
    started_at    DATETIME(6) NOT NULL,
    expires_at    DATETIME(6),
    submitted_at  DATETIME(6),
    score_percent INT,
    correct_count INT,
    total_count   INT         NOT NULL,
    passed        BOOLEAN,
    timed_out     BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT attempts_assessment_fk FOREIGN KEY (assessment_id) REFERENCES assessments (id) ON DELETE CASCADE,
    CONSTRAINT attempts_user_fk       FOREIGN KEY (user_id)       REFERENCES users (id),
    CONSTRAINT attempts_status_check  CHECK (status IN ('IN_PROGRESS', 'SUBMITTED'))
) ENGINE = InnoDB;

CREATE INDEX attempts_user_assessment_idx ON attempts (user_id, assessment_id);

-- The questions one attempt was dealt. option_order lists the original option positions in the order this
-- learner sees them (e.g. "2,0,3,1"), so answers stay hidden and stable across page reloads.
CREATE TABLE attempt_questions (
    id             BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    attempt_id     BINARY(16)  NOT NULL,
    position       INT         NOT NULL,
    question_id    BINARY(16)  NOT NULL,
    option_order   VARCHAR(16) NOT NULL,
    selected_index INT,
    correct        BOOLEAN,
    CONSTRAINT attempt_questions_attempt_fk  FOREIGN KEY (attempt_id)  REFERENCES attempts (id)  ON DELETE CASCADE,
    CONSTRAINT attempt_questions_question_fk FOREIGN KEY (question_id) REFERENCES questions (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE INDEX attempt_questions_attempt_idx ON attempt_questions (attempt_id, position);
