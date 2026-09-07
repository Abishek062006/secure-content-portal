-- ---------------------------------------------------------------------------
-- Application schema
-- ---------------------------------------------------------------------------

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(320) NOT NULL UNIQUE,
    display_name  VARCHAR(255),
    picture_url   VARCHAR(1024),
    role          VARCHAR(16)  NOT NULL DEFAULT 'VIEWER',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_login_at TIMESTAMPTZ,
    CONSTRAINT users_role_check CHECK (role IN ('ADMIN', 'VIEWER'))
);

CREATE TABLE content_items (
    id                UUID PRIMARY KEY,
    title             VARCHAR(200) NOT NULL,
    description       VARCHAR(2000),
    category          VARCHAR(80),
    content_type      VARCHAR(16)  NOT NULL,
    -- Opaque object key in the private bucket. Never exposed to the browser.
    storage_key       VARCHAR(512) NOT NULL UNIQUE,
    original_filename VARCHAR(255),
    mime_type         VARCHAR(128) NOT NULL,
    size_bytes        BIGINT       NOT NULL,
    page_count        INT,
    uploaded_by       BIGINT       NOT NULL REFERENCES users (id),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    view_count        BIGINT       NOT NULL DEFAULT 0,
    last_viewed_at    TIMESTAMPTZ,
    CONSTRAINT content_type_check CHECK (content_type IN ('VIDEO', 'PDF', 'HTML'))
);

CREATE INDEX content_items_type_idx     ON content_items (content_type);
CREATE INDEX content_items_category_idx ON content_items (category);
CREATE INDEX content_items_created_idx  ON content_items (created_at DESC);

-- Bonus: per-item view tracking, surfaced to admins only.
CREATE TABLE content_views (
    id         BIGSERIAL PRIMARY KEY,
    content_id UUID        NOT NULL REFERENCES content_items (id) ON DELETE CASCADE,
    user_id    BIGINT      NOT NULL REFERENCES users (id),
    viewed_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX content_views_content_idx ON content_views (content_id, viewed_at DESC);

-- Bonus: audit trail of admin actions.
CREATE TABLE audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    actor_email VARCHAR(320) NOT NULL,
    action      VARCHAR(32)  NOT NULL,
    content_id  UUID,
    detail      VARCHAR(1000),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX audit_logs_created_idx ON audit_logs (created_at DESC);

-- ---------------------------------------------------------------------------
-- Spring Session JDBC (verbatim from spring-session-jdbc schema-postgresql.sql).
-- Managed here rather than by session auto-initialisation so that Flyway owns
-- the whole schema. Sessions live in Postgres so they survive the free-tier
-- dyno sleeping and restarting.
-- ---------------------------------------------------------------------------

CREATE TABLE SPRING_SESSION (
    PRIMARY_ID            CHAR(36) NOT NULL,
    SESSION_ID            CHAR(36) NOT NULL,
    CREATION_TIME         BIGINT   NOT NULL,
    LAST_ACCESS_TIME      BIGINT   NOT NULL,
    MAX_INACTIVE_INTERVAL INT      NOT NULL,
    EXPIRY_TIME           BIGINT   NOT NULL,
    PRINCIPAL_NAME        VARCHAR(100),
    CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
);

CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
    SESSION_PRIMARY_ID CHAR(36)     NOT NULL,
    ATTRIBUTE_NAME     VARCHAR(200) NOT NULL,
    ATTRIBUTE_BYTES    BYTEA        NOT NULL,
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID)
        REFERENCES SPRING_SESSION (PRIMARY_ID) ON DELETE CASCADE
);
