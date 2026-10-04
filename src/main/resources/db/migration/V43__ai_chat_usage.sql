-- V43: how many questions each member has put to the AI assistant each day, so the daily limit holds across restarts and servers.
CREATE TABLE ai_chat_usage (
    user_id  BIGINT NOT NULL,
    day      DATE   NOT NULL,
    messages INT    NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, day),
    CONSTRAINT ai_chat_usage_user_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;
