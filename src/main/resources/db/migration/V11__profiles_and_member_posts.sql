-- Member profiles: a headline, about text, images, and education / experience / skills entries.
CREATE TABLE profiles (
    user_id      BIGINT        NOT NULL PRIMARY KEY,
    headline     VARCHAR(220),
    about        VARCHAR(2600),
    location     VARCHAR(120),
    website      VARCHAR(300),
    avatar_key   VARCHAR(512),
    avatar_mime  VARCHAR(100),
    banner_key   VARCHAR(512),
    banner_mime  VARCHAR(100),
    updated_at   DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT profiles_user_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE profile_entries (
    id           BINARY(16)    NOT NULL PRIMARY KEY,
    user_id      BIGINT        NOT NULL,
    kind         VARCHAR(12)   NOT NULL,
    title        VARCHAR(200)  NOT NULL,
    subtitle     VARCHAR(200),
    start_year   INT,
    end_year     INT,
    description  VARCHAR(1500),
    created_at   DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT profile_entries_user_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT profile_entries_kind_check CHECK (kind IN ('EDUCATION', 'EXPERIENCE', 'SKILL'))
) ENGINE = InnoDB;

CREATE INDEX profile_entries_user_idx ON profile_entries (user_id, kind);

-- Any member can post; a post can be an article (with a title) or share one of the member's certificates.
ALTER TABLE posts
    ADD COLUMN kind           VARCHAR(10)  NOT NULL DEFAULT 'POST',
    ADD COLUMN title          VARCHAR(200) NULL,
    ADD COLUMN certificate_id BINARY(16)   NULL,
    ADD CONSTRAINT posts_certificate_fk FOREIGN KEY (certificate_id) REFERENCES certificates (id) ON DELETE SET NULL,
    ADD CONSTRAINT posts_kind_check CHECK (kind IN ('POST', 'ARTICLE'));

CREATE INDEX posts_author_idx ON posts (author_id, publish_at);
