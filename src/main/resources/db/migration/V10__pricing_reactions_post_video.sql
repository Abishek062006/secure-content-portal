-- Course pricing: whole rupees, with an optional percentage discount that runs for a period.
ALTER TABLE courses
    ADD COLUMN price_rupees     INT         NOT NULL DEFAULT 0,
    ADD COLUMN discount_percent INT         NOT NULL DEFAULT 0,
    ADD COLUMN discount_start   DATETIME(6) NULL,
    ADD COLUMN discount_end     DATETIME(6) NULL;

-- More reactions, like the ones people know from other feeds.
ALTER TABLE post_reactions DROP CHECK post_reactions_type_check;
ALTER TABLE post_reactions ADD CONSTRAINT post_reactions_type_check
    CHECK (type IN ('LIKE', 'CELEBRATE', 'SUPPORT', 'LOVE', 'INSIGHTFUL', 'FUNNY'));

-- A post can carry a video instead of an image.
ALTER TABLE posts
    ADD COLUMN video_key  VARCHAR(512) NULL,
    ADD COLUMN video_mime VARCHAR(100) NULL;
