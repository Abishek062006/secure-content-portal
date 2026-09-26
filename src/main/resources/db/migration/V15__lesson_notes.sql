-- A learner's private notes on a lesson, each pinned to a moment in the video.
CREATE TABLE lesson_notes (
    id            BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT        NOT NULL,
    lesson_id     BINARY(16)    NOT NULL,
    course_id     BINARY(16)    NOT NULL,
    video_seconds INT           NOT NULL DEFAULT 0,
    body          VARCHAR(2000) NOT NULL,
    created_at    DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT lesson_notes_user_fk   FOREIGN KEY (user_id)   REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT lesson_notes_lesson_fk FOREIGN KEY (lesson_id) REFERENCES lessons (id) ON DELETE CASCADE,
    CONSTRAINT lesson_notes_course_fk FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE INDEX lesson_notes_user_lesson_idx ON lesson_notes (user_id, lesson_id, video_seconds);
