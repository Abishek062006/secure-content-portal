-- "Enquire" — on every course regardless of free/paid/register — collects contact details for the admin
-- to follow up with directly (a phone call, an email); this table is only ever read by an admin, nothing
-- here sends anything automatically.
CREATE TABLE course_enquiries (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    course_id    BINARY(16)   NOT NULL,
    user_id      BIGINT       NOT NULL,
    name         VARCHAR(200) NOT NULL,
    email        VARCHAR(320) NOT NULL,
    phone        VARCHAR(32),
    message      VARCHAR(1000),
    status       VARCHAR(12)  NOT NULL DEFAULT 'NEW',
    created_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    contacted_at DATETIME(6),
    contacted_by VARCHAR(320),
    CONSTRAINT course_enquiries_course_fk FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE CASCADE,
    CONSTRAINT course_enquiries_user_fk   FOREIGN KEY (user_id)   REFERENCES users (id),
    CONSTRAINT course_enquiries_status_check CHECK (status IN ('NEW', 'CONTACTED'))
) ENGINE = InnoDB;

CREATE INDEX course_enquiries_status_idx ON course_enquiries (status);
CREATE INDEX course_enquiries_course_idx ON course_enquiries (course_id);
