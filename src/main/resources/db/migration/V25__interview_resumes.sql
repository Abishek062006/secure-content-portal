-- V25: a learner's resume for interview practice. Only the text read from it is kept (never the file), one per learner,
-- and it is deleted by the learner, by the retention job, or with the learner's account.
CREATE TABLE interview_resumes (
    id                BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id           BIGINT       NOT NULL,
    original_filename VARCHAR(200) NOT NULL,
    content_text      MEDIUMTEXT   NOT NULL,
    characters        INT          NOT NULL,
    uploaded_at       DATETIME(6)  NOT NULL,
    expires_at        DATETIME(6)  NOT NULL,
    CONSTRAINT uk_interview_resume_user UNIQUE (user_id),
    CONSTRAINT interview_resume_user_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
CREATE INDEX idx_interview_resume_expires ON interview_resumes (expires_at);
