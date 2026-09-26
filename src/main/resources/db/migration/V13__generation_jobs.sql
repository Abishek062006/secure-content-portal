-- Question generation runs in the background; this row is what the admin's page follows for progress.
CREATE TABLE generation_jobs (
    id               BINARY(16)   NOT NULL PRIMARY KEY,
    course_id        BINARY(16)   NOT NULL,
    lesson_id        BINARY(16)   NOT NULL,
    requested_by     BIGINT       NOT NULL,
    requested_count  INT          NOT NULL,
    difficulty       VARCHAR(8),
    final_only       BOOLEAN      NOT NULL DEFAULT FALSE,
    status           VARCHAR(10)  NOT NULL,
    produced         INT          NOT NULL DEFAULT 0,
    message          VARCHAR(500),
    created_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    started_at       DATETIME(6),
    finished_at      DATETIME(6),
    CONSTRAINT generation_jobs_course_fk FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE CASCADE,
    CONSTRAINT generation_jobs_lesson_fk FOREIGN KEY (lesson_id) REFERENCES lessons (id) ON DELETE CASCADE,
    CONSTRAINT generation_jobs_status_check CHECK (status IN ('QUEUED', 'RUNNING', 'DONE', 'FAILED'))
) ENGINE = InnoDB;

CREATE INDEX generation_jobs_course_idx ON generation_jobs (course_id, created_at);
