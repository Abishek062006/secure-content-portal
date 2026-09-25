-- A course is one recorded lecture (video) plus an optional cover image and an
-- optional WebVTT transcript. The transcript is stored now so quiz generation
-- can be added later without another upload step.
CREATE TABLE courses (
    id                  UUID PRIMARY KEY,
    title               VARCHAR(200) NOT NULL,
    description         VARCHAR(2000),
    category            VARCHAR(80),
    video_key           VARCHAR(512) NOT NULL UNIQUE,
    video_filename      VARCHAR(255),
    video_mime          VARCHAR(128) NOT NULL,
    video_size          BIGINT       NOT NULL,
    thumbnail_key       VARCHAR(512) UNIQUE,
    thumbnail_mime      VARCHAR(128),
    transcript_key      VARCHAR(512) UNIQUE,
    transcript_filename VARCHAR(255),
    uploaded_by         BIGINT       NOT NULL REFERENCES users (id),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    view_count          BIGINT       NOT NULL DEFAULT 0,
    last_viewed_at      TIMESTAMPTZ
);

CREATE INDEX courses_category_idx ON courses (category);
CREATE INDEX courses_created_idx  ON courses (created_at DESC);
