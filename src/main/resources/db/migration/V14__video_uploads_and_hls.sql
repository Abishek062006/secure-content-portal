-- Videos are uploaded from the browser straight to the bucket in parts; this row tracks one such upload
-- until it is completed into a lesson (or abandoned).
CREATE TABLE video_uploads (
    id           BINARY(16)    NOT NULL PRIMARY KEY,
    module_id    BINARY(16)    NOT NULL,
    course_id    BINARY(16)    NOT NULL,
    title        VARCHAR(200)  NOT NULL,
    description  VARCHAR(2000),
    filename     VARCHAR(255)  NOT NULL,
    size_bytes   BIGINT        NOT NULL,
    part_size    BIGINT        NOT NULL,
    storage_key  VARCHAR(512)  NOT NULL,
    s3_upload_id VARCHAR(512)  NOT NULL,
    status       VARCHAR(10)   NOT NULL,
    created_by   BIGINT        NOT NULL,
    created_at   DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at DATETIME(6),
    CONSTRAINT video_uploads_module_fk FOREIGN KEY (module_id) REFERENCES modules (id) ON DELETE CASCADE,
    CONSTRAINT video_uploads_status_check CHECK (status IN ('UPLOADING', 'COMPLETE', 'ABORTED'))
) ENGINE = InnoDB;

-- Adaptive streaming (HLS) is made from the uploaded video in the background; until it is READY the original plays.
ALTER TABLE lessons
    ADD COLUMN hls_status  VARCHAR(12)  NOT NULL DEFAULT 'NONE',
    ADD COLUMN hls_message VARCHAR(500) NULL;
