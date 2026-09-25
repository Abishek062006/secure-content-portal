-- A course is one recorded lecture (video) plus an optional cover image and an
-- optional WebVTT transcript. The transcript is stored now so quiz generation
-- can be added later without another upload step.
CREATE TABLE courses (
    id                  BINARY(16)   NOT NULL PRIMARY KEY,
    title               VARCHAR(200) NOT NULL,
    description         VARCHAR(2000),
    category            VARCHAR(80),
    video_key           VARCHAR(512) NOT NULL,
    video_filename      VARCHAR(255),
    video_mime          VARCHAR(128) NOT NULL,
    video_size          BIGINT       NOT NULL,
    thumbnail_key       VARCHAR(512),
    thumbnail_mime      VARCHAR(128),
    transcript_key      VARCHAR(512),
    transcript_filename VARCHAR(255),
    uploaded_by         BIGINT       NOT NULL,
    created_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    view_count          BIGINT       NOT NULL DEFAULT 0,
    last_viewed_at      DATETIME(6),
    CONSTRAINT courses_video_key_uq      UNIQUE (video_key),
    CONSTRAINT courses_thumbnail_key_uq  UNIQUE (thumbnail_key),
    CONSTRAINT courses_transcript_key_uq UNIQUE (transcript_key),
    CONSTRAINT courses_uploaded_by_fk    FOREIGN KEY (uploaded_by) REFERENCES users (id)
) ENGINE = InnoDB;

CREATE INDEX courses_category_idx ON courses (category);
CREATE INDEX courses_created_idx  ON courses (created_at);
