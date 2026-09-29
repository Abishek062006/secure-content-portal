-- V35: per-user notification feed (course/quiz/community/announcement/content/security/system/achievement)

CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    recipient_user_id BIGINT NOT NULL,
    category VARCHAR(32) NOT NULL,
    title VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    priority VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    action_url VARCHAR(512),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    read_at DATETIME(6),
    CONSTRAINT notifications_recipient_fk FOREIGN KEY (recipient_user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE INDEX idx_notifications_recipient_read_created ON notifications (recipient_user_id, is_read, created_at DESC);
CREATE INDEX idx_notifications_recipient_category ON notifications (recipient_user_id, category);
