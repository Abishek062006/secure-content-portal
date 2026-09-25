-- A course becomes Course -> Modules -> Lessons. Each lesson owns one video and
-- an optional WebVTT transcript; the course keeps only what describes it.

CREATE TABLE modules (
    id          BINARY(16)    NOT NULL PRIMARY KEY,
    course_id   BINARY(16)    NOT NULL,
    title       VARCHAR(200)  NOT NULL,
    description VARCHAR(2000),
    position    INT           NOT NULL,
    created_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT modules_course_fk FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE INDEX modules_course_idx ON modules (course_id, position);

-- course_id is repeated here so lessons can be found/counted per course without joining modules.
CREATE TABLE lessons (
    id                  BINARY(16)   NOT NULL PRIMARY KEY,
    module_id           BINARY(16)   NOT NULL,
    course_id           BINARY(16)   NOT NULL,
    title               VARCHAR(200) NOT NULL,
    description         VARCHAR(2000),
    position            INT          NOT NULL,
    video_key           VARCHAR(512) NOT NULL,
    video_filename      VARCHAR(255),
    video_mime          VARCHAR(128) NOT NULL,
    video_size          BIGINT       NOT NULL,
    transcript_key      VARCHAR(512),
    transcript_filename VARCHAR(255),
    created_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT lessons_video_key_uq      UNIQUE (video_key),
    CONSTRAINT lessons_transcript_key_uq UNIQUE (transcript_key),
    CONSTRAINT lessons_module_fk FOREIGN KEY (module_id) REFERENCES modules (id) ON DELETE CASCADE,
    CONSTRAINT lessons_course_fk FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE INDEX lessons_module_idx ON lessons (module_id, position);
CREATE INDEX lessons_course_idx ON lessons (course_id);

-- Learners only see PUBLISHED courses; new courses start as DRAFT while the admin builds the outline.
ALTER TABLE courses ADD COLUMN status VARCHAR(12) NOT NULL DEFAULT 'DRAFT';
ALTER TABLE courses ADD CONSTRAINT courses_status_check CHECK (status IN ('DRAFT', 'PUBLISHED'));

-- Existing single-video courses become one module holding one lesson, and stay visible.
UPDATE courses SET status = 'PUBLISHED';

INSERT INTO modules (id, course_id, title, position)
SELECT UUID_TO_BIN(UUID()), id, 'Module 1', 0 FROM courses;

INSERT INTO lessons (id, module_id, course_id, title, position, video_key, video_filename, video_mime, video_size,
                     transcript_key, transcript_filename)
SELECT UUID_TO_BIN(UUID()), m.id, c.id, c.title, 0, c.video_key, c.video_filename, c.video_mime, c.video_size,
       c.transcript_key, c.transcript_filename
FROM courses c JOIN modules m ON m.course_id = c.id;

ALTER TABLE courses DROP INDEX courses_video_key_uq;
ALTER TABLE courses DROP INDEX courses_transcript_key_uq;
ALTER TABLE courses
    DROP COLUMN video_key,
    DROP COLUMN video_filename,
    DROP COLUMN video_mime,
    DROP COLUMN video_size,
    DROP COLUMN transcript_key,
    DROP COLUMN transcript_filename;

-- A learner enrolls in a course (free, self-service) and Continue takes them back to where they were.
CREATE TABLE enrollments (
    id               BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT      NOT NULL,
    course_id        BINARY(16)  NOT NULL,
    enrolled_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_lesson_id   BINARY(16),
    last_accessed_at DATETIME(6),
    CONSTRAINT enrollments_user_course_uq UNIQUE (user_id, course_id),
    CONSTRAINT enrollments_user_fk   FOREIGN KEY (user_id)   REFERENCES users (id),
    CONSTRAINT enrollments_course_fk FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE lesson_progress (
    id               BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT      NOT NULL,
    lesson_id        BINARY(16)  NOT NULL,
    course_id        BINARY(16)  NOT NULL,
    position_seconds INT         NOT NULL DEFAULT 0,
    completed        BOOLEAN     NOT NULL DEFAULT FALSE,
    completed_at     DATETIME(6),
    updated_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT lesson_progress_user_lesson_uq UNIQUE (user_id, lesson_id),
    CONSTRAINT lesson_progress_user_fk   FOREIGN KEY (user_id)   REFERENCES users (id),
    CONSTRAINT lesson_progress_lesson_fk FOREIGN KEY (lesson_id) REFERENCES lessons (id) ON DELETE CASCADE,
    CONSTRAINT lesson_progress_course_fk FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE INDEX lesson_progress_user_course_idx ON lesson_progress (user_id, course_id);
