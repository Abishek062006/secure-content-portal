-- Extra study material attached to a module: PDFs, HTML pages, small videos, documents and links.
-- The admin decides per item whether learners may download it or only view it inside the portal.
CREATE TABLE module_materials (
    id            BINARY(16)    NOT NULL PRIMARY KEY,
    module_id     BINARY(16)    NOT NULL,
    course_id     BINARY(16)    NOT NULL,
    kind          VARCHAR(10)   NOT NULL,
    title         VARCHAR(200)  NOT NULL,
    description   VARCHAR(1000),
    storage_key   VARCHAR(512),
    filename      VARCHAR(255),
    mime          VARCHAR(128),
    size_bytes    BIGINT,
    page_count    INT,
    url           VARCHAR(1000),
    downloadable  BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at    DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT module_materials_module_fk FOREIGN KEY (module_id) REFERENCES modules (id) ON DELETE CASCADE,
    CONSTRAINT module_materials_course_fk FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE CASCADE,
    CONSTRAINT module_materials_kind_check CHECK (kind IN ('PDF', 'HTML', 'VIDEO', 'DOCUMENT', 'LINK'))
) ENGINE = InnoDB;

CREATE INDEX module_materials_module_idx ON module_materials (module_id, created_at);
