-- Courses can now say what a learner will be able to do afterwards, and can gate access behind an
-- admin's approval instead of only "free, enroll immediately" or "paid, enroll immediately" (no payment
-- gateway exists yet either way — see CoursePricing's own note on that).
ALTER TABLE courses
    ADD COLUMN outcomes    VARCHAR(2000) NULL,
    ADD COLUMN access_type VARCHAR(12)   NOT NULL DEFAULT 'OPEN';

ALTER TABLE courses
    ADD CONSTRAINT courses_access_type_check CHECK (access_type IN ('OPEN', 'REGISTER'));

-- One row per (course, learner): a denied request can be resubmitted by resetting this same row to
-- PENDING rather than accumulating a new one, so there's always exactly one live status to review.
CREATE TABLE course_registration_requests (
    id            BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    course_id     BINARY(16)    NOT NULL,
    user_id       BIGINT        NOT NULL,
    status        VARCHAR(12)   NOT NULL DEFAULT 'PENDING',
    message       VARCHAR(1000),
    decision_note VARCHAR(1000),
    requested_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    decided_at    DATETIME(6),
    decided_by    VARCHAR(320),
    CONSTRAINT registration_requests_course_user_uq UNIQUE (course_id, user_id),
    CONSTRAINT registration_requests_course_fk FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE CASCADE,
    CONSTRAINT registration_requests_user_fk   FOREIGN KEY (user_id)   REFERENCES users (id),
    CONSTRAINT registration_requests_status_check CHECK (status IN ('PENDING', 'APPROVED', 'DENIED'))
) ENGINE = InnoDB;

CREATE INDEX registration_requests_status_idx ON course_registration_requests (status);
