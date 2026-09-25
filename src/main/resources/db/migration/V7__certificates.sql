-- A completion certificate, issued once per learner per course. The title and name are copied in
-- so the certificate stays as issued even if the course is renamed or the learner's profile changes.
CREATE TABLE certificates (
    id              BINARY(16)   NOT NULL PRIMARY KEY,
    code            VARCHAR(14)  NOT NULL,
    user_id         BIGINT       NOT NULL,
    course_id       BINARY(16)   NOT NULL,
    recipient_name  VARCHAR(255) NOT NULL,
    course_title    VARCHAR(200) NOT NULL,
    issued_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT certificates_code_uq UNIQUE (code),
    CONSTRAINT certificates_user_course_uq UNIQUE (user_id, course_id),
    CONSTRAINT certificates_user_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT certificates_course_fk FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE CASCADE
) ENGINE = InnoDB;
