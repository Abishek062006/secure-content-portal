-- V36: LinkedIn-style Connections and Real-Time Messaging schema

CREATE TABLE IF NOT EXISTS connections (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    requester_id BIGINT NOT NULL,
    receiver_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    responded_at DATETIME(6),
    CONSTRAINT uk_connection_requester_receiver UNIQUE (requester_id, receiver_id),
    CONSTRAINT fk_connections_requester FOREIGN KEY (requester_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_connections_receiver FOREIGN KEY (receiver_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE INDEX idx_connections_receiver_status ON connections (receiver_id, status);
CREATE INDEX idx_connections_requester_status ON connections (requester_id, status);

CREATE TABLE IF NOT EXISTS conversations (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user1_id BIGINT NOT NULL,
    user2_id BIGINT NOT NULL,
    last_message_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_conversations_user_pair UNIQUE (user1_id, user2_id),
    CONSTRAINT fk_conversations_user1 FOREIGN KEY (user1_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_conversations_user2 FOREIGN KEY (user2_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE INDEX idx_conversations_user1 ON conversations (user1_id, last_message_at DESC);
CREATE INDEX idx_conversations_user2 ON conversations (user2_id, last_message_at DESC);

CREATE TABLE IF NOT EXISTS messages (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    content VARCHAR(2000) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    read_at DATETIME(6),
    CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id) ON DELETE CASCADE,
    CONSTRAINT fk_messages_sender FOREIGN KEY (sender_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE INDEX idx_messages_conversation_created ON messages (conversation_id, created_at);
