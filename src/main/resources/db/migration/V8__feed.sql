-- The feed: admin posts (text with a caption, optional image, optionally promoting a course),
-- one reaction per learner per post, and comments. A post with publish_at in the future is scheduled.
CREATE TABLE posts (
    id          BINARY(16)   NOT NULL PRIMARY KEY,
    author_id   BIGINT       NOT NULL,
    body        TEXT         NOT NULL,
    image_key   VARCHAR(512),
    image_mime  VARCHAR(100),
    course_id   BINARY(16),
    pinned      BOOLEAN      NOT NULL DEFAULT FALSE,
    publish_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT posts_author_fk FOREIGN KEY (author_id) REFERENCES users (id) ON DELETE CASCADE,
    -- Deleting a course keeps the post as plain text rather than taking the announcement with it.
    CONSTRAINT posts_course_fk FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE SET NULL
) ENGINE = InnoDB;

CREATE INDEX posts_feed_idx ON posts (publish_at);

CREATE TABLE post_reactions (
    id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    post_id    BINARY(16)  NOT NULL,
    user_id    BIGINT      NOT NULL,
    type       VARCHAR(12) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT post_reactions_uq UNIQUE (post_id, user_id),
    CONSTRAINT post_reactions_post_fk FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE,
    CONSTRAINT post_reactions_user_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT post_reactions_type_check CHECK (type IN ('LIKE', 'CELEBRATE', 'INSIGHTFUL'))
) ENGINE = InnoDB;

CREATE TABLE post_comments (
    id         BINARY(16)   NOT NULL PRIMARY KEY,
    post_id    BINARY(16)   NOT NULL,
    user_id    BIGINT       NOT NULL,
    body       VARCHAR(1000) NOT NULL,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT post_comments_post_fk FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE,
    CONSTRAINT post_comments_user_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE INDEX post_comments_post_idx ON post_comments (post_id, created_at);
