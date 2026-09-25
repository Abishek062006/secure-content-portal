-- ---------------------------------------------------------------------------
-- Application schema (MySQL 8+/9, InnoDB, utf8mb4)
-- ---------------------------------------------------------------------------

CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    email         VARCHAR(320) NOT NULL,
    display_name  VARCHAR(255),
    picture_url   VARCHAR(1024),
    role          VARCHAR(16)  NOT NULL DEFAULT 'VIEWER',
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_login_at DATETIME(6),
    -- utf8mb4_0900_ai_ci is case-insensitive, so this also makes emails unique regardless of case.
    CONSTRAINT users_email_uq UNIQUE (email),
    CONSTRAINT users_role_check CHECK (role IN ('ADMIN', 'VIEWER'))
) ENGINE = InnoDB;

CREATE TABLE content_items (
    id                BINARY(16)   NOT NULL PRIMARY KEY,
    title             VARCHAR(200) NOT NULL,
    description       VARCHAR(2000),
    category          VARCHAR(80),
    content_type      VARCHAR(16)  NOT NULL,
    -- Opaque object key in the private bucket. Never exposed to the browser.
    storage_key       VARCHAR(512) NOT NULL,
    original_filename VARCHAR(255),
    mime_type         VARCHAR(128) NOT NULL,
    size_bytes        BIGINT       NOT NULL,
    page_count        INT,
    uploaded_by       BIGINT       NOT NULL,
    created_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    view_count        BIGINT       NOT NULL DEFAULT 0,
    last_viewed_at    DATETIME(6),
    CONSTRAINT content_items_storage_key_uq UNIQUE (storage_key),
    CONSTRAINT content_items_uploaded_by_fk FOREIGN KEY (uploaded_by) REFERENCES users (id),
    CONSTRAINT content_type_check CHECK (content_type IN ('VIDEO', 'PDF', 'HTML'))
) ENGINE = InnoDB;

CREATE INDEX content_items_type_idx     ON content_items (content_type);
CREATE INDEX content_items_category_idx ON content_items (category);
CREATE INDEX content_items_created_idx  ON content_items (created_at);

-- Bonus: per-item view tracking, surfaced to admins only.
CREATE TABLE content_views (
    id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    content_id BINARY(16)  NOT NULL,
    user_id    BIGINT      NOT NULL,
    viewed_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT content_views_content_fk FOREIGN KEY (content_id) REFERENCES content_items (id) ON DELETE CASCADE,
    CONSTRAINT content_views_user_fk    FOREIGN KEY (user_id)    REFERENCES users (id)
) ENGINE = InnoDB;

CREATE INDEX content_views_content_idx ON content_views (content_id, viewed_at);

-- Bonus: audit trail of admin actions.
CREATE TABLE audit_logs (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    actor_email VARCHAR(320) NOT NULL,
    action      VARCHAR(32)  NOT NULL,
    content_id  BINARY(16),
    detail      VARCHAR(1000),
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE = InnoDB;

CREATE INDEX audit_logs_created_idx ON audit_logs (created_at);

-- ---------------------------------------------------------------------------
-- Spring Session JDBC (from spring-session-jdbc's schema-mysql.sql, with the
-- attribute blob widened to MEDIUMBLOB for headroom). Managed here rather than
-- by session auto-initialisation so that Flyway owns the whole schema.
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
) ENGINE = InnoDB ROW_FORMAT = DYNAMIC;

CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
    SESSION_PRIMARY_ID CHAR(36)     NOT NULL,
    ATTRIBUTE_NAME     VARCHAR(200) NOT NULL,
    ATTRIBUTE_BYTES    MEDIUMBLOB   NOT NULL,
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID)
        REFERENCES SPRING_SESSION (PRIMARY_ID) ON DELETE CASCADE
) ENGINE = InnoDB ROW_FORMAT = DYNAMIC;
